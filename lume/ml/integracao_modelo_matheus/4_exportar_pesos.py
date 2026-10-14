"""Exporta a regressão (cosseno ajustado, sobreposição, nota do BERTimbau) para o app, com casos de conferência."""
exec(open("2_comparar.py").read().split("def regressao")[0])
X = np.column_stack([cos, sob, bert]); mu, sd = X[tr].mean(0), X[tr].std(0)+1e-9
m = LogisticRegression(C=0.03, class_weight="balanced", max_iter=1000).fit((X[tr]-mu)/sd, y[tr])
corte = json.load(open("cortes.json"))["cortes"]["nota3"]
nota = m.predict_proba((X-mu)/sd)[:, 1]
casos = [{"afirmacao": pares[i]["afirmacao"], "titulo": pares[i]["cand_titulo"], "cosseno": float(cos[i]), "nota_texto": float(bert[i]),
          "sobreposicao": float(sob[i]), "nota": float(nota[i])} for i in range(0, len(pares), 160)][:10]
out = {"medias": mu.tolist(), "desvios": sd.tolist(), "pesos": m.coef_[0].tolist(), "intercepto": float(m.intercept_[0]), "corte": float(corte),
       "entradas": ["cosseno(lume-mpnet-ajustado)", "sobreposicao(jaccard, palavras>=3)", "nota_texto(BERTimbau ajustado)"], "casos": casos}
json.dump(out, open("pesos_app.json", "w"), ensure_ascii=False, indent=1); print(json.dumps({k: v for k, v in out.items() if k != "casos"}, indent=1))
