# FactNews no Lume: documentação completa

Atualizada em 06/10/2026. Este documento reúne **tudo** o que foi feito, medido, decidido e deixado pendente na integração do
rotulador de frases (fato, citação, trecho enviesado) ao app Lume. O `README.md` desta pasta é o guia rápido; aqui está o registro
completo, incluindo o que **não deu certo**.

## 1. Resumo

- **O que é:** um modelo (BERTimbau ajustado no FactNews) que rotula **uma frase** de notícia em português como `factual`
  (tom de relato), `citacao` ou `enviesada`. Descreve o **estilo e o papel da frase**. **Não diz se algo é verdadeiro ou falso** e não
  substitui o classificador de veracidade (que é outro projeto).
- **Onde está:** `lume/ml/factnews/` (treino, medidas, servidor local, pacote de pesos) e uma seção "Linguagem do texto" no app
  `lume/prototypes/ocr-native-android/`.
- **Estado:** funcionando **só neste PC (Windows)**, validado no emulador. **Nada foi commitado nem enviado ao GitHub.** A Release
  com os pesos não foi criada. **Nada foi testado em um Mac.**
- **Números principais (frases novas, histórias separadas):** F1 macro **0,804** em validação por história (desenvolvimento) e
  **0,796** no teste lacrado (lido uma única vez como medida). Classe "enviesada": F1 0,55 a 0,57, perde cerca de metade das frases;
  de cada 10 trechos destacados, ~3 não eram enviesados.
- **Ideias avaliadas depois (1, 2, 3, 4):** **nenhuma passou no critério definido antes dos testes** (seção 8). A ideia 6 (voto local)
  ficou como especificação, **não implementada**, por decisão do usuário.
- **Segunda rodada (R1 a R8, seção 8b):** o modelo generaliza entre jornais (sem queda), envelhece pouco e **depende do assunto**; a confiança bruta era alta demais e a
  tela passou a mostrar a precisão medida. Detalhes na seção 8b.

## 2. Linha do tempo e decisões

1. Dados: FactNews v2.0.0 (o zip v3.0.0 do Zenodo não traz o texto das frases). Auditoria, divisão por história, teste lacrado.
2. Baseline TF-IDF + regressão logística (F1 macro 0,608 em validação por história).
3. Ajuste fino do BERTimbau: F1 macro 0,804 (validação) e 0,796 (teste, uma única leitura).
4. Tentativas de melhora só no desenvolvimento (manchete como contexto, 5 sementes, limiar, lr, épocas): nenhuma passou na regra de
   decisão. As frases de cada artigo estão em **ordem alfabética** no CSV, então "frase anterior/seguinte" não existe.
5. Exportação do modelo (treinado nas 100 histórias), `predict.py`, servidor local, integração no app.
6. Calibração da confiança e regras do app (seção 5). O **título nunca conclui sozinho**: em manchetes, "enviesada" acerta só ~49 a 55%.
7. Correções vindas de testes reais no emulador (seção 6.3).
8. Pacote de pesos para o Mac com SHA-256 e verificação (seção 7).
9. Ideias 1, 2, 3 e 4 testadas com critérios fixados antes (seção 8). Ideia 6 documentada (seção 9).

## 3. Dados

- **FactNews v2.0.0** (Vargas, Jaidka, Pardo, Benevenuto; RANLP 2023), CC BY 4.0: 6.191 frases, 100 histórias, cada uma em Folha,
  Estadão e O Globo (2006 a 2008 e 2021 a 2022). Classes: factual 4.242, citação 1.391, enviesada 558.
- **Divisão:** por história (todas as frases de uma história do mesmo lado). Fold 0 = teste lacrado (19 histórias, 1.249 frases); folds 1 a 4 =
  desenvolvimento (81 histórias, 4.942 frases).
- **Auditoria** (`reports/auditoria.md`): manchetes são linhas do dataset (656); na história c78 o id do Estadão colide com o de O Globo;
  252 frases duplicadas em 116 grupos (7 com rótulos conflitantes); só 4,9% das frases têm "gêmea" em outro veículo; **discrepância não
  resolvida** entre o kappa do artigo (0,82) e o do arquivo de anotadores (0,98).
- **Teste lacrado:** `reports/test_usage.log` registra o que ocorreu: (1) uma leitura como **medida** (10:28); (2) uso como **treino**
  do modelo exportado (11:25), mais uma linha de esclarecimento. Nenhuma comparação de ideias (seção 8) tocou o teste.

## 4. Modelo e medidas

| Medida (mesma divisão) | F1 macro | factual | citação | enviesada |
|---|---:|---:|---:|---:|
| Baseline TF-IDF, validação por história | 0,608 | 0,874 | 0,737 | 0,213 |
| BERTimbau, validação por história | 0,804 | 0,938 | 0,921 | 0,552 |
| **BERTimbau, teste lacrado** | **0,796** | 0,922 | 0,899 | 0,568 |

Receita fixada antes de olhar resultados: lr 2e-5, lote 32, 4 épocas, max_len 128, pesos de classe pela raiz da frequência inversa, sementes
42/7/123/2024/99. Variação entre sementes: ±0,004. **Tentativas que não passaram** (regra: IC 95% por bootstrap de histórias excluir zero): manchete
como contexto (−0,005), conjunto de 5 sementes (+0,0002), limiar de "enviesada" (+0,005). lr 3e-5 (+0,002) e 6 épocas (−0,002) ficaram dentro do ruído
entre sementes (sem bootstrap).
O modelo exportado foi treinado nas 100 histórias e **não tem medida própria**: os números acima são da mesma receita treinada só no
desenvolvimento. Detalhes: `reports/comparacao_dev.md`, `reports/bert_cv.json`, `reports/bert_teste.json`.

**Caixa alta:** só 15 de 6.191 frases do treino estão em CAIXA ALTA e o modelo muda de resposta nesse formato ("xerife das composições nas
redes sociais": enviesada 0,98 → citação 0,96). `factnews/text.py` normaliza antes de classificar (custo: nomes próprios no meio da frase).

## 5. Calibração e regras do app (`reports/calibracao_dev.md`, `scripts/06_calibration.py`)

Medidas fora-da-dobra no desenvolvimento, 5 sementes:

| Pergunta | Medida | Regra no app |
|---|---|---|
| Manchete "enviesada" com confiança alta é confiável? | Não: precisão 0,55 com 0,70 e 0,49 com 0,85 | O título **nunca conclui sozinho**; no máximo "indício fraco" (>= 0,70) |
| Frase do corpo "enviesada" com confiança >= 0,80 | precisão 0,69, cobertura 0,42 | "Trecho com possível viés" (sugestão) |
| Quando a matéria tem "sinal de viés"? | >= 2 destaques **e** >= 10% do texto: 94% tinham ao menos uma enviesada real, 87% duas ou mais | Estado "sinal de viés"; 1 destaque ou poucos para o tamanho = "inconclusivo" |
| Frases em dúvida (confiança < 0,70) | ~3% por matéria (p90 10%) | > 25% em dúvida = "inconclusivo" |
| Tamanho | medido de 8 a 69 frases | < 8 frases = "inconclusivo" |

Estados: **sinal de viés**, **sem sinal de viés** (não prova neutralidade), **inconclusivo**, **sem texto para analisar**, **indisponível**
(servidor desligado; o app continua funcionando). "Tom de relato" **não** quer dizer que o fato seja verdadeiro.

**Matérias longas** (`scripts/07_long_simulation.py`, `reports/simulacao_longa_dev.md`): juntando artigos reais da mesma editoria até 20,
40, 70, 100, 150 e 200 frases, a correlação entre a proporção de destaques e a proporção real de enviesadas **sobe** com o tamanho
(0,87 → 0,98). Em textos com menos de 8% de enviesadas reais, a regra dispara em 0% a partir de 70 frases (7% com 20). O custo é deixar
passar: em textos com >= 8% reais ela dispara só em ~50% das vezes. **Isto mede o tamanho; não cobre estilo de revista.** Por isso a nota
"o texto é mais longo que o medido" foi **retirada da tela** (fica só como marcador interno `alemDaCalibracao`).

## 6. Integração no app Lume

### 6.1 Fluxo
Captura (OCR) → pesquisa na web (já existia) → análise de linguagem: (1) o **título** é rotulado primeiro e nunca conclui sozinho;
(2) o **corpo**: o texto da captura se tiver >= 8 frases; senão a **própria matéria encontrada** (mesmo endereço, republicação do mesmo
texto ou resultado com o mesmo título); senão só o título/frase digitada. Até 200 frases, em blocos de 100.

### 6.2 Arquivos
`research/LanguageSignal.kt` (estados, regras, divisão em frases, `sameHeadline`), `research/SentenceRoleClient.kt` (cliente OkHttp do
servidor local, validação da resposta, pausa de 20 s após falha), mudanças em `research/ResearchService.kt` (`languageSignal`,
`evaluate(..., languageText)`), `ResearchResults.kt` (seção "Linguagem do texto"), `MainActivity.kt`/`AnalysisState.kt` (guarda o que a
pessoa digitou), `app/build.gradle.kts` (`ROLE_CLASSIFIER_URL` de `local.properties`; **vazio = recurso desligado**), `SETUP.md`,
`local.properties.example`, testes em `LanguageSignalTests.kt`.

### 6.3 Defeitos encontrados em teste real e corrigidos
1. Título em MAIÚSCULAS (OCR) mudava a resposta → normalização de caixa (`factnews/text.py`).
2. Depois do passo "O que você quer saber?", a análise olhava a frase reformulada ("Houve um caso recente: “…”?") → o app passou a guardar o que a
   pessoa digitou/corrigiu (`languageText`).
3. Regra só por contagem não servia para texto longo → exige também 10% do texto.
4. Texto digitado só com título não achava a matéria na internet → busca entre os resultados um com o mesmo título (`sameHeadline`).
5. Gramática e contagens da tela; rótulo "relato de fato" → "tom de relato" (para não soar como confirmação).
6. Aviso técnico "mais longo que o medido" saiu da tela (pedido do usuário).

### 6.4 Testes
App: 203 testes JVM, 0 falhas (1 ignorado: diagnóstico ao vivo). Lint e `assembleDebug` passam. Python: 45 testes (dados e divisão, texto, servidor,
pacote de pesos, módulo de distância). Emulador (API 37, Windows): captura do print da Piauí, título digitado, afirmação digitada, servidor
desligado: todos OK.

## 7. Servidor, pesos e Mac

- **Servidor** (`scripts/serve.py`): só `127.0.0.1:8765`; `POST /classify` (até 300 frases/1 MB), `GET /health`; exige `Content-Type:
  application/json` e `Host` local (contra páginas web maliciosas no navegador); não registra o texto; erro de inferência devolve 500 sem detalhes.
- **Ligar:** `scripts/start-factnews-server.ps1` (Windows, testado) ou `bash scripts/start-factnews-server.sh` (macOS, **não testado**); ambos fazem
  `adb reverse tcp:8765 tcp:8765`. Arquivo `.sh` com fim de linha LF (`lume/.gitattributes` tem `*.sh text eol=lf`).
- **Pesos** (~192 MB zip, fora do Git): `scripts/package_weights.py` gera `dist/factnews-v2-weights.zip` (reprodutível, SHA-256
  `70ac892a…`) e `weights.json`; `scripts/fetch_weights.py` baixa/instala com verificação do SHA-256 (e `--file` para instalar de um .zip local).
  Testado com servidor HTTP local: o modelo instalado deu probabilidades **idênticas** às do original. Protegido contra zip malicioso, download
  interrompido e checksum errado. O pacote leva `NOTICE.txt` e `BERTimbau-MIT.txt`.
- **Release do GitHub:** **não criada** (ação pública; exige confirmação explícita e permissão de escrita em `matheushpr94/challenge-fakenews-unb`).
  Até lá, o download pelo endereço de `weights.json` dá erro 404.
- **Mac:** nada validado. No Mac o treino de novo (alternativa ao download) seria mais lento (MPS) e os pesos não sairiam idênticos.

## 8. Ideias avaliadas (critérios fixados ANTES de olhar os resultados)

### Ideia 1: detector de "texto fora do que o modelo conhece": **NÃO adotada**
- **Critério:** separar notícia de outro gênero com AUC >= 0,85, mantendo o controle (notícia de agência pública) perto de 5% marcado.
- **Dados externos** (`scripts/08_external_texts.py`; só textos locais, o manifesto `reports/externos_manifesto.json` guarda endereços e SHA-256):
  Wikipédia pt (10 textos), revista/análise (Piauí, The Conversation, CartaCapital política; 174 blocos de 20 frases) e controle (Agência Brasil; 13 blocos).
- **Com o modelo ajustado** (`09_ood.py`, 4 modelos por validação cruzada): revista AUC 0,77 (13% marcados), enciclopédia AUC 0,54, controle 0% (`reports/ood_dev.md`).
- **Sem ajuste fino** (`09b_ood_base.py`, 3 variantes declaradas): Mahalanobis (última camada ou camada 8) AUC ~0,5; 10 vizinhos: revista AUC 0,89, **mas o controle
  de notícia saiu 31% marcado** (`reports/ood_base_dev.md`): ele detecta "veículo e época diferentes do corpus", não gênero.
- **Decisão:** não entra no app. Efeito prático: **não há como detectar automaticamente que um texto é de estilo desconhecido**; o app continua sem
  esse aviso. Limitações: poucos textos externos, uma só revista longa com rótulo conhecido; o resultado pode mudar com mais dados.

### Ideia 2: aprendizado ativo + rotulagem: **parcial (ferramenta pronta; medida em estilo diferente ainda não existe)**
- **Quem rotula:** foi combinado que o Claude rotularia, **só se** passasse num teste de concordância com o gabarito humano. Critério: kappa >= 0,60 e, em
  "enviesada", precisão e revocação >= 0,50. Teste às cegas em 120 frases de desenvolvimento (40 por classe): **kappa 0,47; "enviesada": precisão 0,83,
  revocação 0,25** (`reports/rotulos_aproximados/concordancia.md`). **Critério NÃO atendido:** o Claude lê como enviesadas bem menos frases que os anotadores
  humanos (28 das 40 enviesadas foram lidas como factuais). Logo, os rótulos do Claude **não servem como medida** da revocação; só seriam uma checagem
  unilateral ("se eu digo enviesada, em 83% dos casos o gabarito concorda").
- **Ferramenta entregue** (`scripts/11_active_sample.py`): seleciona ~100 frases de um texto longo (todos os destaques, as mais incertas, amostra aleatória do
  resto) para **rotulagem humana às cegas** e depois calcula precisão do destaque, revocação estimada e kappa. Já gerada para a reportagem da Piauí
  (152 frases, 29 destaques; 100 selecionadas) em `data/rotulagem_humana/` (não versionado: contém o texto da matéria).
- **Resultado da rotulagem da planilha da Piauí** (arquivo `piaui_para_rotular_preenchida.xlsx`, recebido em 06/10/2026; **a origem do arquivo (pasta de uma conversa
  com outra ferramenta) não deixa claro se foi uma pessoa ou outra IA: tratar como medida aproximada até confirmar**): precisão dos 29 trechos destacados **0,79**
  (IC 95% Wilson [0,62; 0,90]; 23 de 29 confirmados, 5 vistos como fato e 1 como citação); revocação estimada **0,56** (o rotulador viu enviesadas em 11 das 30 frases
  "incertas" e em 3 das 41 aleatórias: estimativa de ~18 enviesadas deixadas passar contra 23 acertadas; incerteza grande, só 3 aleatórias positivas); kappa 0,49 entre
  o rotulador e o modelo (amostra enviesada para frases difíceis, não representativa do texto). **Leitura:** nesta reportagem a precisão dos destaques não ficou pior que a
  medida em notícia (0,69), e a revocação (~0,56) é parecida (~0,4 a 0,5). Limites: um único texto, um único rotulador, 29 destaques.

### Ideia 3: léxico de opinião como segunda opinião: **NÃO adotada**
- **Critério:** precisão do destaque +0,05 ou mais, perda de cobertura <= 0,10, IC 95% da diferença de precisão acima de zero.
- **Resultado** (`12_lexicon.py`, OpLexicon v3 + SentiLex-PT02, 23.236 palavras): o léxico dispara em 64% de **todas** as frases (76% nas enviesadas, 63% nas demais), então
  quase não discrimina. Exigir 1 palavra: precisão −0,012 [−0,044; +0,021], cobertura −0,086; 2 palavras: −0,024 e −0,185; 3 palavras: −0,077 e −0,280.
- **Observação de licença:** os léxicos foram lidos de `lexiconPT` (R, GPL-2) e **não** são redistribuídos aqui; a licença dos dados originais não está esclarecida.

### Ideia 4: contraste entre veículos: **NÃO adotada**
- **Critério:** ganho de AP (precisão média) >= 0,02 com IC 95% acima de zero, além do que o modelo já prevê.
- **Resultado** (`13_contrast.py`): com pareamento por palavras só 15% das frases têm par claro em outro veículo (6% das enviesadas); ganho de AP −0,001
  [−0,007; +0,003]. Com pareamento semântico (paraphrase-multilingual do Lume, limiar 0,72): 32% têm par, na mesma proporção em enviesadas e demais (31% e 33%);
  ganho −0,002 [−0,010; +0,007]. Os jornais escrevem frases diferentes; ter ou não um par não prevê viés.

## 8b. Segunda rodada: oito análises que só dependem do dataset (numeração R1 a R8, para não confundir com as ideias 1 a 6 acima)

Todas só no desenvolvimento (teste lacrado intocado). Scripts `14_robustness.py`, `15_shortcuts.py`, `16_oof_analises.py`; relatórios `reports/robustez_dev.md`, `atalhos_dev.md`, `analises_oof_dev.md`.

| # | Análise | Resultado | Efeito no app |
|---|---|---|---|
| R1 | Treinar sem um jornal e medir nele | **Sem queda**: Δ F1 macro −0,008 a +0,008, ICs incluem zero; "enviesada" −0,02 a +0,01 | Nenhum. O modelo generaliza entre Folha, Estadão e O Globo (todos jornais diários de grande porte: **não vale como prova para revista**) |
| R2 | Treinar no passado e medir no presente (e o inverso) | Queda pequena: −0,013 e −0,016 no F1 macro (IC inclui zero); "enviesada" −0,024 e −0,050 | Nenhum. Indício leve de envelhecimento; vale reavaliar com dados novos |
| R3 | Treinar sem uma editoria | **Dependência do assunto**: sem política, "enviesada" cai de 0,52 para 0,15 (IC exclui zero); sem esportes 0,78 → 0,60. Parte da queda é falta de dados (sem política o treino fica com 1.591 frases) | Aviso nos limites: o sinal de "enviesada" é mais fraco em assuntos pouco vistos |
| R4 | Calibração da confiança | O modelo é **confiante demais**: confiança 0,90 a 0,97 acerta 81%; 0,80 a 0,90 acerta 63%. Escala de temperatura (T ≈ 1,4) reduz o ECE de 0,054 para 0,021. Nos trechos "enviesada", a precisão real é ~55% (conf. 0,80 a 0,90), ~65% (0,90 a 0,95) e ~78% (0,95 a 0,99) | **Mudou a tela:** em vez de "98% de confiança do modelo", mostra "em testes, cerca de N% dos trechos assim eram realmente enviesados" (`LanguageRules.estimatedPrecision`). A temperatura **não** foi aplicada no servidor, para não deslocar os limiares já calibrados |
| R5 | "Inconclusivo" por predição conforme | Garante cobertura por classe (α = 0,10: 89 a 90%), decide 76% das frases com acerto 0,88, mas a precisão de "enviesada" decidida é 0,36 a 0,64 (conforme α), pior que a regra atual (0,69) | Não adotado: serve para fato/citação, não melhora os destaques |
| R6 | Tipologia de erros | Erro geral 9,5%. **Nenhum grupo ficou claramente pior** (todos os ICs incluem o erro geral). Os de erro mais alto: começa em minúscula (12,8%), esportes (12,7%), frase curta (11,4%), manchete (11,2%). Frases com aspas, com dígitos ou sem pontuação final erram menos. Precisão dos destaques **não** muda com o tamanho da frase (0,67 a 0,68) | Nenhuma regra nova (o tamanho da frase não muda a precisão) |
| R7 | Auditoria de atalhos | Embaralhar as palavras derruba o F1 macro de 0,81 para 0,61 e o de "enviesada" de 0,57 para 0,13: o modelo usa conteúdo e **ordem**. Sem aspas: −0,03. Só metadados (editoria, veículo, ano, manchete): 0,33; tamanho da frase e aspas: 0,46 | Nenhum. Há algumas pistas de forma, mas explicam pouco |
| R8 | Anotadores / rótulos suaves | Os dois anotadores discordam em **53 de 6.191 frases (0,9%)**; o modelo acerta 53% nelas (contra 91% no resto) | Nada a ganhar com rótulos suaves. A subjetividade está no critério comum aos dois, não na discordância |

## 9. Ideia 6 (voto local do usuário): ESPECIFICAÇÃO, **não implementada** (decisão do usuário)

- **Objetivo:** coletar, no próprio aparelho, a opinião da pessoa sobre cada trecho destacado, para construir com o tempo um conjunto de avaliação
  em estilos diferentes e medir o modelo. **Votos nunca saem do aparelho** (evita que pessoas mal-intencionadas interfiram em um conjunto compartilhado).
- **Interface:** em cada "trecho com possível viés", dois botões discretos ("concordo" / "não é enviesado"). Opcional e sem texto longo (alinhado ao redesenho da tela).
- **Dados guardados (arquivo privado do app):** hash da frase, texto da frase, rótulo e confiança do modelo, voto, data, versão do modelo e das regras.
  Sem identificadores da pessoa, sem rede, com opção "apagar todos os votos".
- **Validação posterior (local):** os votos **não treinam nada automaticamente**. Um script exporta e agrega: remove duplicatas, sinaliza padrões suspeitos
  (por exemplo, quase todos os votos iguais ou em sequência rápida), separa uma amostra para auditoria manual e só então calcula precisão por estilo/gênero.
  Votos de uma pessoa só medem a opinião dela: é "medida aproximada", como os demais rótulos não humanos.
- **Critérios de aceitação para implementar:** gravação funciona sem rede; apagamento é completo; nenhum voto altera o modelo ou as regras sem revisão;
  testes JVM cobrindo gravação, exportação e apagamento; texto na tela explicando que tudo fica no aparelho.
- **Riscos:** poucos votos e vieses de quem vota; não substitui anotadores treinados; coleta de texto de matérias (direitos autorais) só local.

## 10. Limites e riscos conhecidos

- Treinado em notícia de três jornais (2006 a 2008 e 2021 a 2022). Reportagem de revista, opinião e redes sociais **não foram medidas**; o app não sabe avisar
  quando o texto é de estilo diferente (ideia 1 falhou).
- "Enviesada" é um julgamento subjetivo: o modelo perde cerca de metade das frases e ~3 em cada 10 destaques não eram enviesados.
- Em manchetes o modelo é pouco confiável (F1 0,70); por isso o título só dá indício.
- O sinal de "enviesada" depende do assunto (R3): em assuntos pouco vistos no treino ele é bem mais fraco. Nos trechos destacados, só ~55% a ~78% eram realmente enviesados.
- Opiniões explícitas fora do estilo de jornal podem sair como "citação" com confiança alta (exemplo inventado: 0,98); a confiança não é calibrada nesses casos.
- Afirmações digitadas como "X matou 700 mil pessoas" saem como "tom de relato": o modelo **não detecta falsidade**. Isso é do classificador de veracidade.
- O Lume ainda diz "não encontramos outros veículos" com frequência. Hipóteses (**não medidas**): a comparação exige agente, ação e data, o que não existe em reportagens e
  análises; a mensagem para esses casos é enganosa. Ver pendências.
- Windows e emulador validados; macOS **não**.

## 11. Pendências e próximos passos (em ordem sugerida)

1. **Você testar o app** (servidor: `lume\ml\factnews\scripts\start-factnews-server.ps1`).
2. **Evidência e design do Lume** (pedido em 06/10/2026, **ainda não feito**): (A) medir em 15 a 20 casos reais em que etapa as fontes caem;
   (B) distinguir acontecimento de reportagem/análise e trocar a mensagem enganosa "não encontramos outros veículos"; ampliar a busca com subtítulo e primeiras frases;
   (C) redesenho: cartão de topo com um estado, detalhes escondidos, fonte maior (corpo >= 16 sp), um único rodapé de aviso.
3. **Planilhas de rotulagem** (ver `PLANILHAS.md`): preencher a planilha mista (926 frases, gerada e ainda não preenchida) e esclarecer a divergência das duas versões da Piauí (precisão 0,79 contra 0,55; kappa 0,18 entre elas).
4. Ideia 6 (voto local), quando decidido implementar.
5. Revisão independente do código (foi iniciada e interrompida; nenhum achado foi lido).
6. **Commit e push** (excluir `gradlew.bat`, `local.properties`, `models/`, `dist/`, `data/`) e **Release** `factnews-v2` com o zip, **só com confirmação explícita**.
7. Validação em um Mac.

## 12. Reprodução

Na pasta `lume/ml/factnews` com o ambiente do README: `python scripts/00_download_data.py`, `01_audit.py`, `02_baseline.py`, `03_finetune_bert.py --cv`,
`04_context_ensemble.py --cv ...`, `05_analyze.py`, `06_calibration.py`, `07_long_simulation.py`, `08_external_texts.py`, `09_ood.py`, `09b_ood_base.py`,
`10_label_agreement.py` (precisa de `data/rotulagem_amostra_gold.json`, gerado na sessão; rótulos em `reports/rotulos_aproximados/`), `11_active_sample.py`,
`12_lexicon.py` (precisa dos léxicos em `data/external/lex/`), `13_contrast.py [--semantic]` (o semântico precisa do Ollama com `paraphrase-multilingual`).
Testes: `python -m unittest discover -s tests` (45) e, no app, `gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.

## 13. Atribuições

BERTimbau (MIT, NeuralMind; texto em `licenses/BERTimbau-MIT.txt`) e FactNews v2.0.0 (CC BY 4.0, Vargas et al., RANLP 2023). `NOTICE-weights.txt` acompanha os pesos.
Wikipédia pt (CC BY-SA) e matérias de revistas/agência foram usadas **só localmente** para testes; o repositório guarda apenas endereços e SHA-256.
