"""Ideia 4: contraste entre veículos. Teste de viabilidade no FactNews (cada história sai em 3 veículos), SÓ no desenvolvimento.

Para cada frase, procura a frase mais parecida nos OUTROS veículos da mesma história e cria: novidade (1 - similaridade máxima),
existência de par próximo, e a diferença de "carga" (logit de enviesada do modelo) entre a frase e seu par. Pergunta: isso prevê
"enviesada" ALÉM do que o próprio modelo já prevê? Compara regressão logística só com o logit do modelo contra a mesma com os atributos
de contraste, em validação cruzada por história (os folds do desenvolvimento).
Pareamento: TF-IDF (limiar 0,35) ou, com --semantic, o modelo de sentido do Lume (paraphrase-multilingual via Ollama, limiar 0,72).
Critério fixado ANTES de olhar: ganho de precisão média (AP) >= 0,02 e intervalo de 95% (bootstrap por história) que exclui zero.
Saída: reports/contraste_dev.md (ou contraste_semantico_dev.md)
"""
from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from pathlib import Path

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from factnews.data import load_sentences  # noqa: E402

REPORTS = ROOT / "reports"


def logit(p):
    p = np.clip(p, 1e-4, 1 - 1e-4)
    return np.log(p / (1 - p))


def ollama_embeddings(texts: list[str], cache: Path) -> np.ndarray:
    if cache.exists():
        return np.load(cache)
    out = []
    for i in range(0, len(texts), 64):
        body = json.dumps({"model": "paraphrase-multilingual", "input": [t[:500] for t in texts[i:i + 64]]}).encode("utf-8")
        req = urllib.request.Request("http://127.0.0.1:11434/api/embed", data=body, headers={"Content-Type": "application/json"})
        out += json.load(urllib.request.urlopen(req, timeout=120))["embeddings"]
    e = np.array(out, dtype=np.float32)
    e /= np.linalg.norm(e, axis=1, keepdims=True)
    cache.parent.mkdir(exist_ok=True)
    np.save(cache, e)
    return e


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--semantic", action="store_true")
    args = ap.parse_args()
    thr = 0.72 if args.semantic else 0.35
    z = np.load(REPORTS / "oof_none.npz")
    P, y, rid, fold, story = z["probs"].mean(0), z["y"], z["row_id"], z["fold"], z["story"]
    df = load_sentences().set_index("row_id").loc[rid].reset_index()
    le = logit(P[:, 2])
    tb = (y == 2).astype(int)
    X = TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True).fit(df["text"]).transform(df["text"])
    E = ollama_embeddings(df["text"].tolist(), ROOT / "data" / "dev_embeddings_paraphrase.npy") if args.semantic else None
    nov, has, dlt, sim_max = np.ones(len(df)), np.zeros(len(df)), np.zeros(len(df)), np.zeros(len(df))
    for _, g in df.groupby("story"):
        idx = g.index.to_numpy()
        outlets = g["outlet"].to_numpy()
        S = (E[idx] @ E[idx].T) if args.semantic else (X[idx] @ X[idx].T).toarray()
        for a, i in enumerate(idx):
            other = outlets != outlets[a]
            if not other.any():
                continue
            j = int(np.argmax(np.where(other, S[a], -1)))
            sim = float(S[a, j])
            sim_max[i], nov[i] = sim, 1 - sim
            if sim >= thr:                                   # há um par claro em outro veículo
                has[i], dlt[i] = 1, le[i] - le[idx[j]]
    F0, F1 = le[:, None], np.column_stack([le, nov, has, dlt])

    def cv_scores(F):
        s = np.zeros(len(y))
        for k in np.unique(fold):
            tr, te = fold != k, fold == k
            s[te] = LogisticRegression(C=1.0, max_iter=1000).fit(F[tr], tb[tr]).decision_function(F[te])
        return s

    s0, s1 = cv_scores(F0), cv_scores(F1)
    stories = np.unique(story)
    idx_by = {s: np.where(story == s)[0] for s in stories}
    rng = np.random.default_rng(42)
    d = []
    for _ in range(2000):
        sel = np.concatenate([idx_by[s] for s in rng.choice(stories, len(stories))])
        d.append(average_precision_score(tb[sel], s1[sel]) - average_precision_score(tb[sel], s0[sel]))
    lo, hi = np.percentile(d, [2.5, 97.5])
    ap0, ap1 = average_precision_score(tb, s0), average_precision_score(tb, s1)
    ok = (ap1 - ap0) >= 0.02 and lo > 0
    kind = "semântico (Ollama, paraphrase-multilingual)" if args.semantic else "por palavras (TF-IDF)"
    out = [f"# Contraste entre veículos, pareamento {kind} (desenvolvimento, 4 folds por história)\n",
           f"Frases com par claro (similaridade >= {thr}) em outro veículo da mesma história: {has.mean():.0%}. Similaridade máxima mediana: {np.median(sim_max):.2f}.\n",
           f"Entre as enviesadas reais: {has[tb == 1].mean():.0%} têm par; entre as demais: {has[tb == 0].mean():.0%}. "
           f"Novidade média: enviesadas {nov[tb == 1].mean():.3f}, demais {nov[tb == 0].mean():.3f}.\n",
           "| Modelo | AP (enviesada) |", "|---|---:|", f"| só o logit do modelo | {ap0:.3f} |",
           f"| + novidade, par e diferença de carga | {ap1:.3f} |",
           f"\nGanho de AP: **{ap1 - ap0:+.3f}** [IC 95% {lo:+.3f}; {hi:+.3f}] — critério (>= +0,02 e IC acima de zero): **{'ATENDIDO' if ok else 'NÃO atendido'}**."]
    name = "contraste_semantico_dev.md" if args.semantic else "contraste_dev.md"
    (REPORTS / name).write_text("\n".join(out) + "\n", encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(out))


if __name__ == "__main__":
    main()
