# Lume para Android — pesquisa de fontes sem classificador de veracidade

Este é o aplicativo Android nativo que pode ser demonstrado enquanto o classificador do projeto é treinado. Ele **não calcula uma chance de a notícia ser falsa ou verdadeira**. A tela mostra fontes consultadas, trechos, diferenças de contexto e o que não foi possível esclarecer.

## Fluxos disponíveis

- Colar texto ou link, receber texto/imagem por compartilhamento ou pelo menu de seleção de texto do Android.
- Importar uma imagem e aplicar OCR local com revisão do texto.
- Ler a captura por papéis (`ocr/ArticleReader.kt`): título, subtítulo, assinatura/data, corpo, legenda, crédito de imagem, texto dentro de imagem, coluna lateral, cabeçalho, anúncio e recomendações — cada bloco com o motivo. Veículo, autoria (pessoa ou assinatura institucional, como "Publicado por Agência Senado") e data aparecem com a linha da imagem que os sustenta; o que não aparece fica "não identificado na imagem". Crédito de foto nunca vira autor.
- Mostrar a afirmação que será pesquisada, com edição e frases alternativas da matéria. Sem título completo (recorte, título cortado ou pergunta), o app pede que a pessoa escolha a frase em vez de completar por suposição.
- Tocar em **Ler esta tela**, autorizar a captura do Android para aquela sessão e revisar a imagem/texto antes de pesquisar. A gotinha flutuante é opcional e oferece a mesma ação.
- Pesquisar em Google Notícias RSS, Bing RSS e Wikipédia com consultas complementares (específica, acontecimento, variação de redação tirada do próprio texto da matéria — ex.: sigla por extenso —, detalhe e partes da frase), ler páginas públicas e comparar cada fonte com a matéria importada pela **estrutura do fato** (`research/EventFrame.kt`): quem agiu e em que papel, qual ação e em que etapa (pedido, proposta, decisão, vigência, revogação…), sobre o quê, quando (data do fato ≠ data de publicação), números, prazos, negação e, em indicadores, a origem do dado. Cada fonte recebe uma relação — **mesmo acontecimento**, **mesmo acontecimento com detalhe divergente**, **contexto relacionado**, **relação incerta** ou **outro acontecimento** (fica de fora, com o motivo em "Como a pesquisa foi feita") — com os trechos literais comparados ("Por que esta relação?") e se o Lume leu a página, só o resumo do buscador ou só o título.
- A notícia importada não conta como fonte dela mesma (`research/Independence.kt`): a própria matéria (mesmo endereço, ou mesmo veículo e título), outras matérias do mesmo veículo e republicações (título idêntico em outro veículo, ou crédito explícito) aparecem em "Não contam como fontes independentes".
- Comparação de sentido opcional com o modelo multilíngue gratuito `paraphrase-multilingual` no Ollama local (computador conectado). Ela só ordena as fontes para leitura e ajuda a ver se o **assunto** é o mesmo quando quem agiu e a ação já coincidem pelo texto; nunca cria agente, ação, data ou trecho e não decide sozinha. Sem ela, a comparação estruturada continua funcionando (mesmo resultado na busca real do UOL, em cerca de 2 s).
- Salvar rascunho e pesquisas no próprio aparelho, reabrir resultados em **Ajustes** e apagar o histórico. Imagens e texto bruto de OCR não entram no histórico.

As buscas públicas não exigem chave nem API paga. O Ollama roda no computador; por isso a comparação de sentido na demonstração depende de o computador estar ligado e conectado por ADB. O app funciona sem ela. O classificador do colega permanece separado.

## Abrir e executar

**Preparar Windows ou macOS (SDK, Ollama, modelo, emulador, testes): veja [`SETUP.md`](SETUP.md).** Modelos: `setup/ollama-models.txt`; configuração local: `local.properties.example`; scripts: `start-lume-local-ai.ps1` (Windows) e `setup/start-lume-local-ai.sh` (macOS/Linux).

Abra **esta pasta** (`lume/prototypes/ocr-native-android`) no Android Studio, sincronize o Gradle, selecione o emulador ou aparelho e clique em **Run**. O projeto usa Android API 24 como mínimo e compileSdk 37. O APK de debug é gerado em `app/build/outputs/apk/debug/app-debug.apk`.

No Windows, com Ollama instalado, execute `start-lume-local-ai.ps1` no PowerShell antes da demonstração. O script verifica/baixa o modelo gratuito `paraphrase-multilingual` (aprox. 560 MB) e cria a ponte ADB local. Repita o script após reiniciar o emulador ou desconectar o cabo USB. A aplicação Ollama deve continuar aberta enquanto o Lume pesquisa.

Para conferir o projeto pelo terminal:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Diagnóstico e regressões

- `OcrFixtureDumpTest` (androidTest) grava a saída real do ML Kit das capturas em `app/src/test/resources/ocr`; `ArticleReaderRegressionTest` e `ReaderDiagnosticsTest` usam esses arquivos na JVM.
- [`docs/capture-corpus/`](docs/capture-corpus/README.md) reúne sete capturas de notícias e posts com expectativas explícitas, métricas por etapa e uma linha de base reproduzível (`CaptureCorpusEvaluationTest`). Os textos das capturas não são rótulos de veracidade.
- `WebCaptureTool` (androidTest) gera capturas de páginas públicas no emulador: `adb shell am instrument -w -e class com.example.lumeocrtest.WebCaptureTool -e captureUrl <url> -e captureName <nome> com.example.lumeocrtest.test/androidx.test.runner.AndroidJUnitRunner`.
- Cada pesquisa gera um `ResearchTrace` (consultas, itens por provedor, triagem com motivo, duplicatas, agrupamentos, limite, exibidos e relação). No app de depuração ele vai para o logcat (`LumeTrace`); na tela, um resumo em "Como a pesquisa foi feita".
- Busca com rede real pela JVM: `LUME_LIVE=1 gradlew.bat :app:testDebugUnitTest --tests "*LiveResearchDiagnostics*"` (relatórios em `app/build/lume-live/`).
- Relatório da validação de 29/09/2026, com capturas de tela: `docs/validacao-imagem/RELATORIO.md`.
- Pares difíceis (matéria importada × fonte): `app/src/test/resources/pairs/pares-dificeis.json`, verificados por `SameEventPairsTest` (antes/depois em `app/build/lume-live/pares-*.txt`). Relatório da correção de "mesmo acontecimento", avaliação de modelos locais e telas: `docs/validacao-mesmo-acontecimento/RELATORIO.md`.

## Limites honestos desta versão

- A leitura da matéria usa regras de layout validadas em 7 capturas (Agência Senado, BBC, Agência Brasil, Agência Câmara, g1 em dois pontos da página e um recorte) e em casos sintéticos. Diagramações muito diferentes, modo escuro, imagem de baixa qualidade ou erros do OCR ainda podem exigir que o usuário corrija a afirmação.
- A comparação estruturada usa um vocabulário geral de ações do português jornalístico; verbos fora dele deixam a ação "não identificada" e a fonte tende a ficar em "relação incerta". Contradições que não envolvem números, negação, direção (sobe/cai) ou etapa não são detectadas. Quando uma página repete o acontecimento só na abertura, com outro foco no título, a fonte pode ser aceita pelo que a abertura diz.
- Links do Google Notícias não são abertos pelo app (redirecionamento); essas fontes são avaliadas só pelo título, e a tela informa isso.
- Uma pesquisa pode ficar inconclusiva; ausência de fonte não prova falsidade.
- RSS e páginas públicas podem ficar indisponíveis, omitir o corpo da notícia ou não oferecer dados estruturados.
- Modelos locais foram avaliados em 37 pares reais e sintéticos em português (`docs/validacao-mesmo-acontecimento/`): nenhum decide "mesmo acontecimento" com segurança sozinho; por isso o modelo é só auxiliar e nunca decide veracidade.
- OCR não detecta montagem de imagem, deepfake ou autenticidade de vídeo. Captura de tela pode ser bloqueada pelo Android em conteúdo protegido.
- O histórico fica no armazenamento privado do aplicativo. A pesquisa envia as consultas aos provedores públicos, mesmo quando a IA roda localmente.
- Para disponibilizar a IA a usuários sem o computador conectado, será necessário hospedar um serviço ou integrar um modelo compatível no aparelho; isso não está entregue nesta demonstração gratuita.

O código do treinamento e o fluxo legado em `lume/android` continuam separados deste aplicativo.
