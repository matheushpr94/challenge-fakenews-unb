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

## O que ficou como referência (fora do fluxo principal)

- `research-server/` (cópia Python do protótipo) e `RESEARCH-INTEGRATION.md`: integração intermediária
  pelo Mac, substituída por esta arquitetura. `ocr/ResearchClient.kt` e seu teste continuam no projeto,
  mas a interface não os usa.
- `ocr/SearchClient.kt`, `QueryAnalyzer.kt`, `RelevanceScorer.kt`, `EvidenceClient.kt`, `VlmClient.kt`,
  `evidence-server/`, `local-vlm-server/`, `ml-training/`: preservados, não usados pela tela.
