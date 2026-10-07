# Calibração da confiança (desenvolvimento, fora-da-dobra, 5 sementes)

4942 frases (525 manchetes). Não lê o teste lacrado. Cada célula: média entre sementes [mín–máx]; n = frases acima do limite (média).


## Todas as frases

| Classe prevista | limite | n | precisão (acerto) | cobertura (das reais da classe) |
|---|---:|---:|---:|---:|
| enviesada | 0.50 | 350 | 0.615 [0.608–0.630] | 0.511 |
| enviesada | 0.60 | 317 | 0.632 [0.620–0.651] | 0.476 |
| enviesada | 0.70 | 288 | 0.650 [0.633–0.668] | 0.445 |
| enviesada | 0.80 | 244 | 0.679 [0.661–0.701] | 0.393 |
| enviesada | 0.85 | 218 | 0.698 [0.671–0.717] | 0.362 |
| enviesada | 0.90 | 182 | 0.722 [0.703–0.737] | 0.313 |
| enviesada | 0.95 | 102 | 0.776 [0.737–0.827] | 0.188 |
| citacao | 0.50 | 1111 | 0.922 [0.917–0.925] | 0.921 |
| citacao | 0.60 | 1101 | 0.926 [0.921–0.930] | 0.917 |
| citacao | 0.70 | 1089 | 0.930 [0.925–0.935] | 0.912 |
| citacao | 0.80 | 1076 | 0.936 [0.932–0.939] | 0.907 |
| citacao | 0.85 | 1064 | 0.942 [0.935–0.947] | 0.902 |
| citacao | 0.90 | 1049 | 0.946 [0.942–0.952] | 0.894 |
| citacao | 0.95 | 1017 | 0.954 [0.948–0.959] | 0.873 |
| factual | 0.50 | 3471 | 0.930 [0.928–0.932] | 0.946 |
| factual | 0.60 | 3435 | 0.932 [0.930–0.935] | 0.939 |
| factual | 0.70 | 3386 | 0.938 [0.934–0.941] | 0.931 |
| factual | 0.80 | 3332 | 0.942 [0.938–0.946] | 0.920 |
| factual | 0.85 | 3293 | 0.945 [0.940–0.948] | 0.913 |
| factual | 0.90 | 3235 | 0.949 [0.944–0.952] | 0.901 |
| factual | 0.95 | 3106 | 0.955 [0.950–0.958] | 0.870 |

## Só manchetes (o caso do título no app)

| Classe prevista | limite | n | precisão (acerto) | cobertura (das reais da classe) |
|---|---:|---:|---:|---:|
| enviesada | 0.50 | 33 | 0.531 [0.471–0.588] | 0.374 |
| enviesada | 0.60 | 29 | 0.555 [0.500–0.613] | 0.340 |
| enviesada | 0.70 | 25 | 0.552 [0.522–0.583] | 0.289 |
| enviesada | 0.80 | 18 | 0.539 [0.500–0.588] | 0.204 |
| enviesada | 0.85 | 15 | 0.494 [0.429–0.538] | 0.157 |
| enviesada | 0.90 | 12 | 0.478 [0.400–0.545] | 0.119 |
| enviesada | 0.95 | 5 | 0.612 [0.500–0.667] | 0.068 |
| citacao | 0.50 | 30 | 0.660 [0.645–0.690] | 0.707 |
| citacao | 0.60 | 30 | 0.669 [0.645–0.690] | 0.707 |
| citacao | 0.70 | 28 | 0.701 [0.667–0.741] | 0.700 |
| citacao | 0.80 | 26 | 0.755 [0.714–0.800] | 0.700 |
| citacao | 0.85 | 24 | 0.809 [0.760–0.870] | 0.693 |
| citacao | 0.90 | 23 | 0.852 [0.792–0.909] | 0.693 |
| citacao | 0.95 | 21 | 0.911 [0.826–1.000] | 0.679 |
| factual | 0.50 | 460 | 0.924 [0.918–0.932] | 0.946 |
| factual | 0.60 | 454 | 0.926 [0.921–0.932] | 0.935 |
| factual | 0.70 | 447 | 0.930 [0.924–0.937] | 0.924 |
| factual | 0.80 | 440 | 0.933 [0.927–0.939] | 0.911 |
| factual | 0.85 | 434 | 0.937 [0.933–0.939] | 0.903 |
| factual | 0.90 | 424 | 0.939 [0.935–0.944] | 0.884 |
| factual | 0.95 | 402 | 0.947 [0.939–0.950] | 0.845 |

## Só corpo (frases de matéria)

| Classe prevista | limite | n | precisão (acerto) | cobertura (das reais da classe) |
|---|---:|---:|---:|---:|
| enviesada | 0.50 | 316 | 0.624 [0.611–0.639] | 0.528 |
| enviesada | 0.60 | 288 | 0.640 [0.620–0.660] | 0.493 |
| enviesada | 0.70 | 264 | 0.659 [0.639–0.681] | 0.465 |
| enviesada | 0.80 | 226 | 0.690 [0.672–0.714] | 0.417 |
| enviesada | 0.85 | 203 | 0.713 [0.687–0.738] | 0.387 |
| enviesada | 0.90 | 170 | 0.739 [0.717–0.765] | 0.337 |
| enviesada | 0.95 | 96 | 0.785 [0.750–0.837] | 0.203 |

## Acerto geral conforme a confiança máxima (para o estado 'inconclusivo')

| confiança máxima | frases (corpo) | acerto no corpo | frases (manchete) | acerto na manchete |
|---|---:|---:|---:|---:|
| 0.0–0.5 | 9 | 0.397 | 1 | 0.875 |
| 0.5–0.6 | 67 | 0.545 | 11 | 0.599 |
| 0.6–0.7 | 77 | 0.522 | 13 | 0.594 |
| 0.7–0.8 | 96 | 0.559 | 16 | 0.597 |
| 0.8–0.9 | 159 | 0.619 | 25 | 0.656 |
| 0.9–1.0 | 4008 | 0.941 | 458 | 0.923 |

## Nível da matéria (corpo com >= 8 frases; destaque = 'enviesada' com confiança >= 0.80)

| frases destacadas | matérias (média/semente) | com >= 1 enviesada real | com >= 2 reais | média de reais |
|---|---:|---:|---:|---:|
| 0 | 133 | 0.35 | 0.11 | 0.52 |
| 1 | 45 | 0.73 | 0.40 | 1.56 |
| 2 | 21 | 0.88 | 0.73 | 2.83 |
| 3 ou mais | 23 | 0.96 | 0.92 | 7.20 |

Matérias sem nenhuma frase enviesada real: 0.45.

## Regra de matéria: contagem × contagem e proporção (n = frases do corpo; sementes juntas)

| Grupo | Regra | dispara em (média/semente) | com >= 1 enviesada real | com >= 2 reais | cobre dos com >= 2 reais |
|---|---|---:|---:|---:|---:|
| todas (8 ou mais frases) | 2 ou mais destaques | 44.2 | 0.92 | 0.83 | 0.53 |
| todas (8 ou mais frases) | 2 ou mais destaques E >= 10% do texto | 34.8 | 0.94 | 0.87 | 0.43 |
| 8 a 29 frases | 2 ou mais destaques | 32.0 | 0.89 | 0.79 | 0.48 |
| 8 a 29 frases | 2 ou mais destaques E >= 10% do texto | 27.8 | 0.93 | 0.83 | 0.45 |
| 30 ou mais frases | 2 ou mais destaques | 12.2 | 1.00 | 0.95 | 0.64 |
| 30 ou mais frases | 2 ou mais destaques E >= 10% do texto | 7.0 | 1.00 | 1.00 | 0.39 |

A maior matéria da base tem 69 frases no corpo; acima disso nada foi calibrado. Proporção média de destaques por matéria: 0.049 (p90: 0.156); proporção média de enviesadas reais: 0.082.
