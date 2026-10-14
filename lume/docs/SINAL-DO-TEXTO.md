# Sinal do texto (estilo da escrita) — como funciona, como foi calibrado e o que NÃO mostra

Atualizado em 14/10/2026. Código: `lume/ml/sinal_texto/` (servidor e calibração) e `ResearchResults.kt` (tela).

## O que é
Um modelo SVM treinado no **FakeRecogna** (`modelo_fakerecogna_svm.pkl`, SHA-256 `eb65fa10…999866a`) que mede o quanto o **estilo e o vocabulário**
de um texto lembram textos de boato/checagem ou de notícia comum. O app mostra só uma **faixa** ("tende a lembrar desinformação", "sem sinal claro",
"tende a ser de notícia comum"), a posição numa escala e as palavras que pesaram. Nunca mostra percentual nem "é falso".
**Não verifica fatos** e **não altera a classificação das fontes**.

Pipeline original (lido do `.pkl` com um carregador que só aceita classes de scikit-learn/numpy): `TfidfVectorizer` (1 a 2 palavras, 30.000 termos,
`min_df=2`) + `LinearSVC(C=1)`. Classe 0 = falsa (boato/checagem), classe 1 = verdadeira (notícia comum), conferido com os dados. O arquivo foi
exportado para `modelo_sinal.json` e é pontuado em Python puro (diferença máxima de 9e-8 contra o scikit-learn em 1.500 textos).

## Como entra no fluxo
1. A entrada (corpo da matéria lida, ou o texto colado se for longo) vai ao servidor local `127.0.0.1:8766` **em paralelo com a busca**.
2. O servidor lematiza (spaCy `pt_core_news_sm`), calcula o TF-IDF e a distância ao hiperplano, e devolve faixa, posição e termos.
3. O app espera o resultado só no fim, com prazo de 1,5 s; se não vier, a tela segue sem o cartão. Medido: o servidor responde em 25 a 165 ms,
   bem antes de a busca terminar (3 a 8 s), então **não soma tempo**.
4. Guardas: pelo menos 60 palavras; título solto, frase curta, link e texto todo em maiúsculas não recebem sinal.

## Calibração (feita com dados nossos, 14/10/2026)
O modelo só entende texto **lematizado** (a coluna `Noticia` do FakeRecogna): em título cru ele acerta 50,8% (acaso). Por isso lematizamos com spaCy;
os lemas não são idênticos aos do dataset (ele tem "papar", "presar"), então as faixas foram ajustadas em **texto cru do mesmo tipo que o app recebe**.

| Conjunto | n | Marcado "desinformação" | Marcado "notícia comum" |
|---|---|---|---|
| Notícias reais do dia (Bing Notícias + página, 29 domínios, mediana 686 palavras) | 81 | **2,5%** (2/81; IC 95% 0,7% a 8,6%) | 63% |
| as mesmas, cortadas em 100 palavras | 81 | 1,2% | 30% |
| Boatos (FakeTrueBr, coluna `fake`, boatos.org) | 329 | 69,6% | 0,6% |
| FakeRecogna classe 0 (boato/checagem, já lematizado) | 800 | 80,4% | 0,7% |
| FakeRecogna classe 1 (notícia, já lematizado) | 800 | 8,4% | 75,9% |

Cortes (distância ao hiperplano; positivo = estilo de notícia): textos de 150 palavras ou mais, "desinformação" abaixo de −0,75 e "notícia comum" a
partir de +0,30; textos curtos (60 a 149 palavras), mais ruidosos, usam −1,00 e +0,30. Os cortes foram escolhidos para que **poucas notícias reais
sejam marcadas** (alvo de até 3%) e quase nenhum boato chegue à faixa de notícia. Arquivo: `lume/ml/sinal_texto/faixas.json`.

## Limites (o que o usuário precisa poder ler na tela, e o que o grupo precisa saber)
- **É estilo e fonte, não falsidade.** A classe "falsa" do FakeRecogna é em grande parte texto de checagem e de boatos de poucos sites. Os textos
  de checagem do g1 (FakeTrueBr, coluna `true`) são marcados como "desinformação" em 84% dos casos: o modelo reconhece o *gênero checagem*, não a falsidade.
- Entre datasets o desempenho cai a perto do acaso (análise de 07/10: 40% a 53%). Por isso o app **não usa o sinal para decidir nada**.
- Notícia real em tom de rede social pode cair em "desinformação"; boato bem escrito pode cair em "notícia comum" ou "sem sinal claro".
- A calibração usa **um dia** de notícias, 81 textos e 29 domínios; o intervalo de 95% da taxa de falso alarme vai até 8,6%.
- Texto curto (< 150 palavras) tem pontuação mais baixa em média: por isso o corte é mais conservador e há mais "sem sinal claro".
- O lematizador não é o do dataset; parte do vocabulário do modelo (lemas como "papar") não casa.
- Cruzamento com as fontes: se o texto cai em "desinformação" e 3 ou mais veículos independentes relatam o mesmo fato, a tela avisa que o estilo
  pode vir de rede social ou de um boato citado; se alguma fonte é de agência de checagem (Aos Fatos, Lupa, Comprova, Estadão Verifica,
  Fato ou Fake, Boatos.org, E-Farsas, AFP), ela aparece como "Já checado por".

## Privacidade
O texto vai do app para `127.0.0.1` (o computador, via `adb reverse`), não para a internet. O servidor não grava o texto; o app registra no log só
faixa, posição e quantidade de palavras. O texto das notícias usado na calibração (terceiros) não está no repositório.

## Como ligar e desligar
- Ligar tudo: `bash lume/ml/iniciar_modelos_locais.sh` (Ollama, BERTimbau do Matheus, sinal do texto e `adb reverse`).
- `local.properties`: `lume.textSignal.url=http://127.0.0.1:8766`. Sem essa linha, o app é o de antes.
- Recalibrar: `python coletar_noticias.py saida.jsonl` e `python calibrar.py saida.jsonl` (em `lume/ml/sinal_texto`).
