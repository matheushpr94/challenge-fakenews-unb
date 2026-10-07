# Comparação no desenvolvimento (4 folds por história, 4942 frases, 81 histórias)

Sementes comuns: [7, 42, 99, 123, 2024]. Nada aqui lê o teste lacrado.

| Variante | F1 macro por semente (média ± dp) | enviesada F1 (média ± dp) | **Conjunto** F1 macro | factual | citação | enviesada | prec. env. | rev. env. |
|---|---|---|---:|---:|---:|---:|---:|---:|
| none | 0.8061 ± 0.0043 | 0.559 ± 0.013 | **0.8063** | 0.939 | 0.924 | 0.556 | 0.620 | 0.504 |
| headline | 0.7939 ± 0.0086 | 0.537 ± 0.019 | **0.8017** | 0.934 | 0.918 | 0.553 | 0.621 | 0.499 |

## Comparações pareadas (diferença em F1 macro; IC 95% por bootstrap de histórias)

| Comparação | diferença | IC 95% | exclui zero? |
|---|---:|---|---|
| 1. contexto (conjunto com manchete − sem) | -0.0046 | [-0.0148, +0.0056] | não |
| 1b. contexto, semente única (média) | -0.0122 | [-0.0220, -0.0033] | SIM |
| 2. conjunto de 5 sementes − semente única (none) | +0.0002 | [-0.0054, +0.0049] | não |
| 3. limiar de 'enviesada' (validado entre folds) − sem limiar (none, semente única) | +0.0054 | [-0.0020, +0.0129] | não |

Multiplicador escolhido em cada fold (tuned nos outros 3): {1: 3.0, 2: 2.5, 3: 2.5, 4: 2.5}. Escolhido em todo o desenvolvimento: 2.5.
Com limiar validado entre folds: F1 macro 0.8145 (enviesada 0.588); sem: 0.8090 (enviesada 0.568).

F1 macro por fold da configuração escolhida (sem limiar): {1: 0.8221, 2: 0.7766, 3: 0.8123, 4: 0.8191}

## Decisão pela regra fixada
- contexto (manchete): não adotar (diferença dentro do ruído)
- conjunto de sementes: não adotar (diferença dentro do ruído)
- limiar de 'enviesada': não adotar (diferença dentro do ruído)
