# Detector de texto fora do que o modelo conhece (desenvolvimento, 4 folds por história)

Artigos de notícia de fora do fold: 235; escore mediano 0.91; limiar (percentil 95): **1.27**. Escore ≈ 1 = típico do treino; maior = mais estranho.

| Gênero externo | docs (20 frases) | escore mediano | marcados como estranhos | AUC contra notícia do FactNews |
|---|---:|---:|---:|---:|
| enciclopedia | 111 | 0.95 | 6% | 0.54 |
| revista_analise | 174 | 1.11 | 13% | 0.77 |
| noticia_controle | 13 | 0.93 | 0% | 0.46 |

`noticia_controle` (agência pública) é NOTÍCIA: o esperado é ficar perto dos 5% do limiar e AUC perto de 0,5. `enciclopedia` e `revista_analise` devem ser marcados.
