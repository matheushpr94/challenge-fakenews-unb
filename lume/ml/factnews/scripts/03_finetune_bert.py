"""Ajuste fino do BERTimbau (neuralmind/bert-base-portuguese-cased) no FactNews.

Teste de fumaça: `--cv --epochs 1 --limit 400`.

Modos:
  --cv                 validação cruzada POR HISTÓRIA nos folds 1..4 do desenvolvimento (não toca o teste)
  --final --use-test   treina em todo o desenvolvimento e mede UMA vez no teste lacrado
Saída: reports/bert_cv<tag>.json ou reports/bert_teste.json
Exportar pesos, conjuntos de sementes e contexto: veja 04_context_ensemble.py.
"""
from __future__ import annotations

import argparse
import json
import random
import sys
import time
from pathlib import Path

import numpy as np
import torch
from sklearn.metrics import classification_report, f1_score
from torch.utils.data import DataLoader
from transformers import AutoModelForSequenceClassification, AutoTokenizer, get_linear_schedule_with_warmup

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from factnews.data import CLASS_NAMES, load_sentences  # noqa: E402
from factnews.splits import dev_data, load_or_create, test_data  # noqa: E402

REPORTS = Path(__file__).resolve().parents[1] / "reports"
MODELS = Path(__file__).resolve().parents[1] / "models"
MODEL = "neuralmind/bert-base-portuguese-cased"


def set_seed(seed: int) -> None:
    random.seed(seed); np.random.seed(seed); torch.manual_seed(seed); torch.cuda.manual_seed_all(seed)


def device() -> torch.device:
    if torch.cuda.is_available():
        return torch.device("cuda")
    if getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def encode(tok, texts: list[str], max_len: int):
    return tok(texts, truncation=True, max_length=max_len)


def batches(enc, labels, idx, bs, tok, shuffle):
    order = np.array(idx)
    if shuffle:
        np.random.shuffle(order)
    for i in range(0, len(order), bs):
        sel = order[i:i + bs]
        feats = [{k: enc[k][j] for k in enc.keys()} for j in sel]
        batch = tok.pad(feats, return_tensors="pt")
        batch["labels"] = torch.tensor([labels[j] for j in sel])
        yield batch


def train_and_predict(tr_texts, tr_y, ev_texts, args, dev) -> np.ndarray:
    tok = AutoTokenizer.from_pretrained(MODEL)
    model = AutoModelForSequenceClassification.from_pretrained(MODEL, num_labels=len(CLASS_NAMES)).to(dev)
    enc_tr, enc_ev = encode(tok, tr_texts, args.max_len), encode(tok, ev_texts, args.max_len)
    counts = np.bincount(tr_y, minlength=len(CLASS_NAMES)).astype(float)
    weights = torch.tensor((counts.sum() / (len(counts) * np.maximum(counts, 1))) ** args.weight_power, dtype=torch.float, device=dev)
    loss_fn = torch.nn.CrossEntropyLoss(weight=weights)
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    steps = args.epochs * int(np.ceil(len(tr_y) / args.batch))
    sched = get_linear_schedule_with_warmup(opt, int(0.1 * steps), steps)
    use_amp = dev.type == "cuda"
    for epoch in range(args.epochs):
        model.train(); t0 = time.time(); total = 0.0
        for b in batches(enc_tr, tr_y, range(len(tr_y)), args.batch, tok, shuffle=True):
            b = {k: v.to(dev) for k, v in b.items()}
            labels = b.pop("labels")
            with torch.autocast(device_type=dev.type, dtype=torch.bfloat16, enabled=use_amp):
                logits = model(**b).logits
            loss = loss_fn(logits.float(), labels)
            loss.backward(); torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step(); sched.step(); opt.zero_grad(); total += float(loss)
        print(f"  época {epoch + 1}/{args.epochs}  perda média {total / max(1, int(np.ceil(len(tr_y) / args.batch))):.4f}  ({time.time() - t0:.0f}s)")
    model.eval(); preds = []
    with torch.no_grad():
        dummy = [0] * len(ev_texts)
        for b in batches(enc_ev, dummy, range(len(ev_texts)), 64, tok, shuffle=False):
            b = {k: v.to(dev) for k, v in b.items()}; b.pop("labels")
            preds.append(model(**b).logits.argmax(-1).cpu().numpy())
    return np.concatenate(preds)


def summarize(y, pred) -> dict:
    rep = classification_report(y, pred, target_names=CLASS_NAMES, output_dict=True, zero_division=0)
    return {"macro_f1": rep["macro avg"]["f1-score"], "f1": {c: rep[c]["f1-score"] for c in CLASS_NAMES},
            "precisao": {c: rep[c]["precision"] for c in CLASS_NAMES}, "revocacao": {c: rep[c]["recall"] for c in CLASS_NAMES}}


def main() -> None:
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--cv", action="store_true"); g.add_argument("--final", action="store_true")
    ap.add_argument("--weight-power", type=float, default=0.5, help="expoente do peso de classe (0 = sem peso, 1 = inverso da frequência)")
    ap.add_argument("--tag", default="", help="sufixo do arquivo de saída do --cv")
    ap.add_argument("--use-test", action="store_true", help="necessário com --final: lê o teste lacrado")
    ap.add_argument("--epochs", type=int, default=4); ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--lr", type=float, default=2e-5); ap.add_argument("--max-len", type=int, default=128)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--limit", type=int, default=0, help="usa só N frases por conjunto (teste de fumaça)")
    args = ap.parse_args()

    set_seed(args.seed); dev = device(); print("dispositivo:", dev)
    df = load_sentences(); part = load_or_create(df); devdf = dev_data(df, part)
    REPORTS.mkdir(parents=True, exist_ok=True)

    if args.cv:
        pred = np.full(len(devdf), -1); folds = sorted(devdf["fold"].unique())
        for k in folds:
            tr, va = devdf[devdf["fold"] != k], devdf[devdf["fold"] == k]
            if args.limit:
                tr, va = tr.head(args.limit), va.head(args.limit)
            print(f"fold {k}: treino {len(tr)} / validação {len(va)} frases")
            pred[va.index] = train_and_predict(tr["text"].tolist(), tr["label"].to_numpy(), va["text"].tolist(), args, dev)
        ok = pred >= 0
        res = {"args": vars(args), "por_historia": summarize(devdf["label"].to_numpy()[ok], pred[ok]),
               "macro_f1_por_fold": [float(f1_score(devdf.loc[devdf["fold"] == k, "label"], pred[(devdf["fold"] == k).to_numpy()], average="macro"))
                                     for k in folds if (pred[(devdf["fold"] == k).to_numpy()] >= 0).all()]}
        (REPORTS / f"bert_cv{args.tag}.json").write_text(json.dumps(res, ensure_ascii=False, indent=2), encoding="utf-8")
    else:
        te = test_data(df, part, args.use_test)
        tr = devdf.head(args.limit) if args.limit else devdf
        pred = train_and_predict(tr["text"].tolist(), tr["label"].to_numpy(), te["text"].tolist(), args, dev)
        res = {"args": vars(args), "teste": summarize(te["label"].to_numpy(), pred)}
        (REPORTS / "bert_teste.json").write_text(json.dumps(res, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: v for k, v in res.items() if k != "args"}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
