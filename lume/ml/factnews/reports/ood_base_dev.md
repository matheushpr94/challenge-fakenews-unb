# Detector sem ajuste fino (BERTimbau base), 4 folds por história

| Variante | gênero | marcados como estranhos (limiar = p95 das notícias de fora) | AUC contra notícia do FactNews |
|---|---|---:|---:|
| A: última camada, Mahalanobis | enciclopedia | 7% | 0.53 |
| A: última camada, Mahalanobis | revista_analise | 4% | 0.47 |
| A: última camada, Mahalanobis | noticia_controle | 0% | 0.46 |
| B: camada 8, Mahalanobis | enciclopedia | 6% | 0.51 |
| B: camada 8, Mahalanobis | revista_analise | 1% | 0.51 |
| B: camada 8, Mahalanobis | noticia_controle | 0% | 0.49 |
| C: última camada, 10 vizinhos | enciclopedia | 13% | 0.52 |
| C: última camada, 10 vizinhos | revista_analise | 48% | 0.89 |
| C: última camada, 10 vizinhos | noticia_controle | 31% | 0.69 |

Controle: `noticia_controle` é notícia (esperado ~5% e AUC ~0,5). Critério: AUC >= 0,85 em `revista_analise`.
