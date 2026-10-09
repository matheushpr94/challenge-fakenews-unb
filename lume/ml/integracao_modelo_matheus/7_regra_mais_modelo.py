"""Regra do app OU modelo com nota alta confirmando 'mesmo fato'. Só desenvolvimento (treino+validação). Uso: python3 7_regra_mais_modelo.py saida_harness.json"""
import json, math, re, sys, unicodedata
from pathlib import Path
A = Path(__file__).parent
pares = {p["id"]: p for p in json.loads((A.parent/"entrega_modelo"/"pares_lume_v3.json").read_text())}
ent = {e["id"]: e for e in json.loads((A/"entradas_calculadas.json").read_text())}
w = json.loads((A/"pesos_app.json").read_text())
def words(s): return {x for x in re.findall(r"[a-z0-9]+", unicodedata.normalize("NFKD", s.lower()).encode("ascii","ignore").decode()) if len(x) >= 3}
def sob(a, b):
    x, y = words(a), words(b); return len(x & y)/len(x | y) if x and y else 0.0
def nota(p):
    e = ent[p["id"]]; v = [e["cosseno_ajustado"], sob(p["afirmacao"], p["cand_titulo"]), e["nota_texto"]]
    z = w["intercepto"] + sum(pw*(x-m)/d for pw, x, m, d in zip(w["pesos"], v, w["medias"], w["desvios"])); return 1/(1+math.exp(-z))
out = {o["id"].removeprefix("par3-"): o for o in json.load(open(sys.argv[1]))}
SIM = {"mesmo_acontecimento", "detalhe_divergente"}
rows = []
for i, p in pares.items():
    if p["divisao"] == "teste" or p["esperado"] == "incerta" or i not in out: continue
    rows.append((out[i]["pred"], out[i]["path"], nota(p), p["esperado"] in ("mesmo_acontecimento", "divergente"), p["divisao"]))
def report(name, sel, rows=rows):
    pos = sum(r[3] for r in rows); neg = len(rows) - pos
    tp = sum(1 for r in rows if sel(r) and r[3]); fp = sum(1 for r in rows if sel(r) and not r[3])
    print(f"{name:52} recall {tp/pos:6.1%} | FP {fp/neg:5.1%} ({fp}) | precisão {tp/max(tp+fp,1):6.1%}")
rule = lambda r: r[0] in SIM
report("só a regra", rule)
for t in (0.86, 0.90, 0.92, 0.95):
    report(f"regra OU modelo >= {t:.2f} (exceto não independente)", lambda r, t=t: rule(r) or (r[2] >= t and r[0] != "nao_independente"))
for t in (0.90, 0.92):
    report(f"regra OU modelo >= {t:.2f}, só onde a regra não reconheceu ação", lambda r, t=t: rule(r) or (r[2] >= t and r[0] != "nao_independente" and r[1] != "comparacao_de_acontecimento"))
print("\nsó validação (não usada para ajustar a regressão):")
val = [r for r in rows if r[4] == "validacao"]
report("só a regra", rule, val)
for t in (0.90, 0.92): report(f"regra OU modelo >= {t:.2f}", lambda r, t=t: rule(r) or (r[2] >= t and r[0] != "nao_independente"), val)
print("\ncorte diferente quando a regra reconheceu a ação (comparação de acontecimento):")
for ts, tc in ((0.90, 0.93), (0.90, 0.95), (0.88, 0.93)):
    sel = lambda r, ts=ts, tc=tc: rule(r) or (r[0] != "nao_independente" and r[2] >= (tc if r[1] == "comparacao_de_acontecimento" else ts))
    report(f"sem ação >= {ts:.2f}, com ação >= {tc:.2f}", sel); report("   (só validação)", sel, val)
