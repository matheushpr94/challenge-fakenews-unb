# Concordância dos rótulos do Claude com o gabarito humano (120 frases de desenvolvimento, às cegas)

Acordo simples 0.65; kappa de Cohen **0.47**; F1 macro 0.62.

| Classe | precisão do Claude | revocação do Claude | F1 |
|---|---:|---:|---:|
| factual | 0.51 | 0.78 | 0.61 |
| citacao | 0.79 | 0.93 | 0.85 |
| enviesada | 0.83 | 0.25 | 0.38 |

Matriz (linhas = gabarito humano; colunas = Claude): factual / citacao / enviesada
```
[31, 8, 1]
[2, 37, 1]
[28, 2, 10]
```

Critério (kappa >= 0,60 e enviesada com precisão e revocação >= 0,50): **NÃO atendido**. Com 40 frases por classe, cada taxa tem incerteza de cerca de ±0,15.
