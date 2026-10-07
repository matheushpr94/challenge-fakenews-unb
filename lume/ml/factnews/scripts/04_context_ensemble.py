"""Contexto, conjunto de sementes e limiar no FactNews (complementa 03_finetune_bert.py, que não é alterado).

ACHADO SOBRE OS DADOS: no CSV as frases de cada artigo estão em ORDEM ALFABÉTICA; a ordem de leitura foi perdida.
Por isso não existe "frase anterior / posterior". O contexto testado aqui é a MANCHETE do artigo (linhas `_titulo`),
que não depende da ordem e existe na hora do uso. As próprias linhas de manchete ficam com contexto vazio.

Modos:
  --cv                 treina nos folds 1..4 do desenvolvimento (por história) e grava as probabilidades fora-da-dobra
  --final --use-test   treina em todo o desenvolvimento e grava as probabilidades no teste (lê o teste: fica registrado)
  --export --use-test  treina nas 100 histórias e salva cada modelo em models/<tag>/seed<N> (fp16)
Cada modo roda uma lista de sementes (--seeds); o conjunto é a média das probabilidades (análise em 05_analyze.py).
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import time
from datetime import date
from pathlib import Path

import numpy as np
import torch
from sklearn.metrics import f1_score
from transformers import AutoModelForSequenceClassification, AutoTokenizer, get_linear_schedule_with_warmup

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from factnews.data import CLASS_NAMES, load_sentences  # noqa: E402
from factnews.splits import dev_data, load_or_create, require_test_permission, test_data  # noqa: E402

_spec = importlib.util.spec_from_file_location("ft", Path(__file__).with_name("03_finetune_bert.py"))
ft = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ft)  # só reaproveita set_seed, device, batches, MODEL, REPORTS, MODELS (main não roda)


def headline_context(df):
    """Manchete do artigo (todas as linhas `_titulo`) para cada frase; vazio nas próprias linhas de manchete."""
    heads = df[df["is_title"]].groupby("article")["text"].apply(" ".join)
    return df["article"].map(heads).fillna("").mask(df["is_title"], "")


def encode(tok, d, args):
    if args.ctx == "none":
        return tok(d["text"].tolist(), truncation=True, max_length=args.max_len)
    return tok(d["text"].tolist(), d["ctx"].tolist(), truncation="longest_first", max_length=args.max_len)


def train_proba(tr, ev, args, dev, seed, save_dir=None):
    """Treina um BERTimbau em `tr` e devolve as probabilidades (n_ev x 3) em `ev` (None = só treina)."""
    ft.set_seed(seed)
    tok = AutoTokenizer.from_pretrained(ft.MODEL)
    model = AutoModelForSequenceClassification.from_pretrained(ft.MODEL, num_labels=len(CLASS_NAMES)).to(dev)
    enc_tr, y = encode(tok, tr, args), tr["label"].to_numpy()
    counts = np.bincount(y, minlength=len(CLASS_NAMES)).astype(float)
    weights = torch.tensor((counts.sum() / (len(counts) * np.maximum(counts, 1))) ** args.weight_power, dtype=torch.float, device=dev)
    loss_fn = torch.nn.CrossEntropyLoss(weight=weights)
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    n_steps = int(np.ceil(len(y) / args.batch))
    sched = get_linear_schedule_with_warmup(opt, int(0.1 * args.epochs * n_steps), args.epochs * n_steps)
    use_amp = dev.type == "cuda"
    for epoch in range(args.epochs):
        model.train(); t0 = time.time(); total = 0.0
        for b in ft.batches(enc_tr, y, range(len(y)), args.batch, tok, shuffle=True):
            b = {k: v.to(dev) for k, v in b.items()}
            labels = b.pop("labels")
            with torch.autocast(device_type=dev.type, dtype=torch.bfloat16, enabled=use_amp):
                logits = model(**b).logits
            loss = loss_fn(logits.float(), labels)
            loss.backward(); torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step(); sched.step(); opt.zero_grad(); total += loss.item()
        print(f"    época {epoch + 1}/{args.epochs}  perda {total / n_steps:.4f}  ({time.time() - t0:.0f}s)", flush=True)
    probs = None
    if ev is not None:
        model.eval(); enc_ev, out = encode(tok, ev, args), []
        with torch.no_grad():
            for b in ft.batches(enc_ev, [0] * len(ev), range(len(ev)), 64, tok, shuffle=False):
                b = {k: v.to(dev) for k, v in b.items()}; b.pop("labels")
                out.append(torch.softmax(model(**b).logits.float(), dim=-1).cpu().numpy())
        probs = np.concatenate(out)
    if save_dir is not None:
        save_dir.mkdir(parents=True, exist_ok=True)
        model.half().save_pretrained(save_dir, safe_serialization=True); tok.save_pretrained(save_dir)
        print("    pesos salvos em", save_dir, flush=True)
    del model, opt
    torch.cuda.empty_cache()
    return probs


def save_npz(path, probs_by_seed, meta):
    seeds = sorted(probs_by_seed)
    np.savez_compressed(path, seeds=np.array(seeds), probs=np.stack([probs_by_seed[s] for s in seeds]), **meta)


def load_done(path, y):
    if not path.exists():
        return {}
    z = np.load(path)
    if not np.array_equal(z["y"], y):
        raise SystemExit(f"{path} é de outra divisão/dados; apague-o ou troque a --tag.")
    return {int(s): p for s, p in zip(z["seeds"], z["probs"])}


def main() -> None:
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--cv", action="store_true"); g.add_argument("--final", action="store_true"); g.add_argument("--export", action="store_true")
    ap.add_argument("--use-test", action="store_true")
    ap.add_argument("--ctx", choices=["none", "headline"], default="none")
    ap.add_argument("--seeds", default="42,7,123,2024,99")
    ap.add_argument("--tag", required=True)
    ap.add_argument("--epochs", type=int, default=4); ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--lr", type=float, default=2e-5); ap.add_argument("--weight-power", type=float, default=0.5)
    ap.add_argument("--max-len", type=int, default=0, help="0 = 128 sem contexto, 192 com contexto")
    ap.add_argument("--decision-weights", default="1,1,1", help="só no --export: multiplicadores por classe aplicados às probabilidades")
    ap.add_argument("--limit", type=int, default=0, help="teste de fumaça: usa só N frases por conjunto")
    args = ap.parse_args()
    args.max_len = args.max_len or (128 if args.ctx == "none" else 192)
    seeds = [int(s) for s in args.seeds.split(",")]

    dev = ft.device(); print("dispositivo:", dev, "| ctx:", args.ctx, "| max_len:", args.max_len, "| sementes:", seeds, flush=True)
    df = load_sentences(); df["ctx"] = headline_context(df); part = load_or_create(df)
    ft.REPORTS.mkdir(parents=True, exist_ok=True)

    if args.cv:
        devdf = dev_data(df, part)
        y, fold = devdf["label"].to_numpy(), devdf["fold"].to_numpy()
        path = ft.REPORTS / f"oof_{args.tag}.npz"
        done = load_done(path, y)
        meta = dict(y=y, fold=fold, story=devdf["story"].to_numpy().astype(str), row_id=devdf["row_id"].to_numpy())
        for seed in seeds:
            if seed in done:
                print(f"semente {seed}: já feita, pulando", flush=True)
                continue
            probs = np.zeros((len(devdf), len(CLASS_NAMES)), dtype=np.float32)
            for k in sorted(set(fold)):
                sel = fold == k
                tr, va = devdf[~sel], devdf[sel]
                if args.limit:
                    tr, va = tr.head(args.limit), va.head(args.limit)
                print(f"semente {seed} · fold {k}: treino {len(tr)} / validação {len(va)}", flush=True)
                probs[va.index.to_numpy()] = train_proba(tr, va, args, dev, seed)
            done[seed] = probs
            save_npz(path, done, meta)
            ok = probs.sum(1) > 0
            print(f"semente {seed}: F1 macro {f1_score(y[ok], probs[ok].argmax(1), average='macro'):.4f}", flush=True)
    elif args.final:
        devdf, te = dev_data(df, part), test_data(df, part, args.use_test)
        path = ft.REPORTS / f"test_probs_{args.tag}.npz"
        done = load_done(path, te["label"].to_numpy())
        meta = dict(y=te["label"].to_numpy(), story=te["story"].to_numpy().astype(str), row_id=te["row_id"].to_numpy())
        for seed in seeds:
            if seed in done:
                continue
            print(f"semente {seed}: treino {len(devdf)} / teste {len(te)}", flush=True)
            done[seed] = train_proba(devdf, te, args, dev, seed)
            save_npz(path, done, meta)
    else:
        require_test_permission(args.use_test, "export: teste usado como TREINO do modelo final, não como medida")
        out = ft.MODELS / args.tag
        for seed in seeds:
            print(f"semente {seed}: treino {len(df)} (100 histórias)", flush=True)
            train_proba(df, None, args, dev, seed, save_dir=out / f"seed{seed}")
        cfg = {"classes": CLASS_NAMES, "ctx": args.ctx, "max_len": args.max_len, "base_model": ft.MODEL, "seeds": seeds,
               "decision_weights": [float(x) for x in args.decision_weights.split(",")], "treinado_em": date.today().isoformat(),
               "treino": "100 histórias (desenvolvimento + teste)"}
        (out / "factnews_config.json").write_text(json.dumps(cfg, ensure_ascii=False, indent=2), encoding="utf-8")
        print("config salva em", out / "factnews_config.json")


if __name__ == "__main__":
    main()
