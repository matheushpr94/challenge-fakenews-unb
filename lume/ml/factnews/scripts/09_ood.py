"""Ideia 1: detector de texto fora do que o modelo conhece. Mede se ele separa NOTÍCIA de outros gêneros, sem vazamento.

Validação (só desenvolvimento): para cada fold, treina o BERTimbau com a mesma receita nos outros folds, ajusta a distância de
Mahalanobis nas frases de treino e pontua (a) artigos do fold de fora, (b) textos externos (enciclopédia, revista/análise) e
(c) notícias de agência pública como CONTROLE (não devem ser marcadas). Limiar = percentil 95 dos artigos de fora.
`--deploy --use-test` ajusta a distância nas 100 histórias com o modelo exportado e grava models/factnews-v2/ood_stats.npz
(usa o teste como treino das estatísticas, como o modelo exportado; fica registrado em reports/test_usage.log).
Saída: reports/ood_dev.json e reports/ood_dev.md
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from pathlib import Path

import numpy as np
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer, get_linear_schedule_with_warmup

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from factnews.data import CLASS_NAMES, load_sentences  # noqa: E402
from factnews.ood import OodStats, auc, fit_stats  # noqa: E402
from factnews.splits import dev_data, load_or_create, require_test_permission  # noqa: E402

_spec = importlib.util.spec_from_file_location("ft", Path(__file__).with_name("03_finetune_bert.py"))
ft = importlib.util.module_from_spec(_spec); _spec.loader.exec_module(ft)

REPORTS = ROOT / "reports"
DOC = 20            # frases por documento externo (artigos do FactNews têm ~17 em média)
MAX_DEV_DOC = 40


def chunks(sents: list[str], n: int = DOC) -> list[list[str]]:
    docs = [sents[i:i + n] for i in range(0, len(sents), n)]
    return [d for d in docs if len(d) >= max(8, n // 2)]


def load_external() -> dict[str, list[list[str]]]:
    """genero -> lista de documentos (cada um uma lista de frases)."""
    manifest = json.loads((REPORTS / "externos_manifesto.json").read_text(encoding="utf-8"))
    keep_carta = ("/politica/", "/artigo/", "/mundo/", "/opiniao/", "/economia/")
    out: dict[str, list[list[str]]] = {"enciclopedia": [], "revista_analise": [], "noticia_controle": []}
    for m in manifest:
        u, g = m["url"], m["genre"]
        if g == "revista_analise" and "cartacapital" in u and not any(k in u for k in keep_carta):
            continue                                   # receitas, bem-estar, tecnologia: não são análise
        f = ROOT / "data" / "external" / g / f"{m['sha256'][:16]}.txt"
        if f.exists():
            out[g] += chunks(f.read_text(encoding="utf-8").split("\n"))
    return out


def train_model(tr, dev, seed=42):
    ft.set_seed(seed)
    tok = AutoTokenizer.from_pretrained(ft.MODEL)
    model = AutoModelForSequenceClassification.from_pretrained(ft.MODEL, num_labels=3).to(dev)
    enc, y = tok(tr["text"].tolist(), truncation=True, max_length=128), tr["label"].to_numpy()
    counts = np.bincount(y, minlength=3).astype(float)
    w = torch.tensor((counts.sum() / (3 * np.maximum(counts, 1))) ** 0.5, dtype=torch.float, device=dev)
    loss_fn = torch.nn.CrossEntropyLoss(weight=w)
    opt = torch.optim.AdamW(model.parameters(), lr=2e-5, weight_decay=0.01)
    steps = 4 * int(np.ceil(len(y) / 32)); sched = get_linear_schedule_with_warmup(opt, int(0.1 * steps), steps)
    for _ in range(4):
        model.train()
        for b in ft.batches(enc, y, range(len(y)), 32, tok, shuffle=True):
            b = {k: v.to(dev) for k, v in b.items()}; labels = b.pop("labels")
            with torch.autocast(device_type=dev.type, dtype=torch.bfloat16, enabled=dev.type == "cuda"):
                logits = model(**b).logits
            loss_fn(logits.float(), labels).backward(); torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step(); sched.step(); opt.zero_grad()
    return tok, model.eval()


@torch.no_grad()
def embed(tok, model, texts: list[str], dev, bs=64) -> np.ndarray:
    out = []
    for i in range(0, len(texts), bs):
        enc = tok(texts[i:i + bs], truncation=True, max_length=128, padding=True, return_tensors="pt").to(dev)
        h = model(**enc, output_hidden_states=True).hidden_states[-1].float()
        m = enc["attention_mask"].unsqueeze(-1).float()
        out.append(((h * m).sum(1) / m.sum(1).clamp(min=1)).cpu().numpy())
    return np.concatenate(out)


def dev_docs(df):
    """artigo -> índices; artigos grandes são partidos em blocos de MAX_DEV_DOC frases."""
    docs = []
    for _, g in df.groupby("article"):
        idx = g.index.to_numpy()
        for i in range(0, len(idx), MAX_DEV_DOC):
            part = idx[i:i + MAX_DEV_DOC]
            if len(part) >= 8:
                docs.append(part)
    return docs


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--deploy", action="store_true"); ap.add_argument("--use-test", action="store_true")
    args = ap.parse_args()
    dev = ft.device()
    df = load_sentences(); part = load_or_create(df); devdf = dev_data(df, part)
    ext = load_external()
    ext_flat = {g: [s for d in docs for s in d] for g, docs in ext.items()}
    print({g: f"{len(d)} docs, {len(ext_flat[g])} frases" for g, d in ext.items()}, flush=True)

    news_scores, ext_scores = [], {g: [] for g in ext}
    fold_medians = {}
    for k in sorted(devdf["fold"].unique()):
        tr, va = devdf[devdf["fold"] != k], devdf[devdf["fold"] == k].reset_index(drop=True)
        print(f"fold {k}: treino {len(tr)} / fora {len(va)}", flush=True)
        tok, model = train_model(tr, dev)
        stats = fit_stats(embed(tok, model, tr["text"].tolist(), dev))
        e_va = embed(tok, model, va["text"].tolist(), dev)
        sc = [stats.text_score(e_va[idx]) for idx in dev_docs(va)]
        news_scores += sc; fold_medians[int(k)] = float(np.median(sc))
        for g, docs in ext.items():
            e = embed(tok, model, ext_flat[g], dev); pos = 0; row = []
            for d in docs:
                row.append(stats.text_score(e[pos:pos + len(d)])); pos += len(d)
            ext_scores[g].append(row)
        del model; torch.cuda.empty_cache()

    news = np.array(news_scores); thr = float(np.percentile(news, 95))
    res = {"docs_noticia_fora": len(news), "mediana_noticia": float(np.median(news)), "limiar_p95": thr, "medianas_por_fold": fold_medians, "generos": {}}
    for g in ext:
        s = np.mean(ext_scores[g], axis=0)
        res["generos"][g] = {"docs": len(s), "mediana": float(np.median(s)), "marcados_%": float((s > thr).mean() * 100), "auc_vs_noticia": auc(s, news)}
    out = ["# Detector de texto fora do que o modelo conhece (desenvolvimento, 4 folds por história)\n",
           f"Artigos de notícia de fora do fold: {len(news)}; escore mediano {res['mediana_noticia']:.2f}; limiar (percentil 95): **{thr:.2f}**. "
           "Escore ≈ 1 = típico do treino; maior = mais estranho.\n",
           "| Gênero externo | docs (20 frases) | escore mediano | marcados como estranhos | AUC contra notícia do FactNews |", "|---|---:|---:|---:|---:|"]
    for g, r in res["generos"].items():
        out.append(f"| {g} | {r['docs']} | {r['mediana']:.2f} | {r['marcados_%']:.0f}% | {r['auc_vs_noticia']:.2f} |")
    out.append("\n`noticia_controle` (agência pública) é NOTÍCIA: o esperado é ficar perto dos 5% do limiar e AUC perto de 0,5. "
               "`enciclopedia` e `revista_analise` devem ser marcados.")
    (REPORTS / "ood_dev.json").write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")
    (REPORTS / "ood_dev.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8"); print("\n".join(out), flush=True)

    if args.deploy:
        require_test_permission(args.use_test, "ood: teste usado como TREINO das estatisticas de distancia do modelo exportado, nao como medida")
        d = ROOT / "models" / "factnews-v2" / "seed42"
        tok = AutoTokenizer.from_pretrained(d); model = AutoModelForSequenceClassification.from_pretrained(d).float().to(dev).eval()
        stats = fit_stats(embed(tok, model, df["text"].tolist(), dev)); stats.threshold = thr
        stats.save(ROOT / "models" / "factnews-v2" / "ood_stats.npz")
        print("estatisticas salvas em models/factnews-v2/ood_stats.npz; limiar", round(thr, 3), flush=True)
        for g, docs in ext.items():
            e = embed(tok, model, ext_flat[g], dev); pos = 0; row = []
            for dd in docs:
                row.append(stats.text_score(e[pos:pos + len(dd)])); pos += len(dd)
            print(f"  [modelo exportado] {g}: mediana {np.median(row):.2f}, marcados {np.mean(np.array(row) > thr) * 100:.0f}%", flush=True)


if __name__ == "__main__":
    main()
