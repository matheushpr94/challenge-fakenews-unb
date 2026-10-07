# Auditoria de atalhos (desenvolvimento, 4 folds por história)

## Perturbar o texto de teste (modelo treinado normalmente)

| Variante do texto de teste | F1 macro | F1 enviesada | queda no F1 macro |
|---|---:|---:|---:|
| original | 0.808 | 0.567 | +0.000 |
| sem aspas | 0.774 | 0.558 | -0.034 |
| tudo em minúsculas | 0.779 | 0.485 | -0.030 |
| palavras embaralhadas | 0.607 | 0.133 | -0.202 |
| só as 8 primeiras palavras | 0.699 | 0.336 | -0.110 |
| só as 8 últimas palavras | 0.650 | 0.284 | -0.158 |

## Linhas de base SEM ler o texto (regressão logística, validação por história)

| Atributos usados | F1 macro | F1 enviesada |
|---|---:|---:|
| editoria + veículo + ano + é manchete | 0.333 | 0.168 |
| tamanho da frase + tem aspas | 0.458 | 0.177 |

Leitura: queda grande ao embaralhar as palavras = o modelo usa o conteúdo/ordem das palavras (bom); queda pequena = usa pistas de bolsa de palavras ou de forma. Sem ler o texto: editoria, veículo, ano e manchete dão F1 macro 0,33 (acaso ≈ 0,27); tamanho da frase e aspas dão 0,46. Há pistas de forma, mas explicam pouco do 0,81 do modelo.
