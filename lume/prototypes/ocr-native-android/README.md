# Lume para Android — pesquisa de fontes sem veredito de veracidade

Este é o aplicativo Android nativo de pesquisa e comparação de fontes. Ele **não calcula uma chance de a notícia ser falsa ou verdadeira**. A tela mostra fontes consultadas, trechos, diferenças de contexto e o que não foi possível esclarecer.

## Fluxos disponíveis

- Colar texto ou link, receber texto/imagem por compartilhamento ou pelo menu de seleção de texto do Android.
- Importar uma imagem e aplicar OCR local com revisão do texto.
- Ler a captura por papéis (`ocr/ArticleReader.kt`): título, subtítulo, assinatura/data, corpo, legenda, crédito de imagem, texto dentro de imagem, coluna lateral, cabeçalho, anúncio e recomendações — cada bloco com o motivo. Veículo, autoria (pessoa ou assinatura institucional, como "Publicado por Agência Senado") e data aparecem com a linha da imagem que os sustenta; o que não aparece fica "não identificado na imagem". Crédito de foto nunca vira autor. A linha de autoria sem "Por" ("Fulano e Ciclano, da CNN Brasil, Brasília e São Paulo") também é lida (autores, veículo e a cidade da assinatura, mostrada como "Assinado em": não é onde o fato aconteceu), e "publicado" e "atualizado" com o mesmo dia e hora aparecem uma vez só. Também são lidos: "Nome, colaboração para a CNN Brasil, São Paulo", os rótulos "Data de Publicação: … Data de Atualização: …", "Por Redação g1" (veículo g1), hora com letra O no lugar do zero ("1Oh15") e "atualizado há um minuto".
- Defesas contra erro de leitura do OCR (`ocr/OcrIntegrity.kt`): corrige maiúscula no meio da palavra (`asSociado` vira `associado`), relê a manchete recortada e ampliada (2x e 3x) quando ela tem número e fica com a leitura da maioria, confere os números da manchete com o resto do texto da imagem e avisa na tela de edição ("Atenção: ...") quando o número não bate, quando o leitor de imagem tem baixa confiança ou quando algo pode estar cobrindo a manchete. Só corrige sozinho o defeito mecânico de um dígito repetido a mais (2044 vira 204); nos outros casos apenas avisa e a pessoa decide.
- Mostrar a afirmação que será pesquisada, com edição e frases alternativas da matéria. Sem título completo (recorte, título cortado ou pergunta), o app pede que a pessoa escolha a frase em vez de completar por suposição.
- Tocar em **Ler esta tela**, autorizar a captura do Android para aquela sessão e revisar a imagem/texto antes de pesquisar. A gotinha flutuante é opcional e oferece a mesma ação.
- Pesquisar em Google Notícias RSS, Bing RSS e Wikipédia com consultas complementares (específica, acontecimento, variação de redação tirada do próprio texto da matéria — ex.: sigla por extenso —, detalhe e partes da frase), ler páginas públicas e comparar cada fonte com a matéria importada pela **estrutura do fato** (`research/EventFrame.kt`): quem agiu e em que papel, qual ação e em que etapa (pedido, proposta, decisão, vigência, revogação…), sobre o quê, quando (data do fato ≠ data de publicação), números, prazos, negação e, em indicadores, a origem do dado. Cada fonte recebe uma relação — **mesmo acontecimento**, **mesmo acontecimento com detalhe divergente**, **contexto relacionado**, **relação incerta** ou **outro acontecimento** (fica de fora, com o motivo em "Como a pesquisa foi feita") — com os trechos literais comparados ("Por que esta relação?") e se o Lume leu a página, só o resumo do buscador ou só o título.
- A notícia importada não conta como fonte dela mesma (`research/Independence.kt`): a própria matéria (mesmo endereço, ou mesmo veículo e título), outras matérias do mesmo veículo e republicações (título idêntico em outro veículo, ou crédito explícito) aparecem em "Não contam como fontes independentes".
- Comparação de sentido opcional com o modelo multilíngue gratuito `paraphrase-multilingual` no Ollama local (computador conectado). Ela só ordena as fontes para leitura e ajuda a ver se o **assunto** é o mesmo quando quem agiu e a ação já coincidem pelo texto; nunca cria agente, ação, data ou trecho e não decide sozinha. Sem ela, a comparação estruturada continua funcionando (mesmo resultado na busca real do UOL, em cerca de 2 s).
- Integração opcional do modelo treinado de pares (6B reduzido): combina similaridade do MPNet ajustado, sobreposição de palavras e nota do BERTimbau ajustado. O app usa essa nota junto das regras para auxiliar a triagem, podendo sinalizar uma fonte como provável ou confirmar a relação com limites mais altos; a interface identifica quando a indicação veio do modelo. Isso não é um veredito de veracidade. Requer o modelo `lume-mpnet-ajustado` no Ollama e o servidor local do BERTimbau; sem os serviços configurados, o app continua com as regras e a comparação de sentido disponível.
- Salvar rascunho e pesquisas no próprio aparelho, reabrir resultados em **Ajustes** e apagar o histórico. Imagens e texto bruto de OCR não entram no histórico.

As buscas públicas não exigem chave nem API paga. Os recursos de IA local dependem do computador conectado por ADB. `paraphrase-multilingual` e o modelo treinado de pares cumprem papéis diferentes: o primeiro ajuda na similaridade/ordenação; o segundo combina sinais treinados para apoiar a classificação da relação entre alegação e fonte. Ambos podem ficar indisponíveis sem interromper a pesquisa baseada em regras. Os pesos grandes não são incluídos no Git; veja a configuração em [`SETUP.md`](SETUP.md).

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
- Quando algo cobre a manchete (aviso de notificação, anúncio), o OCR pode ler um número errado ("5" no lugar de "13"): o app avisa e não adivinha. Letras trocadas por outras (ex.: "voaram" no lugar de "votaram") não são corrigidas.
- Medição de 07/10/2026 do comparador estruturado (piloto de 100 pares, 60 pares novos e o conjunto de pares da equipe): recupera cerca de um terço dos "mesmo acontecimento" (limite superior do IC95% em torno de 0,55) e quase não erra ao dizer "mesmo" (1% a 2% de falsos positivos nos conjuntos grandes). Falha em paráfrase forte e em alegação x negativa. Os rótulos são provisórios e de IA. Relatório: `lume/dataset-piloto-2026-10-05/evaluation_report_v2.txt` (local, ainda não versionado).
- O `robots.txt` do Google Notícias proíbe os feeds `/rss` para robôs genéricos; o do Bing permite `/news/search?...format=rss`. O app consulta por pedido da pessoa, mas a equipe deve decidir se mantém o Google RSS.
- OCR não detecta montagem de imagem, deepfake ou autenticidade de vídeo. Captura de tela pode ser bloqueada pelo Android em conteúdo protegido.
- O histórico fica no armazenamento privado do aplicativo. A pesquisa envia as consultas aos provedores públicos, mesmo quando a IA roda localmente.
- Para disponibilizar a IA a usuários sem o computador conectado, será necessário hospedar um serviço ou integrar um modelo compatível no aparelho; isso não está entregue nesta demonstração gratuita.

O código do treinamento e o fluxo legado em `lume/android` continuam separados deste aplicativo.
