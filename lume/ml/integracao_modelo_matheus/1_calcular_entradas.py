#!/usr/bin/env python3
"""Calcula, para os 1.650 pares v3, as entradas do modelo do Matheus que dá para calcular fora do app:
nota_texto (BERTimbau ajustado), cosseno (MPNet ajustado e o paraphrase-multilingual atual, via Ollama) e sobreposição.
Não lê métricas do teste. Rode com: ../factnews/.venv/bin/python 1_calcular_entradas.py"""
import json, math, re, sys, unicodedata, urllib.request
from pathlib import Path
import torch
from transformers import AutoTokenizer, AutoModelForSequenceClassification

AQUI = Path(__file__).parent
PARES = AQUI.parent / "entrega_modelo" / "pares_lume_v3.json"
PASTA6B = Path("/Users/aluno1/Downloads/lume-modelo-6b")
pares = json.loads(PARES.read_text(encoding="utf-8"))

def embed(modelo, textos, lote=32):
    out = []
    for i in range(0, len(textos), lote):
        corpo = json.dumps({"model": modelo, "input": [t[:500] for t in textos[i:i+lote]]}).encode()
        r = urllib.request.urlopen(urllib.request.Request("http://127.0.0.1:11434/api/embed", corpo, {"Content-Type": "application/json"}), timeout=300)
        out += json.loads(r.read())["embeddings"]
    return out

def cos(a, b):
    na = math.sqrt(sum(x*x for x in a)); nb = math.sqrt(sum(x*x for x in b))
    return sum(x*y for x, y in zip(a, b)) / (na*nb) if na and nb else 0.0

# 1. BERTimbau ajustado (título cortado em 110, como no treino dele)
tok = AutoTokenizer.from_pretrained(PASTA6B / "1-bertimbau-ajustado")
bert = AutoModelForSequenceClassification.from_pretrained(PASTA6B / "1-bertimbau-ajustado").eval()
notas = []
for i in range(0, len(pares), 32):
    lote = pares[i:i+32]
    enc = tok([p["afirmacao"] for p in lote], [p["cand_titulo"][:110] for p in lote], truncation=True, max_length=128, padding=True, return_tensors="pt")
    with torch.no_grad():
        notas += torch.sigmoid(bert(**enc).logits.squeeze(-1)).tolist()
    print("bert", i, file=sys.stderr)
# conferência com o exemplo do LEIA-ME (esperado ~0,9959)
enc = tok(["SUS recebe 1 milhao de doses da vacina contra a dengue"], ["Ministerio confirma chegada de 1 milhao de doses ao SUS"[:110]], truncation=True, max_length=128, padding=True, return_tensors="pt")
with torch.no_grad(): print("conferência LEIA-ME (esperado ~0,9959):", float(torch.sigmoid(bert(**enc).logits.squeeze(-1))), file=sys.stderr)

# 2. cossenos pelo Ollama
res = {}
for nome in ["lume-mpnet-ajustado", "paraphrase-multilingual"]:
    va = embed(nome, [p["afirmacao"] for p in pares]); vt = embed(nome, [p["cand_titulo"] for p in pares])
    res[nome] = [cos(a, t) for a, t in zip(va, vt)]
    print("ollama", nome, file=sys.stderr)

saida = [{"id": p["id"], "nota_texto": n, "cosseno_ajustado": c1, "cosseno_antigo": c2}
         for p, n, c1, c2 in zip(pares, notas, res["lume-mpnet-ajustado"], res["paraphrase-multilingual"])]
(AQUI / "entradas_calculadas.json").write_text(json.dumps(saida), encoding="utf-8")
print("ok", len(saida))
