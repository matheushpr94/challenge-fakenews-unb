"""Matérias longas SIMULADAS: junta artigos reais do desenvolvimento (mesma editoria) até chegar a N frases e testa a regra do app.

O modelo classifica cada frase sozinho, então o tamanho só afeta a agregação (destaques / frases). Isto testa o EFEITO DO TAMANHO com
rótulos humanos e erros reais do modelo; NÃO cobre estilo de revista ou texto interpretativo. Só desenvolvimento (oof_none.npz).
Saída: reports/simulacao_longa_dev.md
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from factnews.data import load_sentences  # noqa: E402

REPORTS = Path(__file__).resolve().parents[1] / "reports"
T, MIN_SHARE, MIN_H = 0.80, 0.10, 2
LENGTHS = (20, 40, 70, 100, 150, 200)
DOCS = 400


def main() -> None:
    z = np.load(REPORTS / "oof_none.npz")
    P, y, rid = z["probs"], z["y"], z["row_id"]
    df = load_sentences().set_index("row_id").loc[rid].reset_index()
    body = ~df["is_title"].to_numpy()
    rng = np.random.default_rng(42)
    out = ["# Matérias longas simuladas (desenvolvimento, 5 sementes)\n",
           "Artigos reais da mesma editoria juntos até N frases. Mede o efeito do TAMANHO na regra (>= 2 destaques e >= 10%); não cobre estilo de revista.\n",
           "| Frases | docs | % real enviesada média | destaque médio | corr. destaque × real | dispara com real >= 8% | dispara com real < 8% | não dispara com real >= 12% |",
           "|---:|---:|---:|---:|---:|---:|---:|---:|"]
    arts = {a: g.index.to_numpy() for a, g in df[body].groupby("article")}
    dom = df.groupby("article")["domain"].first()
    by_dom = {d: [a for a in arts if dom[a] == d] for d in dom.unique()}
    for L in LENGTHS:
        rows = []
        for s in range(P.shape[0]):
            pred, conf = P[s].argmax(1), P[s].max(1)
            hl = (pred == 2) & (conf >= T); tb = y == 2
            for _ in range(DOCS // P.shape[0]):
                d = rng.choice([k for k, v in by_dom.items() if sum(len(arts[a]) for a in v) >= L])
                order = list(rng.permutation(by_dom[d])); idx = []
                for a in order:
                    idx += list(arts[a])
                    if len(idx) >= L: break
                idx = np.array(idx[:L])
                rows.append((hl[idx].sum() / len(idx), tb[idx].mean(), hl[idx].sum()))
        r = pd.DataFrame(rows, columns=["hs", "ts", "h"])
        fire = (r.h >= MIN_H) & (r.hs >= MIN_SHARE)
        hi, lo, very = r.ts >= 0.08, r.ts < 0.08, r.ts >= 0.12
        out.append(f"| {L} | {len(r)} | {r.ts.mean()*100:.1f}% | {r.hs.mean()*100:.1f}% | {np.corrcoef(r.hs, r.ts)[0,1]:.2f} | "
                   f"{fire[hi].mean():.2f} | {fire[lo].mean():.2f} | {(~fire[very]).mean() if very.any() else float('nan'):.2f} |")
    (REPORTS / "simulacao_longa_dev.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8"); print("\n".join(out))


if __name__ == "__main__":
    main()
