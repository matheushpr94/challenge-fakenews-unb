"""Recall e falso positivo da REGRA do app nos pares v3, só no desenvolvimento (treino+validação). Uso: python3 6_avaliar_regra.py saida1.json [saida2.json]"""
import json, sys, collections
from pathlib import Path
A = Path(__file__).parent
pares = {p["id"]: p for p in json.loads((A.parent/"entrega_modelo"/"pares_lume_v3.json").read_text())}
SIM = {"mesmo_acontecimento", "detalhe_divergente"}
for f in sys.argv[1:]:
    out = {o["id"].removeprefix("par3-"): o for o in json.load(open(f))}
    pos = neg = tp = fp = 0; por_caminho = collections.Counter()
    for i, p in pares.items():
        if p["divisao"] == "teste" or p["esperado"] == "incerta" or i not in out: continue
        sim = out[i]["pred"] in SIM; real = p["esperado"] in ("mesmo_acontecimento", "divergente")
        pos += real; neg += not real; tp += sim and real; fp += sim and not real
        if real and not sim: por_caminho["perdido via " + out[i]["path"]] += 1
    print(f"{Path(f).name}: recall {tp}/{pos} = {tp/pos:.1%} | falso positivo {fp}/{neg} = {fp/neg:.1%} | precisão {tp/max(tp+fp,1):.1%}")
    print("   perdidos por caminho:", dict(por_caminho.most_common(4)))
