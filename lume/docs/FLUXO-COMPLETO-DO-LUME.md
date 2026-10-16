# Lume — fluxo completo do app e tudo o que ele usa

Documento de apoio à entrega final do Challenge 1. Versão do protótipo: `lume/prototypes/ocr-native-android` (Kotlin + Jetpack Compose),
estado de **14/10/2026**.

## Como este documento foi feito (leia primeiro)

- **Fonte das informações:** o código do app, os arquivos de configuração e as medições feitas nas sessões de trabalho de 07 a 14/10/2026.
  Cada número vem de um arquivo ou de uma execução citada.
- **O que ficou de fora de propósito:** textos antigos do repositório que descrevem o app anterior (demo offline com respostas fictícias,
  APK 0.3.0, prévia web, "feature em planejamento" etc.). Eles estão desatualizados em relação ao protótipo atual e **não** foram usados.
- **Só o que está em uso.** Componentes descartados ao longo do projeto não aparecem como parte do produto (há uma lista curta ao final
  só para evitar confusão).
- **Honestidade sobre limites:** cada seção diz o que foi medido, com que amostra, e o que não foi verificado.
- **Autoria:** o documento descreve o sistema, não atribui decisões a pessoas. A divisão de papéis e a organização do Scrum precisam ser
  preenchidas pelo grupo (seção 19).

---

## 1. Contexto e problema

Uma pessoa recebe uma notícia, um post ou um print e quer saber **o que outras fontes dizem sobre aquele mesmo fato**, sem sair do que está lendo.

Princípio central de produto, implementado no código: **o Lume não diz se algo é verdadeiro ou falso.** Ele mostra fontes, como elas se
relacionam com o texto lido e, quando há texto suficiente, o **estilo da escrita**. Os comentários do código dizem isso literalmente
(por exemplo, em `research/Relation.kt`: "Não diz se a afirmação é verdadeira: diz se a fonte trata do mesmo acontecimento…").
A linguagem da interface evita afirmação categórica ("tende a lembrar…", "sem sinal claro").

## 2. A solução em uma frase

Um app Android com um mascote flutuante: a pessoa toca nele sobre qualquer app (ou compartilha/cola um texto, link ou imagem), o Lume
**lê a tela**, entende qual é a afirmação, **busca e lê fontes**, **compara** cada uma com o texto, usa **modelos de IA locais** como apoio
e mostra tudo com a **evidência literal** que sustenta cada relação.

## 3. Visão geral do fluxo (ponta a ponta)

```text
 ┌──────────── ENTRADA ────────────┐
 │ pop-up (mascote) · compartilhar │
 │ texto/link/imagem · colar ·     │
 │ importar imagem · texto marcado │
 └───────────────┬─────────────────┘
                 ▼
   [1] Captura (1 imagem, só ao toque)  +  [1b] endereço da página (acessibilidade)
                 ▼
   [2] OCR no aparelho (ML Kit) → blocos com posição e confiança
                 ▼
   [3] Leitura da matéria: título, subtítulo, corpo, veículo, autor, data, link
       + defesas contra erro de OCR
                 ▼
   [4] Escolha da afirmação principal
                 ▼
   [5] Esclarecimento "O que você quer saber?" (só se houver ambiguidade)
                 ▼
   [6] Interpretação por regras: entidades, termos, ação, números, datas, negação, consultas
                 ▼
   [7] Busca sem chave de API: Google Notícias · Bing Notícias · Bing Web · Wikipédia   ┐
   [7b] Sinal do texto (SVM FakeRecogna) em paralelo                                     │ em paralelo
   [7c] Similaridade (Ollama) + modelo de pares (MPNet+BERTimbau+regressão)              ┘
                 ▼
   [8] Agrupamento de republicações e exclusão da própria matéria
                 ▼
   [9] Comparação estruturada do acontecimento (quem, ação, etapa, assunto, data, números)
       + promoção/aviso pelo modelo de pares
                 ▼
   [10] Prévia (título e trecho) → leitura das páginas das fontes → releitura
                 ▼
   [11] Síntese do detalhe (quando há número/ano comparável) e contexto
                 ▼
   [12] Tela de resultados com evidência literal, "Por que esta relação?" e histórico local
```

## 4. Entradas aceitas

Declaradas em `AndroidManifest.xml` e tratadas em `MainActivity.kt`:

| Entrada | Como chega |
|---|---|
| Pop-up do mascote → "Ler esta tela" | `MascotService` (sobreposição) → `CaptureActivity` → `CaptureService` (MediaProjection) |
| Compartilhar **texto** (ou link) para o Lume | `ACTION_SEND` `text/plain` |
| Compartilhar **imagem** para o Lume | `ACTION_SEND` `image/*` |
| Texto marcado em outro app → "Lume" | `ACTION_PROCESS_TEXT` |
| Colar texto ou link / digitar | campo da tela principal |
| Importar imagem da galeria | seletor de imagem |
| Botão "Ler esta tela" na tela inicial | mesma captura, com o app em primeiro plano |

Cada ação nova invalida as anteriores (`RequestGate`): uma resposta atrasada de uma pesquisa antiga nunca aparece por cima da atual.

## 5. Captura e privacidade da imagem

- A captura acontece **só após o toque** e a autorização do Android (MediaProjection). Gera **uma** imagem em `cacheDir`.
- O arquivo temporário (`lume-capture-*.png`) é **apagado** logo após o OCR (`MainActivity.kt`, bloco `finally`).
- A gotinha (mascote) é um serviço em primeiro plano de tipo `specialUse`; o texto declarado no manifesto diz que não faz captura nem
  monitoramento por conta própria.
- O histórico local guarda **somente texto da pesquisa e resultado**; imagens e texto bruto do OCR nunca são salvos nele
  (`ResearchHistory.kt`). O rascunho digitado é apagado ao sair do app.

## 6. Endereço da página (serviço de acessibilidade)

Arquivo: `capture/PageUrlService.kt` + `res/xml/page_url_service.xml`.

- **Por quê:** o Chrome corta o endereço na barra, e o OCR só enxerga o que está desenhado. O texto interno da barra
  (`com.android.chrome:id/url_bar`) vem **inteiro** pela árvore de acessibilidade. Verificado no emulador: 148 caracteres para uma
  matéria do g1, com a barra mostrando `g1.globo.com/mundo/uc`.
- **Quando lê:** uma única vez, no toque em "Ler esta tela" do pop-up, **antes** da tela de permissão de captura (ainda é o navegador
  que está em primeiro plano).
- **O que enxerga:** só os pacotes listados (Chrome, Chrome Beta, Samsung Internet, Firefox, Edge, Brave, Opera). Não observa eventos
  (`onAccessibilityEvent` vazio) e não guarda histórico. O endereço lido vale por 2 minutos e é entregue uma única vez.
- **Validação do endereço:** sem espaços, com domínio; texto digitado na barra não vira link. Endereço **só com domínio** (feed ou página
  inicial) não conta como link de matéria.
- **Ativação:** o usuário liga em Configurações › Acessibilidade; na primeira abertura o app guia a configuração ("Ative o Lume completo") e abre as telas do Android; há também a seção "Leitura completa" em Ajustes. O serviço aparece no Android como "Lume (leitura completa)".
- **Verificado:** Chrome (g1). Os identificadores dos outros navegadores estão no código mas **não foram testados**.

## 7. OCR e leitura da matéria

Pasta `ocr/`. Biblioteca: **ML Kit Text Recognition 16.0.1** (reconhecimento no aparelho, sem enviar a imagem).

1. **Blocos** (`MlKitBlocks.kt`, `OcrBlock.kt`): cada linha do OCR mantém posição (caixa) e confiança.
2. **Layout** (`ArticleLayoutAnalyzer.kt`): região de leitura por geometria; campo desconhecido fica ausente em vez de ser chutado.
3. **Leitura** (`ArticleReader.kt`): atribui um **papel** a cada bloco (cabeçalho/navegação, título, subtítulo, corpo, assinatura,
   data, rodapé…) e extrai, **com a linha de OCR que sustenta cada campo**:
   título, subtítulo, corpo, veículo (rótulo "Fonte:", domínio exibido, ou cabeçalho como possibilidade), autor (assinatura), data,
   endereço exibido. Campo sem evidência fica nulo.
4. **Metadados** (`ArticleMetadataExtractor.kt`, `BylineParser.kt`): autor individual, veículo, data de publicação, URL exibida.
5. **Integridade** (`OcrIntegrity.kt`): defesas contra defeito do OCR. Conserta só defeito mecânico (maiúscula no meio da palavra,
   como `asSociado`) e **compara a manchete com o resto do que foi lido** para avisar; não inventa texto. Preserva marcas
   (`iPhone`), links, `@perfis`, `#hashtags` e siglas.
6. **Título cortado:** título visível apenas em parte não é usado como afirmação.

## 8. Escolha da afirmação principal

`ocr/ClaimExtractor.kt`, `ocr/ArticleReader.claimFor(...)`:

- Manchetes não têm ponto final; isso **não** autoriza juntar o corpo à afirmação pesquisada.
- Título sem pontuação final + subtítulo: mantém a fronteira da frase para que a pesquisa avalie cada uma.
- Se não há uma afirmação clara, o campo de pesquisa fica **vazio** e a pessoa escolhe entre as afirmações encontradas.
- Texto com várias afirmações é dividido; cada uma é pesquisada e avaliada **separadamente**, e a tela avisa que o Lume não
  classifica a notícia inteira.

## 9. Esclarecimento antes da pesquisa ("O que você quer saber?")

`research/Disambiguate.kt` — sem modelo de linguagem; só padrões estruturais:

- termo com vários sentidos ou nome compartilhado (sentidos obtidos da **Wikipédia**);
- característica × aparência em entradas telegráficas ("substantivo + adjetivo perceptivo");
- pergunta geral × episódio específico (ação no passado sem sujeito nomeado e sem data).

Não há lista de assuntos. Ambiguidades de tempo, local ou categoria **não** são detectadas (limite declarado no código).

## 10. Interpretação da afirmação (por regras, sem modelo)

`research/Interpret.kt`, `Claim.kt`, `ClaimContext.kt`, `Text.kt`:

- entidades (nomes próprios, siglas resolvidas pelo próprio texto da matéria: "MP" ↔ "medida provisória");
- termos do acontecimento; **números, unidades, períodos, datas** (data do fato ≠ data de publicação); **negação**;
- **tipo da afirmação**: contagem, quantidade por período, fato histórico (tipos "estáveis", que priorizam referências), acontecimento
  recente, opinião/previsão (marcada como não verificável);
- **consultas de busca** derivadas da afirmação (várias variações);
- palavras que o próprio texto usa para o mesmo assunto (variação de redação) vêm da matéria lida, **não** de listas fixas de temas,
  veículos ou pessoas.

## 11. Fontes consultadas

`research/Sources.kt`, `Net.kt`. **Nenhuma chave de API.** São conectores de RSS/HTML/API pública:

| Fonte | Uso | Cache |
|---|---|---|
| Google Notícias (RSS) | notícias recentes | 10 min |
| Bing Notícias (RSS) | notícias recentes | 10 min |
| Bing Web (RSS) | páginas gerais; busca restrita por domínio (`site:`) | 30 min |
| Wikipédia (API) | sentidos de termos e resumo para fatos estáveis | 6 h |
| Páginas das fontes (HTML) | leitura do corpo | 1 h |

- Para fatos "estáveis" (contagem, quantidade/período, fato histórico) a busca prioriza Bing Web, Bing Notícias e Wikipédia.
- Cada falha de fonte é registrada **separadamente** de "sem resultados" (`SourceStatus`), e aparece em "Como a pesquisa foi feita".
- Orçamento total da pesquisa: **45 s**. Cada pedido tem prazo e limite de bytes (padrão 8 s e 1,5 MB; páginas: 6 s e 1,2 MB).
- Identificação: User-Agent `Mozilla/5.0 (compatible; LumeApp/0.1; uso educacional)`; a Wikipédia recebe User-Agent descritivo conforme a
  política dela.
- **Atenção (limite real):** o app **não** consulta `robots.txt` nem aplica limite de 1 pedido/segundo por domínio. Essa conduta existe
  só no coletor usado na calibração (`ml/sinal_texto/coletar_noticias.py`). Em uso real o app lê poucas páginas por pesquisa
  (no máximo 8, mais a da própria matéria), mas isso **precisa ser tratado antes de qualquer publicação**.
- O Google Notícias entrega links de redirecionamento (`news.google.com/rss/articles/...`), que não são a página. O app prefere o
  endereço direto da mesma matéria vindo de outra fonte; se só houver o redirecionamento, a página não é lida (ver seção 16).

## 12. Leitura das páginas e prévia

`ResearchService.enrich(...)`:

- até **8 páginas**, no máximo 2 por grupo de republicações, ordenadas por similaridade/relação;
- lê em paralelo com janela de **3,5 s**; se ao menos metade respondeu, segue com o que chegou; senão espera até **6 s**.
  Quem não chegou fica com o trecho do buscador, e a tela diz que a base foi só o trecho;
- **Prévia**: antes de ler as páginas o app entrega o resultado só com título e trecho. Um teste garante que o resultado final é
  **idêntico** com e sem prévia (`previewDoesNotChangeFinalResult`);
- conteúdo suspeito de instrução a sistemas automáticos dentro da página é **ignorado** e sinalizado (`injectionDropped`);
- página com corpo menor que 500 caracteres é considerada sem texto utilizável.

## 13. Agrupamento, independência e a própria matéria

`research/Relevance.kt`, `Independence.kt`:

- **Republicações**: itens de título/trecho quase igual viram um grupo; cópias da mesma matéria **não** contam como fontes distintas.
- **Independência** (`independenceOf`): compara o endereço canônico, o veículo (nome e domínio) e o título com a matéria importada:
  - `PROPRIA`: mesmo endereço, ou mesmo veículo e mesmo título;
  - `MESMO_VEICULO`: outra publicação do mesmo veículo;
  - `REPUBLICACAO`: mesmo título em outro veículo, ou crédito explícito ao veículo da captura.
  Veículos **diferentes** com títulos parecidos continuam independentes.
- A própria matéria e as republicações **saem** das fontes exibidas (aparecem só como informação).
- **Corpo da própria matéria para o sinal** (`readOwn`): ordem — (1) link completo lido pelo sistema; (2) candidato da busca que é a
  própria matéria; (3) busca `site:domínio "título"` quando o domínio está visível; (4) sem nada disso, o aviso de texto curto.

## 14. Comparação estruturada do acontecimento (a decisão principal)

`research/EventFrame.kt` (maior módulo), `Relation.kt`, `Relevance.kt`:

A busca e os modelos **só encontram candidatas**; a decisão de "mesmo acontecimento" vem das regras:

- **quem agiu** e em que **papel** (autor × alvo; papéis trocados viram "outro acontecimento");
- **qual ação** e **em que etapa** (proposta ≠ decisão ≠ entrada em vigor; "mandar + infinitivo" etc.);
- **sobre o quê**, **quando** (data do fato ≠ data de publicação), **números, prazos e negação**;
- as classes de ação são vocabulário geral do português jornalístico; **nenhuma regra conhece veículos, pessoas ou manchetes
  específicas**;
- cada decisão guarda os **trechos literais** da matéria e da fonte que a sustentam.

Categorias internas: `direto` (mesmo fato), `incerto`, `anterior`, `contexto`, `descartado` (outro acontecimento) e
`nao_independente`. Na tela: "Mesmo fato", "Incerta", "Anteriores", "Contexto" e "Outro acontecimento".

Sem ação reconhecida na frase, não há comparação estruturada: a triagem é por termos em comum e a página só pode **promover** a fonte
pelo que trata como assunto principal (abertura/resumo), não por menções no corpo.

## 15. Modelos de IA em uso

Todos rodam **localmente** (computador do desenvolvedor, acessado pelo app em `127.0.0.1` via `adb reverse`) ou no aparelho. Nenhum
envia texto para serviço de IA em nuvem e nenhum é pago.

### 15.1 Similaridade de sentido — `paraphrase-multilingual` (Ollama)
- `research/LocalSemanticMatcher.kt`. Devolve similaridade 0–1 entre a afirmação e cada título.
- **Serve para ordenar candidatas** (quais páginas ler primeiro) e, na comparação estruturada, só para decidir se o assunto é
  equivalente quando quem agiu e a ação já coincidem. Não classifica relação nem decide veracidade.

### 15.2 Modelo de pares "mesmo fato" — MPNet ajustado + BERTimbau ajustado + regressão logística
- `research/PairModel.kt`; servidor `lume/ml/integracao_modelo_matheus/servidor_bert.py` (porta 8765); `lume-mpnet-ajustado` no Ollama.
- **Entradas** (por par afirmação × título da fonte): (1) cosseno do MPNet ajustado; (2) sobreposição lexical (Jaccard de palavras de 3+
  letras); (3) nota do BERTimbau ajustado. Regressão logística sobre as três (pesos em `pesos_app.json`).
- **Cortes** (escolhidos só em treino/validação dos 1.650 pares da v3; o conjunto de teste foi lido uma vez):

  | Uso | Corte |
  |---|---|
  | Promover para "Incerta" quando a regra não confirmou | 0,783 (sem ação reconhecida) · 0,86 (com ação reconhecida) |
  | **Confirmar** "Mesmo fato" (selo "Confirmado pelo modelo de linguagem") | 0,90 (sem ação) · 0,93 (com ação) |
  | Aviso "confira a fonte" quando a regra disse "mesmo fato" | nota abaixo de 0,20 (a fonte **não** é rebaixada) |

- **Papel:** só **promove** ou **avisa**; nunca rebaixa nem decide veracidade. A tela informa quando o modelo participou da decisão.
- **Medidas (desenvolvimento, 1.398 pares, teste não lido):** regra sozinha — recall 48,6%, falso positivo 7,6%, precisão 81,0%;
  regra + modelo — recall 54,6%, falso positivo 8,1%, precisão 81,8%. Só na validação (fora do ajuste da regressão): recall 40,0% → 44,6%,
  falso positivo igual (7,8%).
- **Medida no teste** (248 pares, 26 histórias, `RESULTADOS.txt`): o modelo sozinho achou mais fontes do mesmo fato que a regra
  no mesmo nível de erro; intervalos de confiança largos por causa das 26 histórias.
- **Limite observado em notícias ao vivo:** o modelo confunde **mesmo assunto em outra etapa, data ou lugar** com o mesmo fato
  (caso do Flamengo no STF); por isso os cortes de confirmação são altos.
- Sem `lume.pairModel.url` configurado, o app roda sem este modelo.

### 15.3 Sinal do texto — SVM treinado no FakeRecogna
Detalhado na seção 16.

### 15.4 OCR — ML Kit (no aparelho)
Seção 7.

## 16. Sinal do texto (estilo da escrita)

Arquivos: `lume/ml/sinal_texto/`, `research/TextSignal.kt`, `ResearchResults.kt` (`TextSignalCard`, `SignalPill`, `StyleScale`).
Documento próprio: `lume/docs/SINAL-DO-TEXTO.md`.

**O que é.** Um modelo `TfidfVectorizer` (1–2 palavras, 30 mil termos, `min_df=2`) + `LinearSVC(C=1)` treinado no **FakeRecogna** sobre o
texto **lematizado** (coluna `Noticia`). Origem: `modelo_fakerecogna_svm.pkl` (SHA-256 `eb65fa10…999866a`). Classe 0 = falsa
(boato/checagem), classe 1 = verdadeira (notícia comum), conferido com os dados.

**Como roda.**
1. O `.pkl` foi lido com um carregador restrito (aceita só classes de scikit-learn/numpy/scipy/joblib) e exportado para
   `modelo_sinal.json` (pesos, vocabulário, idf).
2. O servidor local (`servidor_sinal.py`, porta 8766) lematiza com **spaCy `pt_core_news_sm`**, calcula TF-IDF e a distância ao
   hiperplano em Python puro. Paridade com o scikit-learn: diferença máxima de 9e-8 em 1.500 textos.
3. O servidor **não grava o texto**.

**O texto que vai ao modelo:** o corpo da matéria lida (com título) ou o texto colado se for longo; **ou, se a entrada é curta, o corpo da
própria matéria** achada pela pesquisa/endereço (seção 13). Guardas: mínimo de **60 palavras**; título solto, frase curta, link e texto
todo em maiúsculas **não** recebem sinal.

**O que a tela mostra.** Uma **faixa** (nunca "é falso" nem percentual): "tende a lembrar desinformação", "sem sinal claro" ou
"tende a ser de notícia comum"; a posição numa escala de −1,5 a +1,5; e as palavras que mais pesaram.

**Faixas (`faixas.json`, calibradas em 14/10/2026).**
Distância ao hiperplano; positivo = estilo de notícia. Textos de 150+ palavras: "desinformação" abaixo de −0,75 e "notícia" a partir de
+0,30. Textos curtos (60–149 palavras): −1,00 e +0,30.

| Conjunto de calibração | n | "desinformação" | "notícia comum" |
|---|---|---|---|
| Notícias reais do dia (Bing Notícias + página, 29 domínios) | 81 | 2,5% (IC95% 0,7%–8,6%) | 63% |
| as mesmas, cortadas em 100 palavras | 81 | 1,2% | 30% |
| Boatos (FakeTrueBr, coluna `fake`) | 329 | 69,6% | 0,6% |
| FakeRecogna classe 0 (lematizado) | 800 | 80,4% | 0,7% |
| FakeRecogna classe 1 (lematizado) | 800 | 8,4% | 75,9% |

**Cruzamento com as fontes.** Se o texto cai em "desinformação" e 3 ou mais veículos independentes relatam o mesmo fato, a tela avisa
que o estilo pode vir de rede social ou de um boato citado. Se alguma fonte é de agência de checagem (Aos Fatos, Lupa, Comprova, Estadão
Verifica, Fato ou Fake, Boatos.org, E-Farsas, AFP, Reuters, BBC, UOL Confere), aparece "Já checado por…".

**Verificado com a página da própria matéria (14/10/2026):** 7 manchetes curtas reais do dia (g1, CNN Brasil, CartaCapital, O Globo,
UOL) tiveram a página aberta e o sinal calculado sobre 259 a 577 palavras; 5 caíram em "notícia comum" e 2 em "sem sinal claro". Todas
de veículos grandes: **não** é amostra de sites pequenos.

**Limites que precisam ser ditos junto com o recurso.**
- Mede **estilo/gênero**, não verdade: textos de checagem do g1 foram marcados "desinformação" em 84% dos casos.
- Entre bases diferentes o desempenho caiu a perto do acaso (40%–53%, análise de 07/10).
- Calibração com **um dia** de notícias (81 textos); o lematizador não é o do dataset.
- O "91% de acerto" do teste de paridade é sobre dados do próprio FakeRecogna (vistos no treino) e **não** é estimativa de desempenho.
- Posição de textos extraídos de páginas com menus/rodapés não foi medida contra texto limpo.
- A origem e a licença do `modelo_sinal.json` precisam ser confirmadas com a equipe antes de publicar.

## 17. Síntese do detalhe e contexto

`research/Evidence.kt`, `Context.kt`:

- Só compara **formatos estruturados**: quantidade + unidade (+ categoria e período) e o ano de um fato. Outros tipos ficam
  "sem comparação" e a tela diz isso, em vez de concluir.
- Situações: `apoiam`, `contradizem`, `insuficiente`, `sem_comparacao`, `nao_verificavel` (opinião/previsão), com a frase explicativa e
  o trecho de cada fonte. Nunca usa **contagem de resultados, reputação ou ausência de notícias** como evidência.
- Com negação na afirmação, `apoiam` e `contradizem` se invertem.
- **Contexto**: só entra uma frase que (1) trata da entidade, (2) cita a propriedade perguntada e (3) explica o dado (definição,
  método, período ou condição), com fonte. Sem frase que cumpra tudo, não há contexto.

## 18. Tela de resultados e demais telas

`ResearchResults.kt`, `LumeApp.kt`, `ReadingSummary.kt`, `ui/`:

- **Resumo** do que foi lido (veículo, autor, data) e a afirmação pesquisada.
- **Barra de proporção** e seções por relação com filtros: Mesmo fato, Incerta, Anteriores, Contexto, Outro acontecimento.
- **Cartão de fonte** compacto: veículo (avatar), título, data relativa, tipo de fonte, quanto do conteúdo foi lido
  (página / trecho do buscador / só título), alertas.
- **"Por que esta relação?"**: folha com quem, ação, assunto, data, números e os **trechos literais** que sustentam a decisão.
- **Selos**: "Confirmado pelo modelo de linguagem", "Provável mesmo fato (modelo de linguagem)", aviso "confira a fonte".
- **Sinal do texto**: pílula e cartão com a escala de estilo (três zonas, marcador animado) e termos que pesaram.
- **Já checado por…** quando há agência de checagem entre as fontes.
- **"Como a pesquisa foi feita"**: fontes consultadas e falhas por fonte, etapas e motivos de cada decisão.
- **Ajustes**: animações, salvar pesquisas/rascunho, ativar/desativar mascote, histórico local, ligar a leitura completa.
- **Mascote**: pisca e se move; abre o painel "Posso ajudar?" com "Ler esta tela", "Abrir análise" e "Desativar mascote".

## 19. Requisitos (derivados do comportamento do app)

> Derivados do código e do comportamento verificado; **o grupo deve confirmar e ajustar** antes de versionar como documento oficial.

**Funcionais**
- RF1. Receber conteúdo por: pop-up do mascote, compartilhamento de texto/link/imagem, texto marcado, colagem e importação de imagem.
- RF2. Ler imagem por OCR no aparelho e identificar título, subtítulo, corpo, veículo, autor, data e endereço, com a evidência de cada campo.
- RF3. Escolher a afirmação principal; pedir escolha quando houver mais de uma ou nenhuma clara; dividir entradas com várias afirmações.
- RF4. Esclarecer ambiguidades estruturais antes de pesquisar.
- RF5. Buscar fontes em Google Notícias, Bing Notícias, Bing Web e Wikipédia, e ler a página das mais relevantes.
- RF6. Excluir a própria matéria e republicações; agrupar cópias.
- RF7. Classificar cada fonte quanto ao acontecimento (mesmo fato, incerta, anterior, contexto, outro) com trechos literais.
- RF8. Comparar detalhes estruturados (números, unidades, períodos, anos) e informar quando não for possível comparar.
- RF9. Usar modelos locais como apoio (ordenação, promoção, aviso), sem decidir veracidade.
- RF10. Mostrar o estilo da escrita (faixa, posição, termos) quando houver 60+ palavras, sem afirmar falsidade.
- RF11. Indicar fontes de agências de checagem.
- RF12. Salvar histórico local opcional e permitir apagá-lo.
- RF13. Ler o endereço da página no navegador, sob ativação do usuário, para abrir a matéria própria.

**Não funcionais**
- RNF1. **Privacidade:** OCR no aparelho; imagem apagada após leitura; modelos locais; histórico só no aparelho; tráfego em texto
  claro permitido somente para `127.0.0.1` e `10.0.2.2` (`network_security_config.xml`).
- RNF2. **Sem custo e sem chave de API** para buscar e para os modelos.
- RNF3. **Desempenho:** prévia em ~2,5–3,7 s e resultado final em ~5–10 s medidos no emulador (seção 21); orçamento máximo de 45 s.
- RNF4. **Resiliência:** falha de fonte, página ou modelo não derruba a pesquisa; cada falha é registrada e mostrada.
- RNF5. **Transparência:** toda decisão guarda o trecho e a regra; a tela mostra a base de leitura (página, trecho, título).
- RNF6. **Segurança do conteúdo:** instruções escondidas em páginas são ignoradas; links são validados e endereços internos bloqueados
  (`UrlGuard`); o `.pkl` é lido por carregador restrito.
- RNF7. **Neutralidade de linguagem:** nunca "verdadeiro/falso"; usa "tende a…" e "sem sinal claro".
- RNF8. **Consistência:** só a ação mais recente exibe resultado (`RequestGate`); a prévia não altera o resultado final.
- RNF9. **Compatibilidade:** `minSdk 24`, `targetSdk 37`; validado no emulador API 37 (Windows e macOS).

## 20. Tecnologias e dependências em uso

| Área | Tecnologia |
|---|---|
| App | Kotlin, Jetpack Compose (Material 3), corrotinas |
| Rede | OkHttp 4.12 (DNS seguro, bloqueio de endereços internos), Gson 2.10 |
| OCR | ML Kit Text Recognition 16.0.1 |
| Serviços do sistema | MediaProjection, overlay (`SYSTEM_ALERT_WINDOW`), serviço em primeiro plano, serviço de acessibilidade |
| IA local | Ollama (`paraphrase-multilingual`, `lume-mpnet-ajustado`), servidor Python com BERTimbau ajustado, servidor Python com SVM + spaCy |
| ML (treino/avaliação, fora do app) | scikit-learn, pandas, spaCy `pt_core_news_sm`, scripts em `lume/ml/` |
| Testes | JUnit (unitários), teste instrumentado Compose |

## 21. Desempenho medido

- **Medição de 09/10/2026** (emulador, Mac, 5 pesquisas reais; `lume/docs/MEDICOES-DESEMPENHO.md`): média interna de 9,5 s → 7,0 s e
  do toque ao resultado de 11,8 s → 9,0 s depois de paralelizar modelos, limitar a espera de páginas e antecipar a busca.
- **Prévia** 2,1 a 3,0 s após o início da busca; resultado final 6,5 a 7,1 s nessa rodada.
- **Rodada de 14/10/2026** (7 manchetes, com sinal e leitura da própria matéria): resultado entre 4,8 e 10 s. O sinal responde em 25–165 ms e
  é aguardado só no fim, com limite de 1,5 s, portanto não soma tempo.
- A carga da máquina muda os tempos (houve rodadas isoladas bem mais lentas, não reproduzidas); compare sempre na mesma sessão.

## 22. Testes e verificação

- **214 testes unitários, 0 falhas, 1 ignorado** (14/10/2026): leitura do OCR (`ocr/`), pesquisa e relações (`research/`), pares difíceis
  e mesma história (`HardPairs`, `SameEventPairsTest`), comparação com versão anterior, modelo de pares, sinal do texto, endereço da página,
  mascote (`mascot/`).
- **Web simulada** (`FakeWeb.kt`): os testes de pesquisa não dependem da internet.
- **Teste instrumentado** com capturas de exemplo.
- **Paridade do sinal** contra o scikit-learn (9e-8). **Calibração** com dados coletados do dia.
- **Verificação ao vivo**: logs `LumeResearch` (tempos por etapa, decisões do modelo, leitura da própria matéria, sinal).

## 23. Como ligar o ambiente local

- Tudo de uma vez: `bash lume/ml/iniciar_modelos_locais.sh` (Ollama com os dois modelos aquecidos, servidor BERTimbau na 8765, servidor do
  sinal na 8766 e `adb reverse` das portas 11434, 8765 e 8766).
- `local.properties`: `lume.ollama.url`, `lume.ollama.embedModel`, `lume.pairModel.url`, `lume.pairModel.embedModel`,
  `lume.textSignal.url`. Sem uma linha, o recurso correspondente fica desligado e o app segue sem ele.
- Os **pesos dos modelos não vão para o Git**; o repositório guarda só os identificadores.

## 24. Principais desafios (do que foi enfrentado no código e nas medições)

1. **Mesmo assunto ≠ mesmo fato:** fontes que tratam de outra etapa, data ou lugar parecem iguais ao modelo; exigiu comparação por
   papéis, ação, etapa e data, e cortes altos para o modelo confirmar.
2. **Erros de OCR** em manchetes, assinaturas e marcas.
3. **Latência:** 9–14 s no início; reduzida com paralelismo, janela de espera de páginas, prévia e busca antecipada.
4. **Fontes sem API estável:** RSS/HTML podem mudar ou falhar; cada falha é isolada e registrada.
5. **Link opaco do Google Notícias**, que impede ler a matéria própria quando é a única origem.
6. **Texto curto não permite medir estilo:** resolvido lendo a página da própria matéria (endereço pelo sistema, busca ou domínio).
7. **O modelo do sinal reconhece gênero, não verdade:** tratado como "estilo", nunca como veredito, com cruzamento de fontes.
8. **Generalização fraca entre bases** (40%–53% entre datasets): o sinal não decide nada e não altera a classificação das fontes.
9. **Endereço cortado pelo navegador:** resolvido pela árvore de acessibilidade, com permissão explícita do usuário.

## 25. Limitações conhecidas (resumo honesto)

- Não verifica fatos e não diz se algo é verdadeiro ou falso.
- Calibração do sinal com 81 notícias de um dia; amostra de veículos grandes.
- Sem `robots.txt` nem limite por domínio no app (seção 11).
- Depende de serviços locais (Ollama e dois servidores Python) para os modelos; sem eles o app funciona só com as regras.
- Validação feita em emulador; **aparelho físico não validado**. No emulador usado nas verificações automatizadas o OCR não concluiu
  (módulo do ML Kit ausente); o teste ponta a ponta do pop-up (endereço → página → sinal) foi feito pelo usuário, e o log dele confirmou
  423 palavras lidas e o sinal calculado.
- Endereços de barra de navegadores diferentes do Chrome não foram verificados.
- Páginas com paywall ou texto montado por JavaScript não são lidas.
- Origem e licença do modelo exportado do sinal pendentes de confirmação.

## 26. O que **não** faz parte do app (para evitar confusão)

- O classificador treinado no **LIAR2** (inglês) é trabalho de pesquisa do repositório e **não** é usado pelo app.
- O modelo FactNews foi descartado.
- Servidor Node e prévia web do app antigo não fazem parte do protótipo atual.

## 27. Mapa de arquivos

| Assunto | Caminho (relativo a `lume/prototypes/ocr-native-android/app/src/main/java/com/example/lumeocrtest/`) |
|---|---|
| Entrada e telas | `MainActivity.kt`, `LumeApp.kt`, `ReadingSummary.kt`, `ResearchResults.kt`, `AnalysisState.kt` |
| Mascote e captura | `mascot/`, `capture/` |
| Endereço da página | `capture/PageUrlService.kt` |
| OCR e leitura | `ocr/` |
| Pesquisa | `research/ResearchService.kt`, `Interpret.kt`, `Claim.kt`, `Sources.kt`, `Net.kt` |
| Comparação | `research/EventFrame.kt`, `Relation.kt`, `Relevance.kt`, `Independence.kt`, `Evidence.kt` |
| Modelos | `research/LocalSemanticMatcher.kt`, `PairModel.kt`, `TextSignal.kt` |
| Histórico | `ResearchHistory.kt` |
| Modelos e calibração (fora do app) | `lume/ml/integracao_modelo_matheus/`, `lume/ml/sinal_texto/`, `lume/ml/iniciar_modelos_locais.sh` |
| Medições e relatórios | `lume/docs/MEDICOES-DESEMPENHO.md`, `lume/docs/SINAL-DO-TEXTO.md`, `lume/docs/RELATORIO-MODELO-MATHEUS-NO-APP.md` |

## 28. Pendências para a entrega (para o grupo preencher)

- Organização/Scrum e papéis de cada integrante (não há registro no código; não foi inventado aqui).
- Datasets e EDA do trabalho de pesquisa, e respostas às guiding questions.
- Confirmação final dos requisitos da seção 19.
- Confirmar origem e licença do modelo do sinal e tratar `robots.txt`/limite por domínio.
- Teste em aparelho físico e com outros navegadores.
