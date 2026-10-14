# Challenge FakeNews UnB

Detecção de desinformação com IA, usando o dataset **LIAR2**. Projeto do primeiro challenge oficial da Residência em Inteligência Artificial (UnB / Instituto Eldorado).

O objetivo não é só "acertar", é construir um classificador **honesto**: um processo que não deixa a gente se enganar com métricas infladas por vazamento de dados.

## O problema

Desinformação é diferente de viés. Uma notícia enviesada pode ser factualmente verdadeira. Por isso o alvo é **identificar** conteúdo falso com evidência e método, não punir estilo. O foco deste repositório é a tarefa de **classificação de afirmações** (fact-checking automático) sobre o LIAR2.

## Dataset

- **LIAR2** (~23 mil afirmações rotuladas por checadores profissionais, em inglês). Repositório oficial: [chengxuphd/liar2](https://github.com/chengxuphd/liar2).
- Rótulo em 6 níveis (`pants-fire`, `false`, `barely-true`, `half-true`, `mostly-true`, `true`) ou binarizado (falso vs verdadeiro).
- O dado **não fica versionado neste repo** (ver [`data/README.md`](data/README.md) para baixar).

Decisão de idioma: o grupo optou pelo inglês para casar com o LIAR2, que é claim-level e tem metadados ricos.

## Abordagem

O pipeline segue as fases de um processo investigativo (estilo CRISP-DM):

1. **Entender os dados** — auditoria, distribuição das classes, integridade do split.
2. **Caçar vazamento (leakage)** — provar quais colunas entregam a resposta de graça (ex.: `justification`, `*_counts`) e removê-las.
3. **Baseline honesto** — Dummy → TF-IDF + Regressão Logística, medindo só na validação.
4. **Modelo forte** — fine-tuning de transformer (ex.: DistilBERT/RoBERTa), só se ganhar do baseline.
5. **Avaliação** — matriz de confusão, precisão/recall/F1, custo do erro.
6. **Relatório honesto** — número real, com limitações declaradas. O teste fica lacrado até o fim.

## Estrutura do repositório

```
challenge-fakenews-unb/
├── data/            # datasets (NÃO versionados — ver data/README.md)
├── notebooks/       # análises passo a passo
├── src/             # código reutilizável (dataset, features, treino, avaliação)
├── docs/            # respostas das guiding questions e decisões
├── reports/         # resultados e figuras
├── lume/            # produto: Android, servidor e prévia web
├── requirements.txt
└── README.md
```

## Produto Lume

O [Lume](lume/README.md) é um app Android com mascote flutuante que ajuda a entender notícias e posts a partir de um print. O código do produto fica em `lume/`, separado dos experimentos acadêmicos com LIAR2.

**Versão atual:** [`lume/prototypes/ocr-native-android`](lume/prototypes/ocr-native-android) (Kotlin + Compose). O que ela já faz:

1. lê o print com OCR (ML Kit) e separa título, corpo, veículo, data e autor;
2. escolhe a afirmação principal e a interpreta (quem, ação, números, datas, negação);
3. busca matérias candidatas em fontes de notícia e descarta a própria matéria e republicações;
4. compara cada candidata com a matéria importada por regras estruturadas e mostra o trecho que sustenta a relação;
5. pode usar `paraphrase-multilingual` para similaridade/ordenação e, quando os serviços locais estão configurados, o modelo treinado de pares (MPNet ajustado, sobreposição lexical e BERTimbau ajustado) como apoio à comparação.

O Lume **não afirma que algo é verdadeiro ou falso** e não usa o classificador do LIAR2. A integração do modelo treinado é opcional, depende de serviços e pesos locais e não substitui as regras de comparação.

**Em planejamento:** uma nova feature construída a partir de um dataset de alegações já checadas por agências, indicando se a afirmação já foi checada. Fluxo e plano em `lume/`.

A versão anterior (demo offline com respostas fictícias, APK 0.3.0 e prévia web) ficou como referência em [`lume/prototypes/referencia-antigo`](lume/prototypes/referencia-antigo).

## Como rodar

```bash
git clone https://github.com/matheushpr94/challenge-fakenews-unb.git
cd challenge-fakenews-unb
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# baixe os dados seguindo data/README.md, depois abra os notebooks:
jupyter lab
```

## Resultados

| Modelo | Feature | Métrica (validação) |
|---|---|---|
| Baseline de chute | — | ~58% acurácia |
| TF-IDF + Regressão Logística | `statement` | a preencher |
| Transformer (fine-tuning) | `statement` | a preencher |

> A tabela é atualizada conforme os experimentos avançam.

## Decisões e limitações

- **Leakage confirmado no LIAR2:** `justification` (texto do checador) e `*_counts` (histórico do falante) inflam o resultado. Feature de treino = `statement`. Ver [`docs/`](docs/).
- **Limitações:** o LIAR2 é alegação política curta em inglês; os termos são parcialmente temáticos (assunto misturado com veracidade).

## Equipe

- Ana Paula Gomes de Matos
- Maria Luisa Oliveira Lima
- Jhecy Ketlin Gomes Vieira
- Guilherme Bastos Moreira
- Matheus Henrique Picone Rosa — [@matheushpr94](https://github.com/matheushpr94)
- Pedro Henrique Gonçalves de Oliveira

## Referências

- Xu & Kechadi (2024). LIAR2: A Reworked and Enhanced Version of LIAR.
- Vosoughi, Roy & Aral (2018). The spread of true and false news online. *Science*.
- Kapoor & Narayanan (2023). Leakage and the reproducibility crisis in ML-based science. *Patterns*.

## Licença

MIT — ver [`LICENSE`](LICENSE).
