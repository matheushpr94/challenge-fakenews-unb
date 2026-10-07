"""Compara as variantes fora-da-dobra (SÓ desenvolvimento): contexto, conjunto de sementes e limiar de decisão.

REGRA FIXADA ANTES DE VER OS RESULTADOS: uma mudança só é adotada se o intervalo de 95% da diferença em F1 macro
(bootstrap pareado por HISTÓRIA, 2000 reamostragens) exclui zero. Diferença menor que isso é tratada como ruído.
  1. contexto: conjunto com manchete contra conjunto sem manchete
  2. conjunto de sementes: média das probabilidades contra a semente única (média das sementes)
  3. limiar: multiplicador da classe "enviesada" escolhido por validação cruzada ENTRE folds (nunca no mesmo fold)

Entrada: reports/oof_none.npz e reports/oof_headline.npz (de 04_context_ensemble.py --cv).
Saída: reports/comparacao_dev.md e reports/decisao_dev.json (a configuração que o treino final deve usar).
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from factnews.data import CLASS_NAMES  # noqa: E402

REPORTS = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "reports"  # argumento opcional: pasta de teste
GRID = [0.6, 0.8, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0]   # multiplicador da probabilidade de "enviesada"
N_BOOT, RNG = 2000, np.random.default_rng(42)


def f1s(y, pred):
    cm = np.bincount(y * 3 + pred, minlength=9).reshape(3, 3)
    tp = np.diag(cm); fp = cm.sum(0) - tp; fn = cm.sum(1) - tp
    return 2 * tp / np.maximum(2 * tp + fp + fn, 1)


def macro(y, pred):
    return float(f1s(y, pred).mean())


def load(tag):
    z = np.load(REPORTS / f"oof_{tag}.npz")
    return {k: z[k] for k in z.files}


def apply_w(P, w_env):
    return (P * np.array([1.0, 1.0, w_env])).argmax(1)


def crossfit(P, y, fold):
    """Escolhe o multiplicador nos outros folds e aplica no fold de fora."""
    pred, chosen = np.zeros(len(y), dtype=int), {}
    for k in sorted(set(fold.tolist())):
        tr = fold != k
        best = max(GRID, key=lambda w: macro(y[tr], apply_w(P[tr], w)))
        chosen[int(k)] = best
        pred[~tr] = apply_w(P[~tr], best)
    return pred, chosen


def paired(y, story, A, B):
    """Diferença média de F1 macro (A - B) e IC de 95%; A e B são listas de predições (média sobre elas)."""
    idx_by = {s: np.where(story == s)[0] for s in np.unique(story)}
    names = np.array(list(idx_by))
    score = lambda preds, idx: np.mean([macro(y[idx], p[idx]) for p in preds])
    full = np.arange(len(y))
    diffs = []
    for _ in range(N_BOOT):
        idx = np.concatenate([idx_by[s] for s in RNG.choice(names, len(names))])
        diffs.append(score(A, idx) - score(B, idx))
    lo, hi = np.percentile(diffs, [2.5, 97.5])
    return float(score(A, full) - score(B, full)), float(lo), float(hi)


def main() -> None:
    data = {t: load(t) for t in ("none", "headline") if (REPORTS / f"oof_{t}.npz").exists()}
    if len(data) < 2:
        raise SystemExit("Faltam reports/oof_none.npz e/ou reports/oof_headline.npz (rode 04_context_ensemble.py --cv).")
    ref = data["none"]
    y, fold, story = ref["y"], ref["fold"], ref["story"]
    seeds_common = sorted(set(map(int, ref["seeds"])) & set(map(int, data["headline"]["seeds"])))
    for t, d in data.items():
        assert np.array_equal(d["y"], y) and np.array_equal(d["fold"], fold), "variantes com divisões diferentes"
    P = {t: np.stack([d["probs"][list(map(int, d["seeds"])).index(s)] for s in seeds_common]) for t, d in data.items()}
    S = len(seeds_common)

    out = [f"# Comparação no desenvolvimento (4 folds por história, {len(y)} frases, {len(np.unique(story))} histórias)\n",
           f"Sementes comuns: {seeds_common}. Nada aqui lê o teste lacrado.\n",
           "| Variante | F1 macro por semente (média ± dp) | enviesada F1 (média ± dp) | **Conjunto** F1 macro | factual | citação | enviesada | prec. env. | rev. env. |",
           "|---|---|---|---:|---:|---:|---:|---:|---:|"]
    ens, single = {}, {}
    for t in data:
        single[t] = [P[t][i].argmax(1) for i in range(S)]
        ens[t] = P[t].mean(0)
        m = [macro(y, p) for p in single[t]]; e = [f1s(y, p)[2] for p in single[t]]
        f = f1s(y, ens[t].argmax(1)); pred = ens[t].argmax(1)
        tp = ((pred == 2) & (y == 2)).sum()
        out.append(f"| {t} | {np.mean(m):.4f} ± {np.std(m, ddof=1):.4f} | {np.mean(e):.3f} ± {np.std(e, ddof=1):.3f} | **{f.mean():.4f}** | "
                   f"{f[0]:.3f} | {f[1]:.3f} | {f[2]:.3f} | {tp / max(1, (pred == 2).sum()):.3f} | {tp / max(1, (y == 2).sum()):.3f} |")

    out += ["", "## Comparações pareadas (diferença em F1 macro; IC 95% por bootstrap de histórias)", "",
            "| Comparação | diferença | IC 95% | exclui zero? |", "|---|---:|---|---|"]
    def row(name, A, B):
        d, lo, hi = paired(y, story, A, B)
        out.append(f"| {name} | {d:+.4f} | [{lo:+.4f}, {hi:+.4f}] | {'SIM' if lo > 0 or hi < 0 else 'não'} |")
        return d, lo, hi
    c1 = row("1. contexto (conjunto com manchete − sem)", [ens["headline"].argmax(1)], [ens["none"].argmax(1)])
    row("1b. contexto, semente única (média)", single["headline"], single["none"])
    use_ctx = c1[1] > 0
    best = "headline" if use_ctx else "none"
    c2 = row(f"2. conjunto de {S} sementes − semente única ({best})", [ens[best].argmax(1)], single[best])
    use_ens = c2[1] > 0
    first = 42 if 42 in seeds_common else seeds_common[0]
    P_final = ens[best] if use_ens else P[best][seeds_common.index(first)]
    base_pred = P_final.argmax(1)

    cf_pred, chosen = crossfit(P_final, y, fold)
    c3 = row(f"3. limiar de 'enviesada' (validado entre folds) − sem limiar ({best}, {'conjunto' if use_ens else 'semente única'})", [cf_pred], [base_pred])
    use_thr = c3[1] > 0
    w_all = max(GRID, key=lambda w: macro(y, apply_w(P_final, w)))
    out += ["", f"Multiplicador escolhido em cada fold (tuned nos outros 3): {chosen}. Escolhido em todo o desenvolvimento: {w_all}.",
            f"Com limiar validado entre folds: F1 macro {macro(y, cf_pred):.4f} (enviesada {f1s(y, cf_pred)[2]:.3f}); sem: {macro(y, base_pred):.4f} (enviesada {f1s(y, base_pred)[2]:.3f}).", ""]
    per_fold = {int(k): round(macro(y[fold == k], base_pred[fold == k]), 4) for k in sorted(set(fold.tolist()))}
    out += [f"F1 macro por fold da configuração escolhida (sem limiar): {per_fold}", "",
            "## Decisão pela regra fixada",
            f"- contexto (manchete): {'ADOTAR' if use_ctx else 'não adotar (diferença dentro do ruído)'}",
            f"- conjunto de sementes: {'ADOTAR' if use_ens else 'não adotar (diferença dentro do ruído)'}",
            f"- limiar de 'enviesada': {'ADOTAR (multiplicador %s)' % w_all if use_thr else 'não adotar (diferença dentro do ruído)'}"]
    (REPORTS / "comparacao_dev.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    decision = {"ctx": "headline" if use_ctx else "none", "ensemble": bool(use_ens), "seeds": seeds_common if use_ens else [first],
                "w_env": float(w_all) if use_thr else 1.0}
    (REPORTS / "decisao_dev.json").write_text(json.dumps(decision, indent=2), encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(out)); print("\nDECISÃO:", json.dumps(decision))


if __name__ == "__main__":
    main()
