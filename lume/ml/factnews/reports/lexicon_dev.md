# Léxico como segunda opinião (desenvolvimento, 4942 frases, 81 histórias; modelo = média de 5 sementes)

Léxico: 23236 palavras (OpLexicon v3 adj/verbo/subst. com polaridade ±1 + SentiLex-PT02, >= 4 letras). Frases com ao menos 1 palavra do léxico: 64% (entre as enviesadas reais: 76%; entre as demais: 63%).

Linha de base (destaque do modelo, confiança >= 0,80): precisão 0.725, cobertura 0.363.

| Regra | precisão | cobertura | Δ precisão [IC 95%] | Δ cobertura [IC 95%] | critério |
|---|---:|---:|---|---|---|
| modelo diz enviesada e a frase tem ≥ 1 palavra(s) do léxico | 0.713 | 0.278 | -0.012 [-0.044; +0.021] | -0.086 [-0.112; -0.059] | não |
| modelo diz enviesada e a frase tem ≥ 2 palavra(s) do léxico | 0.701 | 0.178 | -0.024 [-0.083; +0.029] | -0.185 [-0.225; -0.139] | não |
| modelo diz enviesada e a frase tem ≥ 3 palavra(s) do léxico | 0.648 | 0.083 | -0.077 [-0.182; +0.040] | -0.280 [-0.345; -0.198] | não |

Com a regra: o léxico só pode tirar destaques; nunca cria novos. Resultado e decisão estão em DOCUMENTACAO.md.
