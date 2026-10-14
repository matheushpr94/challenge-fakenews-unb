# Challenge FakeNews UnB

Detecção de desinformação com IA, em português, e um produto que a apoia: o **Lume**. Projeto do primeiro challenge oficial da Residência em Inteligência Artificial (UnB / Instituto Eldorado).

O objetivo não é só "acertar", é construir algo **honesto**: um processo que não deixa a gente se enganar com métricas infladas por vazamento de dados, e um produto que não afirma o que não consegue sustentar.

## O problema

Desinformação é diferente de viés. Uma notícia enviesada pode ser factualmente verdadeira. Por isso olhar só o estilo do texto não basta: o repositório tem **duas frentes** que se complementam.

1. **Classificar o texto** (falsa × verdadeira) com modelos clássicos de NLP, medindo com cuidado o que o modelo realmente aprendeu.
2. **Checar o fato**: dizer se uma fonte encontrada pela busca fala do **mesmo fato** que a notícia (modelo "mesmo fato"), que é o que o Lume usa para comparar fontes.

## Datasets

| Dataset | Uso | Onde |
|---|---|---|
| **FakeRecogna** (PT, notícias falsas e verdadeiras) | baseline e classificador final do texto (8.248 treino, 1.768 validação, 1.768 teste) | `notebooks/FakeRecogna.csv` |
| **FakeTrueBr** (PT) | corpus de notícias em português disponível para os experimentos | `notebooks/FakeTrueBr_corpus.csv` |
| **Pares "mesmo fato"** (afirmação × título de fonte, coletados no próprio app, em 4 rodadas) | treino, escolha e prova do modelo "mesmo fato": 14.592 fontes | `notebooks/mesmo-fato/dados/` |
| **LIAR2** (EN, afirmações checadas) | só a **auditoria inicial** de vazamento (`notebooks/01_auditoria_liar2.ipynb`); **não é usado pelo Lume nem pelos modelos atuais** | baixar conforme [`data/README.md`](data/README.md) |

Os dados do LIAR2 não ficam versionados neste repositório.

## Abordagem

O pipeline segue as fases de um processo investigativo (estilo CRISP-DM):

1. **Entender os dados** — auditoria, distribuição das classes, integridade do split.
2. **Caçar vazamento (leakage) e atalhos** — provar o que o modelo usa de graça (colunas que entregam a resposta, fonte do texto, época) e medir sem eles.
3. **Baseline honesto** — TF-IDF + Regressão Logística e SVM, medindo só na validação e abrindo o teste uma única vez.
4. **Modelo "mesmo fato"** — MPNet ajustado (similaridade), BERTimbau ajustado (nota por par) e regressão logística, com rótulos de dois juízes de linguagem, desempate e âncora humana, e uma prova lacrada antes da coleta.
5. **Avaliação** — matriz de confusão, precisão/recall, cobertura contra a regra do MVP, onde o modelo erra.
6. **Produto e relatório honesto** — número real, com limitações declaradas, dentro de um app que mostra a evidência e não dá veredito.

## Estrutura do repositório

```
challenge-fakenews-unb/
├── data/            # instruções para baixar datasets (NÃO versionados)
├── notebooks/       # baseline FakeRecogna, BERTimbau e o pipeline "mesmo fato"
│   └── mesmo-fato/  # modelo, relatório técnico, dados e cadernos do pipeline
├── modelo/          # classificador final de texto (FakeRecogna, SVM)
├── src/             # código reutilizável
├── docs/            # decisões, guiding questions e revisão bibliográfica
├── reports/         # resultados e figuras
├── lume/            # produto: app Android, modelos locais e documentação
├── requirements.txt
└── README.md
```

## Produto Lume

O [Lume](lume/README.md) é um app Android com mascote flutuante que ajuda a entender notícias e posts a partir de um print. O código do produto fica em `lume/`, separado dos experimentos acadêmicos com LIAR2.

**Versão atual:** [`lume/prototypes/ocr-native-android`](lume/prototypes/ocr-native-android) (Kotlin + Compose). O que ela já faz:

1. lê o print com OCR (ML Kit) e separa título, corpo, veículo, data e autor; a captura acontece só quando a pessoa toca no mascote e autoriza o Android;
2. escolhe a afirmação principal e a interpreta (quem, ação, números, datas, negação), perguntando "O que você quer saber?" quando há ambiguidade;
3. busca matérias candidatas em fontes de notícia (Google Notícias, Bing Notícias, Bing Web e Wikipédia, sem chave de API) e descarta a própria matéria e republicações;
4. compara cada candidata com a matéria importada por regras estruturadas (quem agiu, ação e etapa, assunto, data, números, negação) e mostra o trecho que sustenta a relação;
5. pode usar `paraphrase-multilingual` para similaridade/ordenação e, quando os serviços locais estão configurados, o modelo treinado de pares (MPNet ajustado, sobreposição lexical e BERTimbau ajustado) como apoio à comparação: ele só promove uma fonte a "Incerta", confirma com nota alta ou avisa "confira a fonte";
6. lê a página da própria matéria (endereço lido pelo navegador no toque do mascote, pela busca ou por domínio e título) e, com 60 palavras ou mais, mostra o **sinal do texto**: uma faixa de estilo de escrita calculada por um SVM treinado no FakeRecogna, com as palavras que mais pesaram;
7. indica quando uma agência de checagem (Aos Fatos, Lupa, Comprova, Estadão Verifica, Fato ou Fake, Boatos.org, E-Farsas, AFP) aparece entre as fontes.

O Lume **não afirma que algo é verdadeiro ou falso** e não usa o LIAR2. O sinal do texto mostra o **estilo** (tende a lembrar notícia comum, sem sinal claro, tende a lembrar desinformação), nunca um veredito, e foi calibrado com poucos dados (81 notícias de um dia). A integração dos modelos é opcional, depende de serviços locais (Ollama e dois servidores Python, ligados por `lume/ml/iniciar_modelos_locais.sh`) e não substitui as regras de comparação. Limites e medidas: [`lume/docs/SINAL-DO-TEXTO.md`](lume/docs/SINAL-DO-TEXTO.md) e [`lume/docs/FLUXO-COMPLETO-DO-LUME.md`](lume/docs/FLUXO-COMPLETO-DO-LUME.md).

A versão anterior (demo offline com respostas fictícias, APK 0.3.0 e prévia web) ficou como referência em [`lume/prototypes/referencia-antigo`](lume/prototypes/referencia-antigo).

## Como rodar

```bash
git clone https://github.com/matheushpr94/challenge-fakenews-unb.git
cd challenge-fakenews-unb
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# os CSVs usados nos notebooks de português estão em notebooks/; o LIAR2 (só na auditoria inicial)
# se baixa conforme data/README.md. Depois abra os notebooks:
jupyter lab
```

## Resultados

| Frente | Medida | Resultado | Onde |
|---|---|---|---|
| Texto (FakeRecogna) | TF-IDF + SVM linear, teste aberto uma vez | acurácia **95,7%** (validação 96,2%); uma validação "sem atalhos" do mesmo notebook deu 90,3% | `notebooks/baseline_fakerecogna.ipynb` |
| "Mesmo fato" | prova lacrada (100 notícias, 4.298 pares), aberta uma vez em 08/10/2026 | acha **7,58** de cada 10 fontes do mesmo fato, com precisão de 74,3%; a regra do app acha 3,57 (75,0%) e a do MVP, 3,48 (73,3%) | `notebooks/mesmo-fato/relatorio-tecnico.md` |
| Lume (app) | modelo reduzido (3 entradas), cortes altos, só promove ou avisa | cobertura de 48,6% para 54,6% em 1.398 pares de desenvolvimento, precisão de ~82% | `lume/docs/RELATORIO-MODELO-MATHEUS-NO-APP.md` |

Os números do app e os do modelo completo são de conjuntos e pontos de corte diferentes; não se comparam diretamente.

## Decisões e limitações

- **O modelo de texto não verifica fatos.** A classe "falsa" do FakeRecogna é em grande parte checagem e boato de poucos sites; o modelo reconhece o gênero e o vocabulário de uma época. Por isso o Lume o mostra só como **estilo**, nunca como veredito.
- **O modelo "mesmo fato" erra por idade da fonte:** acerta mais com fontes do mesmo dia ou da véspera e bem menos com fontes de mais de um mês (ver a ficha do modelo).
- **A nota do modelo não é probabilidade calibrada**, então o app não mostra percentual.
- **O app roda hoje com serviços locais** (Ollama e servidores Python) e foi validado em emulador; aparelho físico, `robots.txt` e API para o celular estão pendentes (ver `lume/README.md`).
- Decisões anteriores e o registro da escolha dos datasets estão em [`docs/`](docs/).

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
