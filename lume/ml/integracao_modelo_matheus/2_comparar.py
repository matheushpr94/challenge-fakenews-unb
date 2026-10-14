#!/usr/bin/env python3
"""Compara nos 1.650 pares v3: só a regra do app, o modelo do Matheus (versão reduzida) e os dois juntos.
Cortes e pesos são escolhidos SÓ em treino/validação. O teste é lido uma vez, no fim (registro em teste_usado.log).
Positivo = esperado mesmo_acontecimento ou divergente (as 'incerta' ficam de fora). Rode: ../factnews/.venv/bin/python 2_comparar.py"""
import json, math, re, unicodedata, datetime
from pathlib import Path
import numpy as np
from sklearn.linear_model import LogisticRegression

AQUI = Path(__file__).parent
pares = json.loads((AQUI.parent / "entrega_modelo" / "pares_lume_v3.json").read_text(encoding="utf-8"))
ent = {e["id"]: e for e in json.loads((AQUI / "entradas_calculadas.json").read_text())}
pares = [p for p in pares if p["esperado"] != "incerta"]

def palavras(s): return {w for w in re.findall(r"[a-z0-9]+", unicodedata.normalize("NFKD", s.lower()).encode("ascii", "ignore").decode()) if len(w) >= 3}
def jaccard(a, b):
    A, B = palavras(a), palavras(b); return len(A & B) / len(A | B) if A and B else 0.0
MESMO = {"mesmo_acontecimento", "detalhe_divergente"}
y = np.array([p["esperado"] in ("mesmo_acontecimento", "divergente") for p in pares])
hist = np.array([p["historia"] for p in pares]); div = np.array([p["divisao"] for p in pares])
cos = np.array([ent[p["id"]]["cosseno_ajustado"] for p in pares]); cos_old = np.array([ent[p["id"]]["cosseno_antigo"] for p in pares])
bert = np.array([ent[p["id"]]["nota_texto"] for p in pares]); sob = np.array([jaccard(p["afirmacao"], p["cand_titulo"]) for p in pares])
r_tit = np.array([p["regra_titulo"] in MESMO for p in pares]); r_tre = np.array([p["regra_trecho"] in MESMO for p in pares])
tr, va, te = div == "treino", div == "validacao", div == "teste"

# plano B do Matheus com os pesos e o corte DELE (nada ajustado por mim)
pb = json.loads(Path("/Users/aluno1/Downloads/lume-modelo-6b/3-regressao-logistica/decisor_plano_b.json").read_text())
def nota_plano_b(c, s):
    n = pb["numeros"]; z = pb["intercepto"] + n["cosseno"]["peso"]*(c-n["cosseno"]["media"])/n["cosseno"]["desvio"] + n["sobreposicao"]["peso"]*(s-n["sobreposicao"]["media"])/n["sobreposicao"]["desvio"]
    return 1/(1+np.exp(-z))
pl_b = nota_plano_b(cos, sob) >= pb["corte"]

def regressao(cols, nome):
    X = np.column_stack(cols); mu, sd = X[tr].mean(0), X[tr].std(0)+1e-9
    m = LogisticRegression(C=0.03, class_weight="balanced", max_iter=1000).fit((X[tr]-mu)/sd, y[tr])
    return m.predict_proba((X-mu)/sd)[:, 1]
nota_bert_cos = regressao([cos, sob, bert], "bert+cos+sob")   # "6B reduzido": as 3 entradas que dá para calcular fora do app
nota_cos = regressao([cos, sob], "cos+sob")

def corte_para_fp(nota, base, alvo_fp, mask):
    """Menor corte tal que o FP (de base OU nota>=corte) em `mask` seja <= alvo."""
    neg = mask & ~y
    for c in np.unique(np.concatenate([nota[neg], [1.0]]))[::-1][::-1]:
        pass
    cand = sorted(set(np.round(nota[mask], 6)))
    melhor = 1.01
    for c in cand:
        pred = base | (nota >= c)
        if (pred & neg).sum() / neg.sum() <= alvo_fp: melhor = c; break
    return melhor

def metr(pred, mask):
    pos, neg = mask & y, mask & ~y
    return (pred & pos).sum() / pos.sum(), (pred & neg).sum() / neg.sum(), (pred & mask & ~y).sum(), (pred & mask).sum()
def boot(pred, mask, n=2000, seed=1):
    rng = np.random.default_rng(seed); hs = np.array(sorted(set(hist[mask]))); idx = {h: np.flatnonzero(mask & (hist == h)) for h in hs}; r, f = [], []
    for _ in range(n):
        s = np.concatenate([idx[h] for h in rng.choice(hs, len(hs))]); pos, neg = y[s], ~y[s]
        if pos.sum() and neg.sum(): r.append((pred[s] & pos).sum()/pos.sum()); f.append((pred[s] & neg).sum()/neg.sum())
    return np.percentile(r, [2.5, 97.5]), np.percentile(f, [2.5, 97.5])

none = np.zeros(len(y), bool); dev = tr | va
# regra de referência: FP da regra (título) no desenvolvimento define o ponto de operação dos modelos
fp_regra_dev = (r_tit & dev & ~y).sum() / (dev & ~y).sum()
alvo = max(fp_regra_dev, 0.05)
c_bert = corte_para_fp(bert, none, alvo, dev); c_nota3 = corte_para_fp(nota_bert_cos, none, alvo, dev)
c_comb3 = corte_para_fp(nota_bert_cos, r_tit, alvo, dev); c_comb_cos = corte_para_fp(nota_cos, r_tit, alvo, dev)
metodos = {
  "A. só a regra (título)": r_tit,
  "A2. só a regra (trecho)": r_tre,
  "B. plano B dele (MPNet ajustado + sobreposição, corte dele)": pl_b,
  "C. BERTimbau dele sozinho (corte pelo FP da regra)": bert >= c_bert,
  "D. 6B reduzido (BERT+MPNet+sobreposição) sozinho": nota_bert_cos >= c_nota3,
  "E. regra + plano B (MPNet+sobreposição)": r_tit | (nota_cos >= c_comb_cos),
  "F. regra + 6B reduzido (juntos)": r_tit | (nota_bert_cos >= c_comb3),
}
def imprime(titulo, mask):
    print(f"\n== {titulo}: {mask.sum()} pares, {(mask&y).sum()} 'mesmo', {(mask&~y).sum()} outros ==")
    for nome, pred in metodos.items():
        r, f, fpn, npred = metr(pred, mask)
        extra = ""
        if titulo.startswith("TESTE"):
            (rl, rh), (fl, fh) = boot(pred, mask); extra = f"  IC95 recall {rl:.2f}-{rh:.2f} | FP {fl:.3f}-{fh:.3f}"
        print(f"{nome:62} recall {r:5.1%} | FP {f:5.1%} | precisão {(npred-fpn)/max(npred,1):5.1%}{extra}")
print(f"alvo de FP (dev): {alvo:.3f} | cortes: bert {c_bert:.3f} nota3 {c_nota3:.3f} comb3 {c_comb3:.3f} comb_cos {c_comb_cos:.3f}")
print("cosseno médio mesmo/outro — ajustado:", cos[y].mean().round(3), cos[~y].mean().round(3), "| antigo:", cos_old[y].mean().round(3), cos_old[~y].mean().round(3))
imprime("TREINO", tr); imprime("VALIDAÇÃO", va)
# teste: uma única leitura
(AQUI / "teste_usado.log").write_text(f"{datetime.datetime.now().isoformat()} teste lido uma vez, cortes fixados em treino/validação\n")
imprime("TESTE (lido uma vez)", te)
json.dump({"cortes": {"bert": float(c_bert), "nota3": float(c_nota3), "comb3": float(c_comb3), "comb_cos": float(c_comb_cos)}, "alvo_fp": float(alvo)}, open(AQUI / "cortes.json", "w"))
