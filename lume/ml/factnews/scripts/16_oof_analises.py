"""Ideias 4, 5, 6 e 8 sobre as previsões fora-da-dobra do desenvolvimento (oof_none.npz, semente 42 = receita do modelo exportado).

4) Calibração: a confiança do modelo significa o que diz? ECE antes e depois de escala de temperatura (ajustada nos outros folds).
5) Predição conforme (por classe): o modelo "passa" (inconclusivo) para manter uma taxa de erro escolhida; mede cobertura e acerto.
6) Tipologia de erros: em que tipos de frase o modelo erra mais (com IC 95% por bootstrap de histórias).
8) Anotadores: quantas frases têm discordância entre os dois anotadores, e se rótulos suaves poderiam mudar algo.
Saída: reports/analises_oof_dev.md
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from factnews.data import CLASS_NAMES, load_annotators, load_sentences  # noqa: E402

REPORTS = ROOT / "reports"
rng = np.random.default_rng(42)


def ece(conf, correct, bins=15):
    edges = np.linspace(0, 1, bins + 1); tot = 0.0
    for lo, hi in zip(edges[:-1], edges[1:]):
        m = (conf > lo) & (conf <= hi)
        if m.any():
            tot += m.mean() * abs(correct[m].mean() - conf[m].mean())
    return float(tot)


def nll(p, y):
    return float(-np.log(np.clip(p[np.arange(len(y)), y], 1e-9, 1)).mean())


def softmax_t(logp, t):
    z = logp / t
    z = z - z.max(1, keepdims=True)
    e = np.exp(z)
    return e / e.sum(1, keepdims=True)


def main() -> None:
    z = np.load(REPORTS / "oof_none.npz")
    seeds = list(map(int, z["seeds"])); P = z["probs"][seeds.index(42)].astype(np.float64)
    y, fold, story, rid = z["y"], z["fold"], z["story"], z["row_id"]
    df = load_sentences().set_index("row_id").loc[rid].reset_index()
    logp = np.log(np.clip(P, 1e-9, 1)); folds = np.unique(fold)
    out = [f"# Análises sobre as previsões fora-da-dobra (desenvolvimento, {len(y)} frases, {len(np.unique(story))} histórias; semente 42)\n"]

    # ---- 4) calibração
    grid = np.linspace(0.6, 3.0, 49); Pc = np.zeros_like(P); temps = {}
    for k in folds:
        tr, te = fold != k, fold == k
        t = grid[int(np.argmin([nll(softmax_t(logp[tr], g), y[tr]) for g in grid]))]; temps[int(k)] = float(t)
        Pc[te] = softmax_t(logp[te], t)
    conf0, conf1 = P.max(1), Pc.max(1); ok0, ok1 = (P.argmax(1) == y), (Pc.argmax(1) == y)
    pe0, pe1 = P[:, 2], Pc[:, 2]; te_ = (y == 2).astype(float)
    out += ["## 4) Calibração (escala de temperatura ajustada nos outros folds)\n",
            f"Temperaturas por fold: {temps} (T > 1 = o modelo era confiante demais).\n",
            "| Medida | antes | depois |", "|---|---:|---:|",
            f"| ECE da classe prevista (15 faixas) | {ece(conf0, ok0):.3f} | {ece(conf1, ok1):.3f} |",
            f"| ECE da probabilidade de 'enviesada' | {ece(pe0, te_ > 0.5):.3f}* | {ece(pe1, te_ > 0.5):.3f}* |",
            f"| NLL (perda logarítmica) | {nll(P, y):.3f} | {nll(Pc, y):.3f} |",
            f"| Acurácia (não muda: a escala preserva a ordem) | {ok0.mean():.3f} | {ok1.mean():.3f} |",
            "\n*ECE tratando P(enviesada) como confiança de um evento binário (faixas de P, não da classe prevista).\n",
            "Confiança da classe prevista versus acerto (antes → depois):\n", "| faixa de confiança | n | acerto | confiança média (antes) | confiança média (depois) |", "|---|---:|---:|---:|---:|"]
    for lo, hi in ((0, .6), (.6, .8), (.8, .9), (.9, .97), (.97, 1.01)):
        m = (conf0 > lo) & (conf0 <= hi)
        if m.any():
            out.append(f"| {lo:.2f}–{min(hi, 1):.2f} | {m.sum()} | {ok0[m].mean():.3f} | {conf0[m].mean():.3f} | {conf1[m].mean():.3f} |")

    # ---- 5) predição conforme por classe (Mondrian), calibrando nos outros folds
    out += ["\n## 5) 'Inconclusivo' com garantia (predição conforme por classe)\n",
            "Para cada nível α, o modelo devolve um CONJUNTO de classes; conjunto com mais de uma classe (ou vazio) = inconclusivo. "
            "Em teoria, cada classe verdadeira é coberta em ≥ 1−α dos casos (frases trocáveis com as de calibração: aqui, notícias de jornal).\n",
            "| α | cobertura factual | cobertura citação | cobertura enviesada | decide (conjunto de 1 classe) | acerto quando decide | 'enviesada' decidida: precisão | 'enviesada' decidida: revocação |", "|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for alpha in (0.05, 0.10, 0.20):
        sets = np.zeros_like(P, dtype=bool)
        for k in folds:
            tr, te = fold != k, fold == k
            q = np.zeros(3)
            for c in range(3):
                s = np.sort(1 - P[tr & (y == c), c]); n = len(s)
                q[c] = s[min(n - 1, int(np.ceil((n + 1) * (1 - alpha))) - 1)]
            sets[te] = (1 - P[te]) <= q
        cov = [sets[y == c, c].mean() for c in range(3)]; size = sets.sum(1); dec = size == 1
        pred = sets.argmax(1); accd = (pred[dec] == y[dec]).mean()
        decE = dec & (pred == 2); precE = (y[decE] == 2).mean() if decE.any() else float("nan"); recE = (decE & (y == 2)).sum() / (y == 2).sum()
        out.append(f"| {alpha:.2f} | {cov[0]:.3f} | {cov[1]:.3f} | {cov[2]:.3f} | {dec.mean():.3f} | {accd:.3f} | {precE:.3f} | {recE:.3f} |")
    out.append("\nQuanto menor α, mais garantia e mais 'inconclusivo'. A garantia é por classe verdadeira e vale para textos parecidos com os de calibração.")

    # ---- 6) tipologia de erros
    txt = df["text"]; words = txt.str.split().str.len()
    feats = {"tem aspas": txt.str.contains(r'["“”]'), "sem aspas": ~txt.str.contains(r'["“”]'), "é manchete": df["is_title"], "não é manchete": ~df["is_title"],
             "frase curta (< 8 palavras)": words < 8, "frase média (8 a 20)": (words >= 8) & (words <= 20), "frase longa (21 a 40)": (words > 20) & (words <= 40), "frase muito longa (> 40)": words > 40,
             "termina em pergunta": txt.str.rstrip('"”').str.endswith("?"), "não termina em pontuação": ~txt.str.rstrip('"”)').str.contains(r"[.!?…]$"),
             "começa em minúscula": txt.str[:1].str.islower(), "tem dígito": txt.str.contains(r"\d")}
    for col in ("domain", "outlet", "year"):
        for v in sorted(df[col].astype(str).unique()):
            feats[f"{col} = {v}"] = df[col].astype(str) == v
    pred = P.argmax(1); err = pred != y; idx_by = {s: np.where(story == s)[0] for s in np.unique(story)}; base = err.mean(); stories = np.array(list(idx_by))
    out += [f"\n## 6) Onde o modelo erra mais (erro geral {base:.3f})\n", "| Grupo | n | taxa de erro [IC 95%] | diferença para o geral | enviesadas reais no grupo | F1 de 'enviesada' no grupo |", "|---|---:|---|---:|---:|---:|"]
    rows = []
    for name, m in feats.items():
        m = np.asarray(m, bool)
        if m.sum() < 60:
            continue
        bs = []
        for _ in range(500):
            sel = np.concatenate([idx_by[s] for s in rng.choice(stories, len(stories))]); mm = m[sel]
            bs.append(err[sel][mm].mean() if mm.any() else np.nan)
        lo, hi = np.nanpercentile(bs, [2.5, 97.5]); e = err[m].mean()
        f1e = 2 * ((pred[m] == 2) & (y[m] == 2)).sum() / max(1, (pred[m] == 2).sum() + (y[m] == 2).sum())
        rows.append((e - base, name, int(m.sum()), e, lo, hi, int((y[m] == 2).sum()), f1e))
    for dlt, name, n, e, lo, hi, ne, f1e in sorted(rows, reverse=True):
        flag = " **(pior que o geral)**" if lo > base else ""
        out.append(f"| {name}{flag} | {n} | {e:.3f} [{lo:.3f}; {hi:.3f}] | {dlt:+.3f} | {ne} | {f1e:.2f} |")
    out.append("\nGrupo 'pior que o geral' = limite inferior do IC acima do erro geral. Grupos com menos de 60 frases foram omitidos.")

    # ---- 8) anotadores
    a = load_annotators().set_index(load_annotators()["id"] - 1)
    a = a.loc[rid]; dis = (a["annotator1"].to_numpy() != a["annotator2"].to_numpy()); tot_dis = int((load_annotators()["annotator1"] != load_annotators()["annotator2"]).sum())
    out += ["\n## 8) Discordância entre anotadores (rótulos suaves)\n",
            f"Frases em que os dois anotadores discordam: **{tot_dis} de 6.191 ({tot_dis / 6191:.1%})** (no desenvolvimento: {int(dis.sum())}). "
            f"Acerto do modelo nessas frases: {(pred[dis] == y[dis]).mean() if dis.any() else float('nan'):.2f}; no restante: {(pred[~dis] == y[~dis]).mean():.3f}.\n",
            "Com ~1% de discordância, rótulos suaves (média dos anotadores) são praticamente iguais aos rótulos duros; **não há o que ganhar** treinando com eles. "
            "A subjetividade de 'enviesada' que o modelo enfrenta está no **critério** dos dois anotadores (que concordam entre si), não na discordância entre eles."]
    (REPORTS / "analises_oof_dev.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(out))


if __name__ == "__main__":
    main()
