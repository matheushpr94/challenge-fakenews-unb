# Decisões do projeto

Registro das escolhas principais e o porquê. Serve de memória do grupo e de material para a apresentação.

## Por que LIAR2 (e não os datasets de artigo)

Comparamos candidatos antes de escolher:

- **ISOT (Kaggle, LSTM 97,9%)** — descartado. As notícias "reais" são todas da Reuters e começam com "WASHINGTON (Reuters)". O modelo aprende a FONTE, não a veracidade. Usado só como contra-exemplo.
- **FakeRecogna / Fake.br (PT, artigo)** — bons, mas em português e nível de artigo.
- **LIAR2 (EN, alegação, 6 classes)** — escolhido para treino/teste. É claim-level (mais perto de fact-checking real), tem metadados ricos e é um benchmark acadêmico moderno.

Decisão de idioma: inglês, para casar com o LIAR2.

## Vazamento (leakage) no LIAR2 — comprovado

Treinamos três classificadores binários, cada um com uma única informação, e comparamos na validação:

| Enxerga | Acerto | Veredito |
|---|---|---|
| Chute (classe maior) | ~58% | piso |
| `*_counts` (histórico do falante) | ~63% | fraco |
| `statement` (a afirmação) | ~70% | honesto |
| `justification` (texto do checador) | ~81% | vazamento |

**Regra de treino:** usar apenas `statement` (no máximo `speaker`/`subject`/`context` como metadado). Proibido `justification` e `*_counts`.

## Integridade do split

Os splits oficiais do LIAR2 (train/valid/test, 8:1:1) foram checados: sobreposição de afirmações entre partes é desprezível. Usar os splits oficiais; não re-dividir aleatoriamente. O `test` fica lacrado até o relatório final.

## Métrica

- Binário: F1 (além da acurácia).
- Multiclasse (6 níveis): macro-F1.
- Escolher a métrica antes de treinar, para não mover a trave depois.
