"""Escolhe (só no desenvolvimento: treino+validação; o teste não é lido) o corte de promoção e o limite do aviso 'confira'."""
import json, math, re, unicodedata
from pathlib import Path
A = Path(__file__).parent
pares = [p for p in json.loads((A.parent/"entrega_modelo"/"pares_lume_v3.json").read_text()) if p["divisao"] != "teste" and p["esperado"] != "incerta"]
ent = {e["id"]: e for e in json.loads((A/"entradas_calculadas.json").read_text())}
w = json.loads((A/"pesos_app.json").read_text())
def words(s): return {x for x in re.findall(r"[a-z0-9]+", unicodedata.normalize("NFKD", s.lower()).encode("ascii","ignore").decode()) if len(x) >= 3}
def sob(a, b):
    x, y = words(a), words(b); return len(x & y)/len(x | y) if x and y else 0.0
def nota(p):
    e = ent[p["id"]]; v = [e["cosseno_ajustado"], sob(p["afirmacao"], p["cand_titulo"]), e["nota_texto"]]
    z = w["intercepto"] + sum(pw*(x-m)/d for pw, x, m, d in zip(w["pesos"], v, w["medias"], w["desvios"])); return 1/(1+math.exp(-z))
MESMO = {"mesmo_acontecimento", "detalhe_divergente"}
rows = [(nota(p), p["regra_trecho"], p["esperado"] in ("mesmo_acontecimento", "divergente")) for p in pares]
print(f"desenvolvimento: {len(rows)} pares")
print("\n1) PROMOÇÃO: a regra não disse mesmo (contexto/outro/incerto); quantas promovidas são de fato o mesmo acontecimento")
cand = [r for r in rows if r[1] in ("contexto_relacionado", "outro_acontecimento", "incerto")]
for c in [0.78, 0.82, 0.86, 0.88, 0.90, 0.92, 0.95]:
    pr = [r for r in cand if r[0] >= c]; ok = sum(r[2] for r in pr)
    print(f"  corte {c:.2f}: promove {len(pr):4d} | certas {ok:4d} ({ok/max(len(pr),1):.0%}) | erradas {len(pr)-ok:3d} | mesmo-fato que a regra perdeu e foram recuperados: {ok} de {sum(r[2] for r in cand)}")
for tipo in ("contexto_relacionado", "outro_acontecimento", "incerto"):
    sub = [r for r in cand if r[1] == tipo]
    print("   por tipo da regra:", tipo, " ".join(f"{c:.2f}:{sum(r[2] for r in sub if r[0]>=c)}/{sum(1 for r in sub if r[0]>=c)}" for c in (0.78, 0.90)))
print("\n2) AVISO: a regra disse mesmo; quando a nota é baixa, quantas vezes a regra estava errada")
conf = [r for r in rows if r[1] in MESMO]
base = sum(not r[2] for r in conf)
print(f"  regra disse mesmo em {len(conf)} pares; erradas no total: {base} ({base/len(conf):.0%})")
for lim in [0.10, 0.20, 0.30, 0.40, 0.50]:
    av = [r for r in conf if r[0] < lim]; bad = sum(not r[2] for r in av)
    print(f"  nota < {lim:.2f}: avisa em {len(av):3d} | regra errada em {bad:3d} ({bad/max(len(av),1):.0%}) | erros da regra cobertos: {bad} de {base}")
