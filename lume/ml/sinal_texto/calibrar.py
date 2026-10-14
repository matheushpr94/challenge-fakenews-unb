#!/usr/bin/env python3
"""Define as faixas do 'sinal do texto' a partir de notícias reais coletadas (coletar_noticias.py) e dos conjuntos rotulados.
Regra: poucas notícias reais marcadas como 'desinformação' (alvo <= 3% em textos longos), e 'notícia comum' só quando
quase nenhum texto de boato/checagem chega lá. Textos curtos têm mais ruído e usam cortes mais conservadores."""
import json, sys, datetime, numpy as np, pandas as pd, math
sys.path.insert(0, ".")
from sinal import Sinal
s = Sinal()
real = [json.loads(l) for l in open(sys.argv[1], encoding="utf-8")]
MIN_PALAVRAS, LIMITE_CURTO = 60, 150
CORTES = {"curto": {"desinformacao": -1.00, "noticia": 0.30}, "normal": {"desinformacao": -0.75, "noticia": 0.30}}
def regime(n): return "curto" if n < LIMITE_CURTO else "normal"
def faixa(d, n):
    c = CORTES[regime(n)]
    return "desinformacao" if d < c["desinformacao"] else "noticia" if d >= c["noticia"] else "sem_sinal"
def wilson(k, n, z=1.96):
    if n == 0: return (0, 0)
    p = k/n; den = 1 + z*z/n; c = (p + z*z/(2*n))/den; h = z*math.sqrt(p*(1-p)/n + z*z/(4*n*n))/den
    return (round(max(0, c-h), 3), round(min(1, c+h), 3))
res = {}
# notícias reais, texto cheio e cortado em N palavras
for nome, N in [("completo", None), ("100", 100), ("60", 60)]:
    fx = []
    for t in real:
        palavras = t["texto"].split(); x = palavras if N is None else palavras[:N]
        if len(x) < MIN_PALAVRAS: continue
        fx.append(faixa(s.pontuar(" ".join(x))["decisao"], len(x)))
    n = len(fx); k = fx.count("desinformacao")
    res[f"reais_{nome}"] = {"n": n, "desinformacao": k, "taxa": round(k/n, 3), "ic95": wilson(k, n),
                            "noticia": round(fx.count("noticia")/n, 3), "sem_sinal": round(fx.count("sem_sinal")/n, 3)}
# boatos (FakeTrueBr fake) e textos de checagem; e FakeRecogna lematizado (regime normal)
ft = pd.read_csv("/Users/aluno1/Downloads/FakeTrueBr_corpus.csv")
fake = ft["fake"].dropna().astype(str).sample(400, random_state=5).tolist()
fx = [faixa(s.pontuar(t)["decisao"], len(t.split())) for t in fake if len(t.split()) >= MIN_PALAVRAS]
res["boatos_faketruebr"] = {"n": len(fx), "marcados": round(fx.count("desinformacao")/len(fx), 3), "como_noticia": round(fx.count("noticia")/len(fx), 3)}
fr = pd.read_csv("/Users/aluno1/Downloads/FakeRecogna.csv").dropna(subset=["Noticia", "Classe"])
for c, nome in ((0, "fakerecogna_classe0"), (1, "fakerecogna_classe1")):
    xs = [s.decisao(t)[0] for t in fr[fr.Classe == c].Noticia.astype(str).sample(800, random_state=9)]
    fx = [faixa(d, 400) for d in xs]
    res[nome] = {"n": len(fx), "desinformacao": round(fx.count("desinformacao")/len(fx), 3), "noticia": round(fx.count("noticia")/len(fx), 3)}
out = {"versao": 1, "data": datetime.date.today().isoformat(), "min_palavras": MIN_PALAVRAS, "limite_curto": LIMITE_CURTO,
       "cortes": CORTES, "posicao": {"minimo": -1.5, "maximo": 1.5},
       "calibracao": {"fonte": "notícias reais coletadas no dia (Bing Notícias RSS + página), FakeTrueBr e FakeRecogna",
                      "n_reais": len(real), "dominios": len({t["dominio"] for t in real}), **res},
       "aviso": "Mede o estilo do texto (treinado em FakeRecogna: boatos/checagens x notícia comum). Não verifica fatos."}
json.dump(out, open("faixas.json", "w"), ensure_ascii=False, indent=1)
print(json.dumps(out["calibracao"], ensure_ascii=False, indent=1))
