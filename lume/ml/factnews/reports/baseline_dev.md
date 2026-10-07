# Baseline no desenvolvimento (teste lacrado)

4942 frases, 81 histórias, 4 folds.

| Modelo / validação | F1 macro | F1 factual | F1 citação | F1 enviesada |
|---|---:|---:|---:|---:|
| Dummy (classe mais frequente) | 0.272 | 0.817 | 0.000 | 0.000 |
| TF-IDF + LR, por história (correto) | 0.608 | 0.874 | 0.737 | 0.213 |
| TF-IDF + LR, ao acaso (inflado) | 0.687 | 0.889 | 0.806 | 0.367 |

F1 macro por fold (por história): 0.590, 0.584, 0.630, 0.625

Revocação por classe (por história): factual 0.909, citacao 0.713, enviesada 0.162
Precisão por classe (por história): factual 0.841, citacao 0.764, enviesada 0.312