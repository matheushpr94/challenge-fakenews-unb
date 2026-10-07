"""Ideia 1, variantes SEM ajuste fino (BERTimbau base, sem treino): três variantes declaradas ANTES de olhar os resultados.

  A) última camada + Mahalanobis (PCA 64)   B) camada 8 + Mahalanobis (PCA 64)   C) última camada + distância aos 10 vizinhos mais próximos
Mesmo protocolo de 09_ood.py: ajusta nas frases de treino de cada fold do desenvolvimento, pontua artigos de fora, textos externos e
o controle (notícia de agência pública). Critério (igual ao anterior): AUC >= 0,85 contra a notícia do FactNews em revista_analise.
Saída: reports/ood_base_dev.md
"""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

import numpy as np
import torch
from sklearn.neighbors import NearestNeighbors
from transformers import AutoModel, AutoTokenizer

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from factnews.data import load_sentences  # noqa: E402
from factnews.ood import auc, fit_stats  # noqa: E402
from factnews.splits import dev_data, load_or_create  # noqa: E402

_spec = importlib.util.spec_from_file_location("ood09", Path(__file__).with_name("09_ood.py"))
o9 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(o9)
MODEL = "neuralmind/bert-base-portuguese-cased"
REPORTS = ROOT / "reports"


@torch.no_grad()
def embed_layers(tok, model, texts, dev, layers=(-1, 8), bs=64):
    out = {l: [] for l in layers}
    for i in range(0, len(texts), bs):
        enc = tok(texts[i:i + bs], truncation=True, max_length=128, padding=True, return_tensors="pt").to(dev)
        hs = model(**enc, output_hidden_states=True).hidden_states
        m = enc["attention_mask"].unsqueeze(-1).float()
        for l in layers:
            out[l].append(((hs[l].float() * m).sum(1) / m.sum(1).clamp(min=1)).cpu().numpy())
    return {l: np.concatenate(v) for l, v in out.items()}


def main() -> None:
    dev = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    df = load_sentences(); part = load_or_create(df); devdf = dev_data(df, part)
    ext = o9.load_external()
    ext_sents = [s for g in ext for d in ext[g] for s in d]
    tok = AutoTokenizer.from_pretrained(MODEL); model = AutoModel.from_pretrained(MODEL).to(dev).eval()
    E_dev = embed_layers(tok, model, devdf["text"].tolist(), dev)
    E_ext = embed_layers(tok, model, ext_sents, dev)
    docs = o9.dev_docs(devdf)
    doc_fold = np.array([devdf.loc[idx[0], "fold"] for idx in docs])
    pos = {}; p = 0
    for g in ext:
        pos[g] = []
        for d in ext[g]:
            pos[g].append((p, p + len(d))); p += len(d)
    variants = {"A: última camada, Mahalanobis": (-1, "maha"), "B: camada 8, Mahalanobis": (8, "maha"), "C: última camada, 10 vizinhos": (-1, "knn")}
    out = ["# Detector sem ajuste fino (BERTimbau base), 4 folds por história\n",
           "| Variante | gênero | marcados como estranhos (limiar = p95 das notícias de fora) | AUC contra notícia do FactNews |", "|---|---|---:|---:|"]
    for name, (layer, kind) in variants.items():
        news, ext_s = [], {g: [[] for _ in ext[g]] for g in ext}
        for k in np.unique(devdf["fold"]):
            tr = (devdf["fold"] != k).to_numpy(); Etr = E_dev[layer][tr]
            if kind == "maha":
                st = fit_stats(Etr); score = lambda e: st.text_score(e)  # noqa: E731
            else:
                En = Etr / np.linalg.norm(Etr, axis=1, keepdims=True); nn = NearestNeighbors(n_neighbors=10).fit(En)
                score = lambda e: float(nn.kneighbors(e / np.linalg.norm(e, axis=1, keepdims=True))[0].mean())  # noqa: E731
            for di, idx in enumerate(docs):
                if doc_fold[di] == k:
                    news.append(score(E_dev[layer][idx]))
            for g in ext:
                for ji, (a, b) in enumerate(pos[g]):
                    ext_s[g][ji].append(score(E_ext[layer][a:b]))
        news = np.array(news); thr = np.percentile(news, 95)
        for g in ext:
            s = np.array([np.mean(v) for v in ext_s[g]])
            out.append(f"| {name} | {g} | {(s > thr).mean() * 100:.0f}% | {auc(s, news):.2f} |")
    out.append("\nControle: `noticia_controle` é notícia (esperado ~5% e AUC ~0,5). Critério: AUC >= 0,85 em `revista_analise`.")
    (REPORTS / "ood_base_dev.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(out))


if __name__ == "__main__":
    main()
