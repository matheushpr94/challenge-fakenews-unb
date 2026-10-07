"""Ideias 1, 2 e 3: robustez a outro VEÍCULO, outra ÉPOCA e outro ASSUNTO. Só desenvolvimento (teste lacrado intocado).

Para cada grupo de fora (um jornal, um período, uma editoria): treina o BERTimbau (mesma receita, semente 42) SEM esse grupo e mede nele.
A linha de base é o próprio modelo da validação por história (oof_none.npz, semente 42) nas MESMAS frases, treinado COM os outros grupos.
A diferença (de fora − linha de base) tem IC 95% por bootstrap de histórias. Observação: os grupos de fora são menores e o treino também é
menor (cerca de 2/3 dos dados em "sem um jornal"), então parte da queda é só falta de dados.
Saída: reports/robustez_dev.md e .json
"""
from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path

import numpy as np
import torch
from sklearn.metrics import f1_score

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from factnews.data import load_sentences  # noqa: E402
from factnews.splits import dev_data, load_or_create  # noqa: E402

_s = importlib.util.spec_from_file_location("o9", Path(__file__).with_name("09_ood.py"))
o9 = importlib.util.module_from_spec(_s)
_s.loader.exec_module(o9)
ft = o9.ft
REPORTS = ROOT / "reports"


@torch.no_grad()
def predict_probs(tok, model, texts, dev, bs=64):
    out = []
    for i in range(0, len(texts), bs):
        enc = tok(texts[i:i + bs], truncation=True, max_length=128, padding=True, return_tensors="pt").to(dev)
        out.append(torch.softmax(model(**enc).logits.float(), -1).cpu().numpy())
    return np.concatenate(out)


def macro(y, p):
    return float(f1_score(y, p, average="macro", labels=[0, 1, 2], zero_division=0))


def env_f1(y, p):
    return float(f1_score(y, p, average=None, labels=[0, 1, 2], zero_division=0)[2])


def main() -> None:
    dev = ft.device()
    df = load_sentences(); part = load_or_create(df); d = dev_data(df, part)
    d["year_i"] = d["year"].astype(str).str[:4].astype(int)
    z = np.load(REPORTS / "oof_none.npz")
    seeds = list(map(int, z["seeds"])); base = {int(r): p for r, p in zip(z["row_id"], z["probs"][seeds.index(42)])}
    experiments = []
    for o in sorted(d["outlet"].unique()):
        experiments.append((f"sem o jornal {o}", d["outlet"] != o, d["outlet"] == o))
    experiments.append(("passado -> presente (treino 2006 a 2008; teste 2021 e 2022)", d["year_i"] <= 2008, d["year_i"] >= 2021))
    experiments.append(("presente -> passado (treino 2021 e 2022; teste 2006 a 2008)", d["year_i"] >= 2021, d["year_i"] <= 2008))
    for dom in ("sports", "politics", "world", "daily"):
        experiments.append((f"sem a editoria {dom}", d["domain"] != dom, d["domain"] == dom))
    rng = np.random.default_rng(42)
    rows = []
    for name, trm, tem in experiments:
        tr, te = d[trm], d[tem].reset_index(drop=True)
        print(f"{name}: treino {len(tr)} / teste {len(te)}", flush=True)
        tok, model = o9.train_model(tr, dev)
        p_out = predict_probs(tok, model, te["text"].tolist(), dev)
        del model; torch.cuda.empty_cache()
        p_base = np.array([base[r] for r in te["row_id"]])
        y = te["label"].to_numpy(); st = te["story"].to_numpy()
        pred_o, pred_b = p_out.argmax(1), p_base.argmax(1)
        stories = np.unique(st); idx_by = {s: np.where(st == s)[0] for s in stories}
        diffs, diffs_e = [], []
        for _ in range(1000):
            sel = np.concatenate([idx_by[s] for s in rng.choice(stories, len(stories))])
            diffs.append(macro(y[sel], pred_o[sel]) - macro(y[sel], pred_b[sel]))
            diffs_e.append(env_f1(y[sel], pred_o[sel]) - env_f1(y[sel], pred_b[sel]))
        rows.append({"experimento": name, "treino": len(tr), "teste": len(te), "historias_teste": len(stories), "enviesadas_teste": int((y == 2).sum()),
                     "f1_macro_de_fora": macro(y, pred_o), "f1_macro_base": macro(y, pred_b), "dif_macro_ic": [float(np.percentile(diffs, 2.5)), float(np.percentile(diffs, 97.5))],
                     "f1_env_de_fora": env_f1(y, pred_o), "f1_env_base": env_f1(y, pred_b), "dif_env_ic": [float(np.percentile(diffs_e, 2.5)), float(np.percentile(diffs_e, 97.5))]})
    out = ["# Robustez a outro jornal, outra época e outro assunto (só desenvolvimento)\n",
           "Modelo treinado SEM o grupo e medido NO grupo, contra o modelo da validação por história nas mesmas frases (semente 42). "
           "Δ = de fora − base, com IC 95% por bootstrap de histórias. Treino menor explica parte da queda.\n",
           "| Experimento | treino | teste (frases / histórias / enviesadas) | F1 macro de fora | F1 macro base | Δ macro [IC 95%] | F1 enviesada de fora | F1 enviesada base | Δ enviesada [IC 95%] |",
           "|---|---:|---|---:|---:|---|---:|---:|---|"]
    for r in rows:
        a, b = r["dif_macro_ic"]; c, e = r["dif_env_ic"]
        out.append(f"| {r['experimento']} | {r['treino']} | {r['teste']} / {r['historias_teste']} / {r['enviesadas_teste']} | {r['f1_macro_de_fora']:.3f} | {r['f1_macro_base']:.3f} | "
                   f"{r['f1_macro_de_fora'] - r['f1_macro_base']:+.3f} [{a:+.3f}; {b:+.3f}] | {r['f1_env_de_fora']:.3f} | {r['f1_env_base']:.3f} | "
                   f"{r['f1_env_de_fora'] - r['f1_env_base']:+.3f} [{c:+.3f}; {e:+.3f}] |")
    (REPORTS / "robustez_dev.json").write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")
    (REPORTS / "robustez_dev.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8"); print("\n".join(out), flush=True)


if __name__ == "__main__":
    main()
