# Análises sobre as previsões fora-da-dobra (desenvolvimento, 4942 frases, 81 histórias; semente 42)

## 4) Calibração (escala de temperatura ajustada nos outros folds)

Temperaturas por fold: {1: 1.45, 2: 1.2999999999999998, 3: 1.4, 4: 1.4} (T > 1 = o modelo era confiante demais).

| Medida | antes | depois |
|---|---:|---:|
| ECE da classe prevista (15 faixas) | 0.054 | 0.021 |
| ECE da probabilidade de 'enviesada' | 0.039* | 0.020* |
| NLL (perda logarítmica) | 0.322 | 0.294 |
| Acurácia (não muda: a escala preserva a ordem) | 0.905 | 0.905 |

*ECE tratando P(enviesada) como confiança de um evento binário (faixas de P, não da classe prevista).

Confiança da classe prevista versus acerto (antes → depois):

| faixa de confiança | n | acerto | confiança média (antes) | confiança média (depois) |
|---|---:|---:|---:|---:|
| 0.00–0.60 | 93 | 0.559 | 0.540 | 0.510 |
| 0.60–0.80 | 215 | 0.540 | 0.706 | 0.637 |
| 0.80–0.90 | 210 | 0.633 | 0.857 | 0.767 |
| 0.90–0.97 | 535 | 0.806 | 0.946 | 0.874 |
| 0.97–1.00 | 3889 | 0.962 | 0.988 | 0.955 |

## 5) 'Inconclusivo' com garantia (predição conforme por classe)

Para cada nível α, o modelo devolve um CONJUNTO de classes; conjunto com mais de uma classe (ou vazio) = inconclusivo. Em teoria, cada classe verdadeira é coberta em ≥ 1−α dos casos (frases trocáveis com as de calibração: aqui, notícias de jornal).

| α | cobertura factual | cobertura citação | cobertura enviesada | decide (conjunto de 1 classe) | acerto quando decide | 'enviesada' decidida: precisão | 'enviesada' decidida: revocação |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 0.05 | 0.949 | 0.952 | 0.943 | 0.540 | 0.936 | 0.635 | 0.475 |
| 0.10 | 0.899 | 0.899 | 0.893 | 0.764 | 0.879 | 0.444 | 0.686 |
| 0.20 | 0.784 | 0.801 | 0.784 | 0.918 | 0.842 | 0.357 | 0.777 |

Quanto menor α, mais garantia e mais 'inconclusivo'. A garantia é por classe verdadeira e vale para textos parecidos com os de calibração.

## 6) Onde o modelo erra mais (erro geral 0.095)

| Grupo | n | taxa de erro [IC 95%] | diferença para o geral | enviesadas reais no grupo | F1 de 'enviesada' no grupo |
|---|---:|---|---:|---:|---:|
| começa em minúscula | 86 | 0.128 [0.064; 0.209] | +0.033 | 4 | 0.00 |
| domain = sports | 363 | 0.127 [0.046; 0.191] | +0.032 | 96 | 0.78 |
| frase curta (< 8 palavras) | 475 | 0.114 [0.066; 0.171] | +0.019 | 24 | 0.38 |
| é manchete | 525 | 0.112 [0.080; 0.144] | +0.017 | 47 | 0.47 |
| domain = science | 75 | 0.107 [0.027; 0.184] | +0.012 | 7 | 0.00 |
| year = 2021 | 179 | 0.106 [0.020; 0.184] | +0.011 | 15 | 0.38 |
| frase muito longa (> 40) | 282 | 0.103 [0.072; 0.135] | +0.008 | 32 | 0.63 |
| sem aspas | 4038 | 0.101 [0.083; 0.118] | +0.006 | 396 | 0.58 |
| year = 2022 | 2515 | 0.101 [0.080; 0.123] | +0.005 | 201 | 0.56 |
| domain = world | 633 | 0.100 [0.070; 0.126] | +0.004 | 47 | 0.48 |
| domain = culture | 161 | 0.099 [0.071; 0.121] | +0.004 | 9 | 0.53 |
| frase longa (21 a 40) | 1690 | 0.098 [0.082; 0.113] | +0.003 | 160 | 0.58 |
| outlet = Estadão | 1559 | 0.097 [0.079; 0.117] | +0.002 | 141 | 0.57 |
| outlet = O Globo | 1577 | 0.096 [0.074; 0.122] | +0.001 | 118 | 0.53 |
| domain = politics | 3351 | 0.095 [0.080; 0.114] | +0.000 | 255 | 0.52 |
| não é manchete | 4417 | 0.093 [0.078; 0.109] | -0.002 | 374 | 0.58 |
| year = 2006 | 444 | 0.092 [0.030; 0.157] | -0.003 | 45 | 0.54 |
| outlet = Folha | 1806 | 0.092 [0.074; 0.109] | -0.003 | 162 | 0.60 |
| frase média (8 a 20) | 2495 | 0.089 [0.071; 0.109] | -0.006 | 205 | 0.57 |
| year = 2007 | 1765 | 0.087 [0.066; 0.113] | -0.008 | 157 | 0.60 |
| tem aspas | 904 | 0.070 [0.052; 0.088] | -0.025 | 25 | 0.42 |
| tem dígito | 1614 | 0.063 [0.046; 0.084] | -0.032 | 95 | 0.52 |
| não termina em pontuação | 379 | 0.061 [0.039; 0.088] | -0.034 | 31 | 0.71 |
| domain = daily | 359 | 0.047 [0.019; 0.072] | -0.048 | 7 | 0.15 |

Grupo 'pior que o geral' = limite inferior do IC acima do erro geral. Grupos com menos de 60 frases foram omitidos.

## 8) Discordância entre anotadores (rótulos suaves)

Frases em que os dois anotadores discordam: **53 de 6.191 (0.9%)** (no desenvolvimento: 38). Acerto do modelo nessas frases: 0.53; no restante: 0.908.

Com ~1% de discordância, rótulos suaves (média dos anotadores) são praticamente iguais aos rótulos duros; **não há o que ganhar** treinando com eles. A subjetividade de 'enviesada' que o modelo enfrenta está no **critério** dos dois anotadores (que concordam entre si), não na discordância entre eles.
