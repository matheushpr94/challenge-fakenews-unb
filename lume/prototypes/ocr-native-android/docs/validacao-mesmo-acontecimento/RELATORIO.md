# Identificação de fontes do “mesmo acontecimento” — correção e validação (30/09/2026)

Projeto: `lume/prototypes/ocr-native-android`. Emulador `Medium_Phone` (API 37). Capturas de teste: UOL (autoria explícita) e
Estadão (matéria relacionada), ambas fornecidas pela pessoa usuária; mais Agência Senado, BBC e g1 da validação anterior.

**Situação:** os três erros reproduzidos foram corrigidos e verificados em testes, em buscas reais e no emulador. Restam limites
e casos de fronteira (seção 9). O app não deve ser apresentado como pronto para uso geral.

## 1. Erros reproduzidos antes da mudança

Fluxo antigo: triagem por palavras (`assess`) + relação (`relate`). Registros: `antes/live-*.txt` e `antes/pares-antes.txt`.

| Erro | Onde | O que acontecia |
|---|---|---|
| A própria matéria como fonte dela mesma | captura do UOL | O próprio UOL (“Flamengo aciona STF e reforça ação das bets…”) aparecia como **mesmo acontecimento** (tela `00-antes-uol-como-fonte-de-si-mesmo.png`). Na captura do Estadão, o próprio Estadão e a republicação do Terra (título idêntico) também. |
| Relato com outras palavras perdido | captura do UOL | Estadão (“…primeiro clube a **entrar com pedido** no STF contra fim das **casas de apostas**”) e O Antagonista (“Flamengo **vai ao STF** contra proibição das bets”) ficavam em **contexto**; na captura do Estadão, UOL e O GLOBO também. |
| Notícia parecida sobre outro fato aceita | captura do Estadão | “**Bets** entram com nova ação no STF…” (as empresas de apostas, 28/09) e “Flamengo **se posiciona** contra restrições a bets” (nota de 25/09, antes do pedido ao STF) viravam **mesmo acontecimento** depois da leitura da página. |
| Extração | capturas | UOL: autor e veículo nulos (“lgor Siqueira Do UOL, no Rio de Janeiro 29/09/2026 22h03” lido como uma linha só, com “I” trocado por “l”). Estadão: veículo “ELEIÇÕES JORNAL DO CARRO” (menu do site). |

Causas: (1) nenhuma comparação com a matéria importada (URL, veículo, título); (2) decisão por palavras em comum com os termos
da manchete — sinônimos (“aciona” × “entrar com pedido”, “bets” × “casas de apostas”) não contam, e o mesmo tema com outro agente
conta; (3) a página lida podia “promover” a fonte com qualquer frase que repetisse o tema.

## 2. O que mudou

- `research/EventFrame.kt` (novo): quadro do acontecimento em cada frase — **quem agiu** (e papel: agente, alvo, objeto; voz passiva;
  papéis invertidos), **ação** por classes do vocabulário geral do português jornalístico (acionar a Justiça, pedir, declarar,
  propor/anunciar, aprovar, proibir, revogar/suspender, decidir, confirmar/negar, prazo, alta/queda…), **etapa**, **sobre o quê**
  (substantivos do título, subtítulo e abertura da matéria importada), **quando** (data do fato resolvida contra a data de
  publicação: “nesta terça-feira (29)”, “hoje à noite”, “29/9”, “em janeiro de 2024”; mês/ano soltos só quando a própria afirmação
  usa essa precisão), **números/prazos**, **negação**, **origem do dado** em indicadores. Decide uma de cinco relações e guarda os
  trechos literais usados. Nenhuma regra conhece veículo, pessoa, clube ou manchete.
- `research/Independence.kt` (novo): própria matéria (URL canônica, ou mesmo veículo + mesmo título), mesmo veículo, republicação
  (título idêntico com os mesmos números, publicado até 7 dias da matéria, ou crédito explícito “X Conteúdo”, “com informações de X”).
  Títulos apenas parecidos de outros veículos continuam independentes.
- `research/ResearchService.kt`: a triagem por palavras continua servindo para busca e ordenação; a relação final vem da comparação
  estruturada, também depois de ler a página (pode subir ou descer). Nova consulta “evento” (quem + alvo + assunto, ex.:
  “Flamengo STF bets proibição”). Seções: mesmo acontecimento, detalhe divergente, relação incerta, contexto, fatos anteriores,
  não independentes; “outro acontecimento” fica fora, com motivo.
- `research/LocalSemanticMatcher.kt`: o Qwen consultivo foi substituído por `OllamaEmbeddingRanker` (`paraphrase-multilingual`).
- `ocr/BylineParser.kt`, `ocr/ArticleReader.kt`: “l”→“I” no início de nome (OCR), “Nome Do Veículo, no Local” sem separador,
  data colada à linha; logotipo no topo confirmado pela trilha de navegação e fileiras de menu descartadas.
- UI (`ResearchResults.kt`): rótulos das cinco relações, “Por que esta relação?” (quem, ação, assunto, quando, detalhe, com os
  trechos da matéria importada e da fonte), aviso da própria matéria no resumo, seção “Não contam como fontes independentes”.
- Testes: `pairs/pares-dificeis.json` + `SameEventPairsTest`, `HardPairs`, novos testes em `CoverageAndRelationTests`,
  `OcrArticleRegressionTest.uolExplicitAuthorAndEstadaoMasthead` (ML Kit real no emulador). Testes antigos que contavam a própria
  matéria como fonte ou tratavam negação como “contexto” foram atualizados para o comportamento pedido.

## 3. Pares difíceis: antes × depois

40 pares (24 reais, recebidos dos buscadores em 29–30/09; 16 sintéticos, nomes fictícios). **Antes 16/40, depois 40/40.**
Tabela completa: [`tabela-pares.md`](tabela-pares.md). Motivos e trechos de cada decisão: [`depois/pares-depois.txt`](depois/pares-depois.txt).

Atenção: os rótulos são meus e as regras foram ajustadas vendo esses pares; 40/40 não mede desempenho em notícias novas. As buscas
reais das seções 5 e 6 (com títulos que não estavam nos pares) são a verificação independente — e nelas ainda apareceram erros,
que viraram novos pares (`uol-gdf-corte-homonimo`, `uol-df-derrubar`, `uol-folhape-87`).

## 4. Trechos que justificam as decisões (exemplos)

| Par | Relação | Trechos comparados |
|---|---|---|
| UOL × Estadão (só título) | mesmo acontecimento | Captura: “Flamengo **aciona STF** e reforça ação das **bets** contra proibição do governo” · Fonte: “Flamengo é o primeiro clube a **entrar com pedido no STF** contra fim das **casas de apostas** no Brasil” · Quem: Flamengo = Flamengo; ação equivalente; assunto “casas, apostas” (a abertura da captura diz “coro das casas de apostas”); publicado 29/09 = fato “hoje à noite” de 29/09. Aviso: lemos só o título. |
| UOL × Terra | mesmo acontecimento | Página: “O Flamengo se antecipou aos clubes e **acionou o Supremo Tribunal Federal (STF)** para tentar derrubar o veto às bets.” |
| UOL × O GLOBO “Bets entram com nova ação no STF…” | contexto relacionado | Mesma ação (“entram com nova ação” = “aciona”), mesmo assunto, **outro agente** (“Bets”; página: “Entidades que representam empresas de apostas entraram com uma nova ação…”). |
| Estadão × Gazeta Brasil | contexto relacionado | Ação “**se posiciona**” ≠ “entrar com pedido”; página: “O Flamengo criticou nesta sexta-feira (25)…”. |
| UOL × “Governo do DF aciona STF… socorro ao BRB” | outro acontecimento | Mesma ação, outro agente, nenhum substantivo do assunto em comum. |
| UOL × “PGR defende no STF que Flamengo e Sport dividam título de 1987” | outro acontecimento | Agente PGR; Flamengo aparece como objeto; assunto sem relação. |
| UOL × UOL | própria matéria | Mesmo veículo (UOL = uol.com.br) e mesmo título. |
| Estadão × Terra (título idêntico) | republicação | Mesmo título da captura, publicado por outro veículo. |
| “Secretaria… confirma surto” × “…nega surto” | detalhe divergente | “Negação: a fonte nega; a matéria importada afirma”. |
| “…prazo de 30 dias…” × “…dá 10 dias…” | detalhe divergente | “Detalhe: 10 dias × 30 dias”. |
| “Governo… proíbe venda de fogos” × “…propõe proibir…” | contexto relacionado | “Outra etapa: fala de proposta ou anúncio, não da decisão”. |
| “Barragem… transborda” (2026) × mesma manchete com “Em janeiro de 2024…” | fato anterior (contexto) | “Quando: ‘Em janeiro de 2024’ — antes de 29/09/2026”. |
| “Câmara… aprova reajuste de 12% no IPTU” × “IPTU de Vale Claro: o que muda em 2027” | relação incerta | Só título, sem ação: “o texto lido não diz o que aconteceu”. |
| mesma × “Vereadores… aprovam aumento do IPTU” + resumo | mesmo acontecimento | Resumo: “O reajuste de 12% no imposto foi aprovado nesta terça pelos vereadores.” — cita 12%. |

## 5. Buscas reais (rede real, JVM, com o comparador de sentido)

| Captura | Consultas | Recebidos | Repetidos | Fora (outro acontecimento etc.) | Mesmo acontecimento | Incerta | Contexto / anterior | Não independentes |
|---|---|---|---|---|---|---|---|---|
| UOL (antes) | 9 | 92 | 4 | 56 | 3 (incl. o próprio UOL) | — | 8 | — |
| **UOL (depois)** | 10 | 126 | 1 | 74 | **10** (Terra, Brasil 247, O GLOBO, Estadão on MSN, Terra, O Antagonista, O Dia, professorrafaelporcari, ncnews, BNLData) | 2 | 8 | 6 (UOL e outras do UOL) |
| UOL (depois, **sem modelo**) | 10 | — | — | 72 | 10 (mesmos veículos) | 2 | 8 | 6 |
| Estadão (antes) | 8 | 82 | 12 | 45 | 3 (incl. o próprio Estadão, Terra republicação, “Bets entram…”) | — | 8 | — |
| **Estadão (depois)** | 9 | 106 | 18 | 50 | **9** (O GLOBO, Terra, Brasil 247, iG, UOL ×2, O Dia, ncnews, professorrafaelporcari) | 3 | 8 | 6 (Estadão, MSN, Terra republicação) |
| Agência Senado | 10 | 107 | 8 | 41 | 15 | 6 | 8 | 2 (Senado Federal e Jornal de Uberaba com o mesmo título: republicação) |
| BBC | 10 | 138 | 52 | 22 | 20 | 4 | 8 | 3 (outra matéria da BBC; BBC on MSN e Correio Braziliense com o mesmo título: republicação) |
| g1 (inadimplência) | 8 | 113 | 6 | 48 | 10 | 6 | 6 / 4 anteriores (julho, maio) | 6 (g1) |

Fontes rejeitadas na busca do UOL, com o motivo mostrado ao usuário: [`rejeitadas-uol.md`](rejeitadas-uol.md) (ex.: “Governo do DF
aciona STF…” — mesma ação, outro envolvido e outro assunto; “PGR defende… título de 1987” — outro acontecimento com nomes em comum;
“Foragida na Itália, Carla Zambelli… reforça críticas ao STF” — outras pessoas). Registros completos em `depois/live-*.txt`.

## 6. Emulador (Medium_Phone, fluxo real)

1. Importar imagem pelo seletor do Android → leitura: **Veículo UOL**, **Autoria Igor Siqueira**, 29/09/2026 22h03, Rio de Janeiro (`01`).
2. Afirmação “Flamengo aciona STF e reforça ação das bets contra proibição do governo” → Pesquisar (21–25 s, com leitura de páginas).
3. Resumo: “8 outro(s) veículo(s) relataram este mesmo acontecimento… A própria matéria importada (UOL) apareceu na busca e não
   foi contada como outra fonte” (`02`); cartões com relação e trecho (`03`); “Por que esta relação?” com os trechos (`04`);
   “Não contam como fontes independentes” com o UOL (`05`).
4. **Abrir fonte original** → Chrome abriu `terra.com.br/esportes/flamengo/flamengo-pede-ao-stf…` (`06`). Para isso, com sua
   autorização, aceitei os Termos do Chrome no emulador, desliguei o envio de estatísticas, fiquei sem conta e recusei notificações.
5. Captura do Estadão: veículo “Estadão” (logotipo + trilha), sem autor e sem data na captura (`08`); UOL, O GLOBO, Terra, iG, O Dia
   como mesmo acontecimento; o próprio Estadão, a cópia no MSN e a republicação do Terra como não independentes (`07`,
   `depois/emulador-trace-estadao.txt`).

## 7. Modelos locais avaliados (37 pares, CPU/GPU deste PC)

| Modelo (gratuito) | Uso testado | Resultado | Custo | Decisão |
|---|---|---|---|---|
| paraphrase-multilingual-MiniLM-L12 (ONNX int8, 118 MB) | similaridade título × afirmação | 75,7% no melhor limiar (escolhido olhando os próprios pares); aceitou data diferente, proposta×decisão, papéis invertidos, outro local | ~4 ms/texto | não usado |
| multilingual-e5-small (ONNX int8, 118 MB) | idem | 75,7%; perdeu o próprio par UOL×Estadão | ~4 ms/texto | não usado |
| paraphrase-multilingual (mpnet, Ollama, 562 MB) | idem | 81,1% no melhor limiar; **não perdeu nenhum relato do mesmo fato**, mas aceitou 6 fatos diferentes (data, etapa, papéis, local, notas de posição) | ~0,7 s por lote no Ollama | **usado só como auxiliar**: ordena leitura de páginas e, quando quem agiu e a ação já coincidem pelo texto, desempata se o assunto é o mesmo (≥ 0,72 mesmo; < 0,55 outro; senão incerta) |
| mDeBERTa-v3 xnli (ONNX int8, 317 MB) | inferência textual | saída quase uniforme (quantização int8 quebrada; teste de sanidade falhou) | 60 ms/par | descartado |
| multilingual-MiniLMv2-L6 mnli-xnli (428 MB) | inferência textual | 54%: marca “contradição” para pares sem relação; aprovou proposta como decisão | 9 ms/par | não usado |
| qwen3.5:4b (Ollama, já instalado) | classificar o par e citar trecho | 43%: aceitou 21 fatos diferentes; **6 trechos “citados” não existiam no texto** | 0,66 s/par | removido do app |

Conclusão: nenhum modelo decide “mesmo acontecimento” sozinho com segurança; todos confundem proximidade de tema com o mesmo
fato. A decisão ficou na comparação estruturada; o modelo nunca cria agente, ação, data ou trecho, e cada trecho exibido é conferido
como texto recebido. Sem Ollama, a pesquisa segue igual e a tela diz que a comparação de sentido não foi usada (teste
`semanticSimilarityNeverDecidesAlone` e busca real “sem modelo” acima). Script da avaliação: `avaliacao-modelos.py`; números: `avaliacao-modelos.json`.

## 8. Testes, lint e compilação

- JVM: **127 testes, 0 falhas**, 1 ignorado de propósito (diagnóstico com rede real, só roda com `LUME_LIVE=1`). Inclui 40 pares difíceis.
- Instrumentados no emulador: **13/13 OK** (ML Kit real; novo teste do UOL/Estadão).
- `assembleDebug` OK. `lintDebug`: 0 erros, 49 avisos (mesmos tipos de antes; nenhum nos arquivos novos da comparação).
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

## 9. Limitações e erros restantes

- **Vocabulário de ações**: verbos fora dele (“se antecipa”, “engrossa”, “terão crédito”) deixam a ação sem classe; a fonte tende a
  “relação incerta” (ex.: Tribuna PR “Clubes de futebol terão crédito… após proibição das bets”).
- **Foco na abertura**: se o título não traz ação reconhecida, a abertura da página pode decidir. Caso de fronteira: BNLData
  “Flamengo pode perder R$ 400 mi por ano com proibição de bets” foi aceito como mesmo acontecimento pela abertura da página.
- **Indicadores** (g1): a captura não mostra de onde vem o dado; fontes que citam uma instituição ficam com aviso “confira se é o
  mesmo levantamento”. Recordes de institutos diferentes no mesmo mês podem aparecer como o mesmo fato.
- **Apelidos e descrições** (“Rubro-Negro”, “clube carioca”) só são reconhecidos quando a abertura do texto traz o nome.
- **Só título** (Google Notícias): a decisão usa o título e a data de publicação; a tela avisa “Lemos só o título”.
- **Captura sem data** (Estadão): a comparação usa a data de publicação da fonte; mais de 30 dias antes da pesquisa vira incerta.
- **Veículo lido do cabeçalho** é uma sugestão; se o OCR errar o nome, a exclusão da própria matéria depende só do título.
- Contradições qualitativas (sem número, negação, direção ou etapa) não são detectadas.
- O comparador de sentido depende do Ollama no computador (ponte ADB); não roda no celular sozinho.
- Os rótulos dos pares são meus; o conjunto é pequeno (40) e foi usado para ajustar as regras.
