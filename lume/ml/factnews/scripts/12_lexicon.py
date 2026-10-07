"""Ideia 3: segunda opinião por léxico de opinião em português (OpLexicon v3 e SentiLex-PT02), SÓ no desenvolvimento.

Regra testada: um trecho só é destacado quando o modelo diz "enviesada" (confiança >= 0,80) E a frase tem pelo menos K palavras
de polaridade no léxico. Não cria placar novo: o léxico apenas confirma ou veta o destaque do modelo.
Critério fixado ANTES de olhar os resultados: precisão do destaque +0,05 ou mais, com perda de cobertura de no máximo 0,10, e o
intervalo de 95% (bootstrap por história) da diferença de precisão exclui zero.
Os léxicos são lidos de data/external/lex/*.rda (não versionado; a licença dos dados não está esclarecida, então não são redistribuídos).
Saída: reports/lexicon_dev.md
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

import numpy as np
import pyreadr

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from factnews.data import load_sentences  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / "reports"
TOKEN = re.compile(r"[a-zà-ÿç]+")


def load_lexicon() -> set[str]:
    op = pyreadr.read_r(ROOT / "data/external/lex/oplexicon_v3.0.rda")["oplexicon_v3.0"]
    sl = pyreadr.read_r(ROOT / "data/external/lex/sentiLex_lem_PT02.rda")["sentiLex_lem_PT02"]
    words = set(op.loc[ops_ok(op), "term"].str.lower()) | set(sl["term"].str.lower())
    return {w for w in words if len(w) >= 4 and w.isalpha()}


def ops_ok(op):
    return ops_type(op) & (ops_pol(op))


def ops_type(op):
    return op["type"].isin(["adj", "vb", "n"])


def ops_pol(op):
    return op["polarity"].isin([-1, 1])


def main() -> None:
    lex = load_lexicon()
    z = np.load(REPORTS / "oof_none.npz")
    P, y, rid, story = z["probs"].mean(0), z["y"], z["row_id"], z["story"]
    df = load_sentences().set_index("row_id").loc[rid].reset_index()
    hits = np.array([sum(t in lex for t in TOKEN.findall(s.lower())) for s in df["text"]])
    hl = (P.argmax(1) == 2) & (P.max(1) >= 0.80); tb = y == 2
    stories = np.unique(story); idx_by = {s: np.where(story == s)[0] for s in stories}
    rng = np.random.default_rng(42)

    def pr(mask, sel):  # precisão e cobertura de `mask` (destaques) sobre as enviesadas reais em `sel`
        m = mask[sel]; t = tb[sel]
        return (t[m].mean() if m.any() else np.nan), (m & t).sum() / max(1, t.sum())

    base_p, base_c = pr(hl, slice(None))
    out = [f"# Léxico como segunda opinião (desenvolvimento, {len(y)} frases, {len(stories)} histórias; modelo = média de 5 sementes)\n",
           f"Léxico: {len(lex)} palavras (OpLexicon v3 adj/verbo/subst. com polaridade ±1 + SentiLex-PT02, >= 4 letras). "
           f"Frases com ao menos 1 palavra do léxico: {(hits >= 1).mean():.0%} (entre as enviesadas reais: {(hits[tb] >= 1).mean():.0%}; entre as demais: {(hits[~tb] >= 1).mean():.0%}).\n",
           f"Linha de base (destaque do modelo, confiança >= 0,80): precisão {base_p:.3f}, cobertura {base_c:.3f}.\n",
           "| Regra | precisão | cobertura | Δ precisão [IC 95%] | Δ cobertura [IC 95%] | critério |", "|---|---:|---:|---|---|---|"]
    best = None
    for k in (1, 2, 3):
        gate = hl & (hits >= k); p, c = pr(gate, slice(None))
        dp, dc = [], []
        for _ in range(2000):
            sel = np.concatenate([idx_by[s] for s in rng.choice(stories, len(stories))])
            bp, bc = pr(hl, sel); gp, gc = pr(gate, sel)
            dp.append(gp - bp); dc.append(gc - bc)
        lo, hi = np.nanpercentile(dp, [2.5, 97.5]); clo, chi = np.nanpercentile(dc, [2.5, 97.5])
        ok = (p - base_p) >= 0.05 and (c - base_c) >= -0.10 and lo > 0
        out.append(f"| modelo diz enviesada e a frase tem ≥ {k} palavra(s) do léxico | {p:.3f} | {c:.3f} | {p - base_p:+.3f} [{lo:+.3f}; {hi:+.3f}] | {c - base_c:+.3f} [{clo:+.3f}; {chi:+.3f}] | {'ATENDIDO' if ok else 'não'} |")
    out.append("\nCom a regra: o léxico só pode tirar destaques; nunca cria novos. Resultado e decisão estão em DOCUMENTACAO.md.")
    (REPORTS / "lexicon_dev.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8"); print("\n".join(out))


if __name__ == "__main__":
    main()
