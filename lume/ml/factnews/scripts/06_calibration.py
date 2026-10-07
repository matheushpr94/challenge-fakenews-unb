"""Calibração da confiança do modelo (SÓ desenvolvimento, fora-da-dobra; não lê o teste lacrado).

Responde: quando o modelo diz 0,85 de confiança, ele acerta quanto? Isso fixa os limites usados pelo app
(título "basta", frase destacada como enviesada, "inconclusivo"). Saída: reports/calibracao_dev.md e .json
Usa as 5 sementes de reports/oof_none.npz: cada semente é avaliada separadamente e a tabela mostra média e mínimo/máximo.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from factnews.data import CLASS_NAMES, load_sentences  # noqa: E402

REPORTS = Path(__file__).resolve().parents[1] / "reports"
THRESHOLDS = [0.50, 0.60, 0.70, 0.80, 0.85, 0.90, 0.95]


def main() -> None:
    z = np.load(REPORTS / "oof_none.npz")
    P, y, rid = z["probs"], z["y"], z["row_id"]            # P: (sementes, frases, 3)
    df = load_sentences().set_index("row_id").loc[rid].reset_index()
    assert (df["label"].to_numpy() == y).all()
    is_title = df["is_title"].to_numpy()
    out = ["# Calibração da confiança (desenvolvimento, fora-da-dobra, 5 sementes)\n",
           f"{len(y)} frases ({int(is_title.sum())} manchetes). Não lê o teste lacrado. "
           "Cada célula: média entre sementes [mín–máx]; n = frases acima do limite (média).\n"]
    result = {}

    def table(title, mask, classes):
        out.append(f"\n## {title}\n")
        out.append("| Classe prevista | limite | n | precisão (acerto) | cobertura (das reais da classe) |")
        out.append("|---|---:|---:|---:|---:|")
        for c in classes:
            for t in THRESHOLDS:
                precs, covs, ns = [], [], []
                for s in range(P.shape[0]):
                    pred, conf = P[s].argmax(1), P[s].max(1)
                    sel = mask & (pred == c) & (conf >= t)
                    real = mask & (y == c)
                    ns.append(sel.sum())
                    precs.append((y[sel] == c).mean() if sel.sum() else np.nan)
                    covs.append(((y == c) & sel).sum() / max(1, real.sum()))
                pr = np.array(precs)
                out.append(f"| {CLASS_NAMES[c]} | {t:.2f} | {np.mean(ns):.0f} | {np.nanmean(pr):.3f} [{np.nanmin(pr):.3f}–{np.nanmax(pr):.3f}] | {np.mean(covs):.3f} |")
                result.setdefault(title, {}).setdefault(CLASS_NAMES[c], {})[f"{t:.2f}"] = {
                    "n": float(np.mean(ns)), "precisao": float(np.nanmean(pr)), "cobertura": float(np.mean(covs))}

    table("Todas as frases", np.ones(len(y), bool), [2, 1, 0])
    table("Só manchetes (o caso do título no app)", is_title, [2, 1, 0])
    table("Só corpo (frases de matéria)", ~is_title, [2])

    # "Inconclusivo" por confiança baixa: acerto da classe prevista conforme a confiança máxima
    out.append("\n## Acerto geral conforme a confiança máxima (para o estado 'inconclusivo')\n")
    out.append("| confiança máxima | frases (corpo) | acerto no corpo | frases (manchete) | acerto na manchete |")
    out.append("|---|---:|---:|---:|---:|")
    bins = [(0, 0.5), (0.5, 0.6), (0.6, 0.7), (0.7, 0.8), (0.8, 0.9), (0.9, 1.01)]
    for lo, hi in bins:
        row = [f"{lo:.1f}–{min(hi, 1):.1f}"]
        for m in (~is_title, is_title):
            ns, acc = [], []
            for s in range(P.shape[0]):
                conf, pred = P[s].max(1), P[s].argmax(1)
                sel = m & (conf >= lo) & (conf < hi)
                ns.append(sel.sum()); acc.append((pred[sel] == y[sel]).mean() if sel.sum() else np.nan)
            row += [f"{np.mean(ns):.0f}", f"{np.nanmean(acc):.3f}" if not np.all(np.isnan(acc)) else "-"]
        out.append("| " + " | ".join(row) + " |")

    # Nível da matéria: quantas frases destacadas (enviesada, confiança >= LIMIAR_DESTAQUE) e o que isso diz sobre a matéria
    import pandas as pd
    T = 0.80
    rows = []
    for sd in range(P.shape[0]):
        pred, conf = P[sd].argmax(1), P[sd].max(1)
        d = df.assign(hl=((pred == 2) & (conf >= T)), true_b=(y == 2))[~is_title]
        g = d.groupby("article").agg(n=("hl", "size"), hl=("hl", "sum"), tb=("true_b", "sum")); g["seed"] = sd
        rows.append(g)
    g = pd.concat(rows); g = g[g.n >= 8]
    out.append(f"\n## Nível da matéria (corpo com >= 8 frases; destaque = 'enviesada' com confiança >= {T:.2f})\n")
    out.append("| frases destacadas | matérias (média/semente) | com >= 1 enviesada real | com >= 2 reais | média de reais |")
    out.append("|---|---:|---:|---:|---:|")
    for k in (0, 1, 2, 3):
        m = (g.hl == k) if k < 3 else (g.hl >= k); sub = g[m]
        out.append(f"| {'3 ou mais' if k == 3 else k} | {m.groupby(g.seed).sum().mean():.0f} | {(sub.tb >= 1).mean():.2f} | {(sub.tb >= 2).mean():.2f} | {sub.tb.mean():.2f} |")
    out.append(f"\nMatérias sem nenhuma frase enviesada real: {(g.tb == 0).mean():.2f}.")

    # Regra de matéria usada no app: só a contagem de destaques x contagem E proporção mínima (MIN_SHARE do app = 0,10)
    g["share"] = g.hl / g.n
    out.append("\n## Regra de matéria: contagem × contagem e proporção (n = frases do corpo; sementes juntas)\n")
    out.append("| Grupo | Regra | dispara em (média/semente) | com >= 1 enviesada real | com >= 2 reais | cobre dos com >= 2 reais |")
    out.append("|---|---|---:|---:|---:|---:|")
    groups = (("todas (8 ou mais frases)", g.n >= 8), ("8 a 29 frases", (g.n >= 8) & (g.n < 30)), ("30 ou mais frases", g.n >= 30))
    rules = (("2 ou mais destaques", g.hl >= 2), ("2 ou mais destaques E >= 10% do texto", (g.hl >= 2) & (g.share >= 0.10)))
    for gname, gm in groups:
        for rname, rm in rules:
            fire = rm & gm; sub = g[fire]
            if len(sub) == 0:
                out.append(f"| {gname} | {rname} | 0 | - | - | - |"); continue
            cover = ((g.tb >= 2) & fire).sum() / max(1, ((g.tb >= 2) & gm).sum())
            out.append(f"| {gname} | {rname} | {fire.groupby(g.seed).sum().mean():.1f} | {(sub.tb >= 1).mean():.2f} | {(sub.tb >= 2).mean():.2f} | {cover:.2f} |")
    out.append(f"\nA maior matéria da base tem {int(g.n.max())} frases no corpo; acima disso nada foi calibrado. "
               f"Proporção média de destaques por matéria: {g.share.mean():.3f} (p90: {g.share.quantile(.9):.3f}); proporção média de enviesadas reais: {(g.tb / g.n).mean():.3f}.")
    (REPORTS / "calibracao_dev.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    (REPORTS / "calibracao_dev.json").write_text(json.dumps(result, indent=1, ensure_ascii=False), encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(out))


if __name__ == "__main__":
    main()
