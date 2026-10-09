"""Exploração SÓ no desenvolvimento (treino+validação): o teste não é lido aqui."""
exec(open("2_comparar.py").read().split("none = np.zeros")[0])
dev = tr | va; modelo = nota_bert_cos
c = corte_para_fp(modelo, none if False else np.zeros(len(y), bool), 0.05, dev)
m = modelo >= c
print("dev: regra acha", (r_tit&dev&y).sum(), "de", (dev&y).sum(), "| modelo (FP 5%) acha", (m&dev&y).sum())
print("positivos que SÓ a regra acha:", (r_tit&~m&dev&y).sum(), "| SÓ o modelo:", (m&~r_tit&dev&y).sum(), "| ambos:", (m&r_tit&dev&y).sum())
print("FPs da regra:", (r_tit&dev&~y).sum(), "| desses o modelo também aceita:", (r_tit&m&dev&~y).sum(), "| FPs do modelo:", (m&dev&~y).sum())
for nome, pred in {"regra": r_tit, "modelo": m, "união": r_tit|m, "regra E modelo (veto)": r_tit&m,
                   "modelo, mas regra 'outro' vence": m & ~np.array([p['regra_titulo']=='outro_acontecimento' for p in pares])}.items():
    r, f, fpn, n = metr(pred, dev); print(f"{nome:36} recall {r:5.1%} FP {f:5.1%} precisão {(n-fpn)/max(n,1):5.1%}")
