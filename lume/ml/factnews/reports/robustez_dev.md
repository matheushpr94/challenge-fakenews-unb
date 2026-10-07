# Robustez a outro jornal, outra época e outro assunto (só desenvolvimento)

Modelo treinado SEM o grupo e medido NO grupo, contra o modelo da validação por história nas mesmas frases (semente 42). Δ = de fora − base, com IC 95% por bootstrap de histórias. Treino menor explica parte da queda.

| Experimento | treino | teste (frases / histórias / enviesadas) | F1 macro de fora | F1 macro base | Δ macro [IC 95%] | F1 enviesada de fora | F1 enviesada base | Δ enviesada [IC 95%] |
|---|---:|---|---:|---:|---|---:|---:|---|
| sem o jornal Estadão | 3383 | 1559 / 81 / 141 | 0.815 | 0.808 | +0.008 [-0.008; +0.026] | 0.580 | 0.568 | +0.012 [-0.028; +0.061] |
| sem o jornal Folha | 3136 | 1806 / 81 / 162 | 0.814 | 0.822 | -0.008 [-0.025; +0.013] | 0.575 | 0.598 | -0.023 [-0.072; +0.034] |
| sem o jornal O Globo | 3365 | 1577 / 81 / 118 | 0.798 | 0.793 | +0.005 [-0.013; +0.026] | 0.538 | 0.525 | +0.012 [-0.035; +0.068] |
| passado -> presente (treino 2006 a 2008; teste 2021 e 2022) | 2248 | 2694 / 39 / 216 | 0.786 | 0.798 | -0.013 [-0.035; +0.010] | 0.525 | 0.549 | -0.024 [-0.088; +0.040] |
| presente -> passado (treino 2021 e 2022; teste 2006 a 2008) | 2694 | 2248 / 42 / 205 | 0.806 | 0.822 | -0.016 [-0.054; +0.004] | 0.539 | 0.589 | -0.050 [-0.158; +0.005] |
| sem a editoria sports | 4579 | 363 / 7 / 96 | 0.773 | 0.860 | -0.087 [-0.134; +0.011] | 0.601 | 0.779 | -0.178 [-0.259; +0.019] |
| sem a editoria politics | 1591 | 3351 / 52 / 255 | 0.663 | 0.794 | -0.131 [-0.168; -0.092] | 0.150 | 0.523 | -0.373 [-0.471; -0.267] |
| sem a editoria world | 4309 | 633 / 12 / 47 | 0.740 | 0.783 | -0.043 [-0.127; +0.001] | 0.386 | 0.479 | -0.094 [-0.337; +0.033] |
| sem a editoria daily | 4583 | 359 / 7 / 7 | 0.728 | 0.668 | +0.060 [+0.007; +0.122] | 0.286 | 0.154 | +0.132 [+0.000; +0.333] |
