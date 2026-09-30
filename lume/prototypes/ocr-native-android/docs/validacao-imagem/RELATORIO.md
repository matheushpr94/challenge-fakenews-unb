# Análise de notícia por imagem — diagnóstico, correções e validação (29/09/2026)

Escopo: fluxo **importar imagem → ler a matéria → escolher/editar a afirmação → pesquisar → apresentar fontes**.
O classificador de veracidade (desenvolvido à parte) **não** faz parte deste trabalho; nada aqui decide se a notícia é verdadeira.

## 1. Como a falha foi reproduzida

- **OCR real, sem emulador nos testes:** `OcrFixtureDumpTest` (androidTest) grava a saída do ML Kit (blocos, linhas, posição e confiança) de cada captura em `app/src/test/resources/ocr/*.json`. Os testes JVM reproduzem a leitura a partir desses arquivos.
- **Capturas usadas:** Agência Senado (a da falha), BBC (coluna de recomendações, autor individual), e quatro capturas novas feitas no emulador com `WebCaptureTool` (WebView, largura de celular): Agência Brasil (autor em maiúsculas quebrado em duas linhas, crédito de foto, banner de cookies), Agência Câmara (nome do site em letra grande, assinatura institucional), g1 (anúncio e cotações antes do título, título em dois blocos) e g1 rolado (título cortado no topo, anúncio no meio). Mais um recorte da Senado sem título.
- **Antes/depois registrados:** `antes-extracao.txt` (pipeline antigo sobre as mesmas fixtures), `depois-extracao.txt` (papel e motivo de cada bloco), `antes-busca-senado.txt` e `diagnostico-*.txt` (consultas, quantidade por provedor, triagem com motivo, duplicatas, agrupamentos, limite, exibidas e relação).
- **Busca com rede real:** `LiveResearchDiagnostics` (JVM, só com `LUME_LIVE=1`) e o log `LumeTrace` do app no emulador.

## 2. Causa em cada etapa

| Etapa | O que acontecia | Causa geral |
|---|---|---|
| Blocos do OCR | O app descartava a geometria por linha | `runOcr` criava blocos só com texto e caixa do bloco; um parágrafo que contorna a coluna lateral virava uma caixa larga |
| Título | Nome do site virou título (Câmara); título não achado (g1); parágrafo virou título (recorte) | Referência de "letra do corpo" contaminada por títulos de várias linhas; título limitado aos 48% superiores; nenhum teste de "título cortado" |
| Assinatura/data | "Da Agência Senado \| 28/09/2026, 10h51" virou **subtítulo**; veículo e data vazios; autor em maiúsculas quebrado em 2 linhas não reconhecido | Só havia padrões "Por X"/"Publicado em"; "Da X" exigia "em Cidade"; linhas não eram unidas |
| Crédito/legenda/lateral | Legenda e "Proposições legislativas" no corpo | Título de largura total fazia tudo entrar na coluna; sem noção de faixa de imagem |
| Afirmação | Cauda "entenda o vaivém…" na afirmação; cotação do dólar na afirmação (g1) | Afirmação = primeiro parágrafo limpo, sem papéis |
| Interpretação | "em 30 dias" tratado como **contagem** | Toda quantidade com unidade virava "contagem"; prazo não era distinguido |
| Consultas | "MP 30 dias", "MP dias", "quantas dias MP" — sem "bets", "proíbe" | Consultas de quantidade descartam a ação e o objeto |
| Provedores | Funcionavam: a mesma pergunta bem formada no Google Notícias devolve 100 itens de 58 veículos | Não era falha de provedor — o app perguntava errado |
| Relevância | "MP dá 30 dias para EMTU…" (Ministério Público) aceito; "Medida Provisória proíbe apostas…" rejeitado | Sigla ambígua sem resolução; nome + número bastavam; paráfrase não reconhecida |
| Síntese | Fontes irrelevantes em "Informa o detalhe" e frase "As fontes apoiam essa informação" | Motor de quantidades aplicado a um prazo |
| Deduplicação | Títulos quase iguais com "não" ou números diferentes seriam agrupados como cópia | Agrupamento só por semelhança de palavras |
| Interface | Só 2 publicações do mesmo fato visíveis; seção "responde" no topo | `take(2)` e limites de 6 por seção |

## 3. O que mudou (principais arquivos)

- `ocr/ArticleReader.kt` (novo): papéis por bloco — título, subtítulo, assinatura/data, corpo, legenda, crédito de imagem, texto dentro de imagem, coluna lateral, cabeçalho, interface, recomendações, anúncio — cada um com motivo; título cortado; afirmação sem cauda editorial; título-pergunta ou ausente ⇒ **pede escolha**.
- `ocr/BylineParser.kt` (novo): "Por Nome, Veículo — Cidade", "Da Organização \| data", "NOME - REPÓRTER DA ORG", "Publicado em…", "Atualizado há…". Assinatura institucional ≠ autor.
- `ocr/MlKitBlocks.kt` (novo): conversão única do ML Kit com linhas e confiança (app e testes).
- `ocr/OcrBlock.kt`, `ocr/ArticleMetadata.kt`: geometria por linha; origem e evidência de cada campo; créditos e legendas.
- `research/ClaimContext.kt` (novo): contexto da matéria; siglas resolvidas pelo próprio texto (iniciais ou "Nome (SIGLA)"); vocabulário repetido pela matéria; prazo ≠ contagem; etapa do ato (proposto, aprovado, decidido, vigente, revogado…).
- `research/Relation.kt` (novo): relação de cada fonte — mesmo fato / informação diferente / contexto / anterior / outro — e o que diz sobre o detalhe (cita / diferente / não cita), com o **trecho real** e se a base foi página, resumo do buscador ou só título.
- `research/Interpret.kt`, `Claim.kt`, `Relevance.kt`, `ResearchService.kt`, `Sources.kt`: consultas específica/acontecimento/variação/detalhe/parte; moldura inicial ("Em meio a…,") fora dos termos; partes coordenadas ("A e B"); oração secundária ("…MP **que** proíbe") e foco no título; mês diferente = outro período; negação só junto da ação; releitura após abrir a página; deduplicação que não junta títulos com negação ou números diferentes; IA local **consultiva**; rastreamento `ResearchTrace`.
- `MainActivity.kt`, `ReadingSummary.kt` (novo), `ResearchResults.kt`, `AnalysisState.kt`: telas "O que conseguimos ler nesta imagem?", "O que não entrou na matéria", "O que vamos pesquisar?" (edição, frases alternativas, aviso de sujeito vago), resumo sem veredito, "Quais publicações falam deste mesmo acontecimento?", "Informação diferente…", "Contexto e desdobramentos", "Publicações anteriores", "O que ainda não sabemos?", "Como a pesquisa foi feita".

### Como a expansão de termos evita falsos positivos
1. Só usa formas **encontradas no texto da própria matéria** (sigla por extenso, palavras repetidas); sem matéria, não há expansão.
2. Uma palavra da matéria substitui **no máximo um** termo e só quando a ação já coincide.
3. Consultas de variação vão só ao Google Notícias (controle de volume).
4. Toda fonte ainda passa pelos mesmos filtros: ator/sigla + ação + objeto, foco no título, oração secundária, período, negação junto da ação.
5. A IA local não derruba evidência lexical forte e não promove sozinha.

## 4. Casos de regressão (antes → depois)

Extração (fixtures reais do ML Kit + casos sintéticos; `ArticleReaderRegressionTest`, `OcrArticleRegressionTest`):

| Caso | Antes | Depois |
|---|---|---|
| Senado — veículo | não identificado | Agência Senado (lido em "Da Agência Senado\| 28/09/2026, 10h51") |
| Senado — autoria | vazio; assinatura virava subtítulo | "publicado por Agência Senado" (institucional, sem autor) |
| Senado — data | não identificada | 28/09/2026, 10h51 |
| Senado — crédito "Anna Tolipova" | não distinguido | crédito de imagem, não autor |
| Senado — legenda e "Proposições legislativas" | no corpo | fora do corpo (legenda / coluna lateral) |
| BBC — coluna "Principais notícias/Leia mais" | fora do corpo | fora do corpo (coluna lateral) |
| BBC — afirmação | com "entenda o vaivém…" | "Fux derruba decisão de Dino sobre posts de Nossa Senhora Aparecida" |
| Agência Brasil — autor em maiúsculas em 2 linhas | não identificado | Marcelo Brandão; veículo Agência Brasil; local Brasília |
| Agência Brasil — "© PAULO PINTO/AGÊNCIA BRASIL" e cookies | — | crédito; banner ignorado |
| Câmara — título | "AGÊNCIA CÂMARA DE NOTÍCIAS" (também a afirmação) | título real; assinatura institucional Agência Câmara + data |
| g1 celular — título em 2 blocos após anúncio/cotação | sem título; afirmação com "R$ 5,910 -0,5%" | título completo; autor, veículo g1, data, "atualizado há 7 horas" |
| g1 com título cortado | subtítulo usado como título | "título cortado (…agosto)" ⇒ pede escolha |
| Recorte sem cabeçalho | parágrafo inteiro como título/afirmação | título/veículo/autor/data não identificados ⇒ pede escolha |
| Sem autor visível (sintético) | — | autor e veículo nulos, data preservada |
| Nome sob a foto (sintético) | — | crédito, não autor |
| "Leia também" + menu (sintético) | — | fora do corpo; "Da Redação" não vira veículo |
| Coluna lateral com assunto próximo (sintético) | — | lateral |
| Título-pergunta (sintético) | — | pede escolha |

Pesquisa (rede simulada, nomes fictícios; `CoverageAndRelationTests`, `ClaimInterpretationTests`):

| Caso | Resultado esperado e obtido |
|---|---|
| Prazo "em 30 dias" | detalhe (prazo), não contagem |
| Dois veículos, palavras diferentes | ambos "mesmo fato"; página confirma o prazo com trecho |
| Fonte que contradiz o detalhe (90 dias) | "informação diferente", trecho com 90 dias |
| Título relevante, conteúdo sem o detalhe | mesmo fato, "não menciona o detalhe" |
| Homônimo "MP dá 30 dias para prefeitura…" | descartado |
| Oração secundária / outro aspecto no título | contexto |
| Matéria antiga do mesmo tema | não é mesmo fato |
| Republicação idêntica | veículos continuam listados no cartão |
| Negação longe da ação / junto da ação | mesmo fato / não mesmo fato |
| Títulos com "não" | não agrupados como cópia |
| Parte genérica ("O texto foi publicado…") | não vira mesmo fato; sujeito vago detectado |
| Mês diferente | outro período (contexto) |
| IA errando de propósito | não derruba evidência forte nem promove desdobramento |
| Sem evidência / provedor fora / todos fora | "insuficiente" / aviso / "erro" |

## 5. Testes, lint e compilação (neste PC)

- `gradlew :app:testDebugUnitTest` — **124 testes, 0 falhas** (1 ignorado: diagnóstico com rede real, só roda com `LUME_LIVE=1`).
- `gradlew :app:connectedDebugAndroidTest` no `Medium_Phone` (API 37) — **12 testes, 0 falhas** (ML Kit real sobre as 7 capturas + interface).
- `gradlew :app:assembleDebug` — ok. `gradlew :app:lintDebug` — **0 erros, 49 avisos** (catálogo de versões, extensões KTX, recursos não usados — mesmos tipos já existentes).

## 6. Buscas reais no emulador (app instalado, importando pelo seletor do Android)

| Captura | Consultas | Recebidos | Repetidos (URL) | Descartados (principal motivo) | Exibidos: mesmo fato / contexto | Veículos no mesmo fato | Detalhe |
|---|---|---|---|---|---|---|---|
| Agência Senado | 10 | 109 | 17 | 22 (poucas palavras em comum; só o nome "MP" coincide) | 17 / 9 | 19 (tela) | 6 citam "30 dias" com trecho da página; 0 diferentes |
| BBC | 9 | 116 | 48 | 18 (só o nome coincide) | 16 / 10 | 15 | sem número a comparar |
| g1 (celular) | 7 | 93 | 8 | 40 (poucas palavras em comum) | 12 / 8 | 12 | outro mês (maio) → contexto |
| Recorte, frase "O texto foi publicado…" | 8 | 90 | 0 | 90 | 0 / 0 | 0 | "evidência insuficiente" + aviso de sujeito vago |
| Câmara (afirmação editada) | 7 | 74 | 5 | 58 | 3 / 4 | 3 | — |

Antes, a mesma captura da Agência Senado gerava 3 consultas sobre "MP … dias", 1 publicação no mesmo acontecimento, 4 fontes irrelevantes como "Informa o detalhe" e a frase "As fontes consultadas apoiam essa informação".

- Falha real de conexão (rede do emulador desligada): todos os provedores com erro de DNS registrado ⇒ "Não foi possível consultar as fontes agora…" + "Tentar novamente"; com a rede de volta a busca concluiu.
- Abrir fonte: o app entregou `https://www.camara.leg.br/…` ao Chrome. O Chrome do emulador está na tela de primeira execução, que exige aceitar os Termos de Serviço — **não aceito em nome do usuário**; a página em si foi carregada no mesmo emulador pelo WebView (captura `camara-bets-pl.png`).

## 7. IA local (Qwen 3.5 4B via Ollama, `adb reverse`)

Medido no emulador: 20–30 s por pesquisa com a comparação ligada, contra 1–5 s só com regras. Na pesquisa da Senado, ela rebaixou um relato do mesmo fato para "outro acontecimento" e promoveu desdobramentos. Por isso ficou **consultiva**: vira aviso quando discorda de evidência forte e só promove fonte à qual falta apenas o ator. Sem o computador conectado, a pesquisa segue sem ela.

## 8. Limitações que restam

- Relações vêm de palavras, números, datas, negação e etapa; não há compreensão semântica completa. Desdobramentos ainda podem aparecer como "mesmo acontecimento" quando o título repete ação e objeto; fontes de instituições diferentes sobre o mesmo indicador (ex.: Serasa × Banco Central) não são separadas.
- "Informação diferente" cobre números/prazos, negação e etapa (proposta × decisão); contradições qualitativas não são detectadas.
- Links do Google Notícias não são abertos (redirecionamento): essas fontes são avaliadas só pelo título, e a tela diz isso.
- Erros do OCR ("crimea") e palavras comuns com maiúscula ("Executivo") pioram a busca; a correção é a edição da afirmação.
- Validado em 7 capturas (quatro veículos com páginas reais, dois assuntos principais). Páginas em modo escuro, texto sobre foto em toda a página, imagens de baixa resolução e colunas duplas de jornal impresso não foram testadas.
- Datas relativas ("há 2 horas") não são convertidas em data.
- A abertura da fonte no Chrome do emulador não foi concluída (termos do Chrome pendentes).
