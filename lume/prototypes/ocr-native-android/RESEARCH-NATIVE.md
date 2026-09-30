# Pesquisa nativa no Android (sem servidor)

O fluxo principal do app pesquisa **diretamente pela internet, no aparelho**. Não usa WebView,
localhost, `adb reverse` nem servidor no Mac. Precisa apenas de internet.

```
Imagem → ML Kit OCR (local) → limpeza/metadados → afirmação editável
      → esclarecimento ("O que você quer saber?", só quando a ambiguidade muda a resposta)
      → consultas → Google Notícias / Bing Notícias / Bing Web / Wikipédia (em paralelo)
      → leitura das páginas acessíveis → relevância e agrupamento de republicações
      → comparação por regras → indicação provisória + o que encontramos + contexto + fontes
```

## Onde está cada parte

Pacote `app/src/main/java/com/example/lumeocrtest/research/` (porte em Kotlin do protótipo web em Python):

| Arquivo | Função | Origem no protótipo |
|---|---|---|
| `Text.kt` | normalização, radicais, números, datas (sem `java.time`, minSdk 24) | `textutil.py` |
| `Interpret.kt` | entidades (preserva nomes compostos), termos, números, datas, negações, consultas | `interpret.py` |
| `Claim.kt` | quantidade + unidade + categoria + comparador, tipo da afirmação, negação do valor | `claim.py` |
| `Net.kt` | OkHttp com prazo total, limite de bytes, redirecionamentos revalidados, DNS que recusa IPs internos, cancelamento junto com a corrotina | `netfetch.py` |
| `Sources.kt` | conectores RSS/JSON (XML estrito), leitura de páginas com Jsoup, filtro de instruções embutidas | `sources.py` |
| `Relevance.kt` | direto / anterior / contexto / descartado; republicações; versões móvel e desktop da mesma página | `relevance.py` |
| `Evidence.kt` | comparação de quantidades e anos (entidade, unidade, categoria, período, negação) e indicação | `evidence.py` |
| `Context.kt` | contexto útil: entidade + propriedade + período, frase completa, com fonte | `context.py` |
| `Disambiguate.kt` | "O que você quer saber?" por regras | `disambiguate.py` (sem o modelo) |
| `ResearchService.kt` | orquestração em corrotinas (fora da thread principal), cache com prazo, avaliação por partes | `service.py` |
| `RequestGate.kt` | só a ação mais recente mostra resultado (nova pesquisa, nova imagem, cancelar) | `web/controller.js` |

Interface: `MainActivity.kt` (OCR, edição, pesquisa, cancelar, tentar de novo, esclarecimento,
"Não é isso — adicionar detalhes", "Pesquisando: … Corrigir") e `ResearchResults.kt` (indicação
provisória, "O que encontramos", contexto, fontes que esclarecem o detalhe, "Outras publicações
relacionadas" recolhida). Detalhes técnicos (fontes, falhas, descartes) ficam no Logcat com as tags
`LumeResearch` e `LumeUi`.

## Executar e testar

```sh
cd /Users/aluno1/AndroidStudioProjects/LumeOCRTest
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Depois, Run no Android Studio (emulador ou aparelho com internet). Não é preciso iniciar nenhum servidor.

Testes com rede simulada em `app/src/test/java/com/example/lumeocrtest/research/` (`FakeWeb.kt`,
`ResearchTests.kt`), portados do protótipo, com nomes fictícios: interpretação e consultas, apoio,
contradição, divergência, ausência de evidência, período/categoria/unidade diferentes, conteúdo só
relacionado, número sem a entidade, muitos títulos, data da página × período do fato, negação,
republicações, fato estável × notícias recentes, contexto, ambiguidade, falha/timeout/conteúdo inválido,
URLs inseguras, cancelamento, nova pesquisa e nova imagem durante o processamento, OCR título + texto.

Logs úteis: `adb logcat -s LumeResearch LumeUi`.

## Fontes e condições de uso

- **Google Notícias (RSS)** e **Bing Notícias/Web (RSS)**: não são APIs oficiais para este uso; podem
  mudar, limitar ou bloquear sem aviso. O RSS do Bing declara uso pessoal e não comercial; para uso
  público/comercial será preciso um provedor com contrato ou chave. Links do Google Notícias passam por
  redirecionamento e não são lidos (aparecem como "Apenas título disponível").
- **Wikipédia (API)**: exige User-Agent com contato. Ajuste `WIKI_USER_AGENT` em `Sources.kt` com um
  contato real do projeto; sem isso a API pode responder HTTP 429.
- Páginas `http://` não são lidas (tráfego sem TLS está desativado no app); aparecem só com o trecho.

## Limitações desta versão (sem modelo)

- Sem Qwen, DeBERTa ou API de modelo. As regras só comparam **quantidade + unidade (+ categoria, período)**
  e **ano de um fato**. Outras afirmações ficam **Inconclusiva**, com a limitação explicada na tela.
- A indicação exige que a frase da fonte nomeie a entidade; textos que dizem "o clube", "a cidade" não
  geram tendência (exceto artigos da Wikipédia cujo título é a entidade). Categorias são comparadas por
  palavras. Números de frases que citam a entidade mas falam de outra coisa (ex.: municípios de um país)
  podem aparecer como divergência; a explicação mostra cada valor e sua fonte.
- Esclarecimento sem modelo cobre só sentidos/homônimos da Wikipédia, característica × aparência e geral
  × caso específico. Tempo, local e categoria não são detectados.
- Nome próprio no início da frase ("Brasil tem…") nem sempre é reconhecido como entidade; a pesquisa usa
  os termos, o que ainda funciona na maioria dos casos testados.
- O cache é em memória (notícias 10 min, web 30 min, Wikipédia 6 h, páginas 1 h) e some ao fechar o app.
- Resultados dependem do que os buscadores devolvem no momento. A indicação descreve os trechos
  encontrados e não garante a verdade.

## Referências fora do aplicativo

- `research-server/` (cópia Python do protótipo) e `RESEARCH-INTEGRATION.md` documentam a integração
  intermediária pelo Mac, substituída pela pesquisa nativa.
- `evidence-server/`, `local-vlm-server/` e `ml-training/` são experimentos separados. Os clientes Android
  antigos para esses servidores, a busca RSS antiga e seus testes exclusivos foram retirados do módulo do app:
  a interface atual não os chamava. A pesquisa em `research/` e os testes do fluxo atual permanecem.

## Publicação antiga x afirmação atual (30/09/2026)

- **Post com imagem anexada**: quando há um @perfil no alto e texto logo abaixo, esse texto é o conteúdo do post. Letras grandes dentro da arte ou da foto não viram título, e respostas de outros perfis ficam fora (`ArticleReader`).
- **Afirmação datada com número**: se a frase traz "hoje", "amanhã" etc., ou vem de uma publicação com data e descreve uma ação, só fontes sobre o mesmo acontecimento e do mesmo período apoiam ou contradizem o detalhe (`happening` em `ResearchService`, `contextOnly` em `quantityEvidence`). Publicações anteriores e fontes relacionadas aparecem como "Não comparado — … contexto histórico/relacionado".
- **Valor citado com negação**: frase da fonte que cita o número para negá-lo ou restringi-lo ("não estabeleceu uma taxa de 55% para…") não conta como apoio.
- **Contagens e fatos estáveis** ("tem 5 títulos", "em 2020 registrou…") continuam aceitando fontes antigas.
- **Consultas**: afirmações datadas com número levam mais palavras do assunto e nenhuma palavra de tempo.
- Testes: `OldVersusCurrentTests` e `postTextWinsOverBigTextInsideAttachedImage`.

## Post com chamada, material citado e atribuição (30/09/2026)

- **Delimitação**: com o post reconhecido (@perfil no alto), o corpo é só o texto do post; contadores, respostas de outros perfis e demais posts ficam fora. O nome do perfil vem do bloco ao lado ou logo acima do @.
- **Limpeza da entrada** (`cleanInput`): remove a chamada inicial ("URGENTE -", "BOMBA:"), separa palavras curtas coladas pela leitura ("foio" → "foi o") e tira a moldura "Imagens/Vídeo mostram como foi…" (o material citado fica em `materialCitado`).
- **Quadro do fato** (`eventFrame`): a atribuição final ("…, diz O Globo") não é a ação; particípio seguido de "por" é voz passiva ("almoço organizado por X"); nova classe de ação `organizar`; o substantivo que nomeia o fato fica em `eventNoun`.
- **Consulta do fato**: na voz passiva, quem promoveu + demais envolvidos + substantivo do fato ("Vorcaro Moraes almoço").
- **Comparação**: com `eventNoun`, outra redação que traga o mesmo substantivo e todos os envolvidos é o mesmo acontecimento (sem verbo, com "participou", "confirma/nega"). Só nomes em comum não bastam; outra ação (investiga, decide) continua como contexto.
- **Data**: se a matéria importada não diz quando o fato ocorreu e a fonte é da mesma época, a data citada na fonte é a do fato relatado, não "fato anterior".
- **Avisos**: material citado e veículo citado pelo texto aparecem em "O que ainda não sabemos?"; matérias sobre o fato não confirmam esses pontos.
- Testes: `PostClaimRetrievalTests` e `postBodyStopsBeforeCountersAndRepliesFromOtherProfiles`.

## Título de serviço, data solta e formato incerto (30/09/2026)

- **Corte do título** (`claimFor`): "Título: entenda…" só vira "Título" quando a primeira parte é a notícia inteira (4+ palavras) e o resto não traz números nem nomes. Senão, fica o título sem o verbo de chamada ("Lei Seca: em quais estados é proibido beber no 1° turno das eleições"), com nota de que é um guia. Nunca sobra uma palavra solta no lugar de um título longo.
- **Data sozinha na linha** ("30/09/2026 04:00") deixou de ser tratada como contador; com isso a autoria logo acima também é lida.
- **Formato incerto**: se a captura tem @perfil no alto e, abaixo, um título com linha fina, assinatura ou vários parágrafos, fica a leitura de notícia; o texto junto ao perfil vai para as opções, com nota explicando a dúvida.
- Regressões com OCR real: `metropoles-lei-seca`, `x-post-almoco`, `x-post-sobretaxa`, `x-post-flamengo` (em `src/test/resources/ocr`, listados em `RealCaptures.MORE`).
