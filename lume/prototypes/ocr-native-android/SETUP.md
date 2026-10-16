# Preparar o ambiente do Lume Android (Windows e macOS)

Pasta do app no repositório: `lume/prototypes/ocr-native-android`. Tudo abaixo é relativo a ela.

**Estado da validação:** passos de Windows executados em 30/09/2026 (Windows 11, Android Studio com JBR, emulador
`Medium_Phone` API 37, Ollama 0.34.4). **macOS (Apple Silicon): compilação, testes unitários, instalação no emulador (API 37), `adb reverse` e Ollama local validados em 07/10/2026**; no Mac o `gradlew` pode estar sem permissão de execução (use `bash gradlew`). O script `setup/start-lume-local-ai.sh` ainda NÃO foi executado em um Mac (só no Git Bash do Windows): validação pendente.

## O que precisa existir em cada computador (não vem do Git)

| Item | Por quê | Como obter |
|---|---|---|
| Android Studio (com JBR 17+) e Android SDK (API 37, Platform-Tools, Emulator) | compilar e rodar | instalador do Android Studio + SDK Manager |
| Um AVD (ex.: Pixel/“Medium Phone”, API 37, Google APIs) | emulador | Device Manager |
| Ollama | serviço local de IA | https://ollama.com/download (macOS: também `brew install ollama`) |
| Pesos do modelo `paraphrase-multilingual:latest` (≈562 MB) | comparação de sentido | `ollama pull paraphrase-multilingual` |
| Opcional: `lume-mpnet-ajustado` no Ollama e pesos locais do BERTimbau ajustado | modelo treinado de pares integrado | provisionar os pesos localmente; eles não são versionados |
| Opcional: servidor local do BERTimbau (porta 8765) | pontuar pares para a integração do modelo treinado | `lume/ml/integracao_modelo_matheus/servidor_bert.py` |
| `local.properties` | caminho do SDK e endereço do Ollama | criado pelo Android Studio; modelo em `local.properties.example` |

O GitHub guarda **só os identificadores** dos modelos (`setup/ollama-models.txt`: nome, digest e tamanho), nunca os pesos.
Baixe-os em cada máquina. Confira o digest com `ollama list` (ID `ba13c2e06707`).

## Configuração local do app

O código não tem caminhos de máquina. O endereço do Ollama e o modelo vêm de `local.properties` (ou `-P` no Gradle) e entram no
`BuildConfig` na compilação:

```properties
lume.ollama.url=http://127.0.0.1:11434
lume.ollama.embedModel=paraphrase-multilingual
# Opcional: habilita o modelo treinado de pares quando os dois serviços estiverem ativos
lume.pairModel.url=http://127.0.0.1:8765
lume.pairModel.embedModel=lume-mpnet-ajustado
```

- Padrão `http://127.0.0.1:11434` + `adb reverse tcp:11434 tcp:11434`: o `127.0.0.1` do emulador/aparelho é encaminhado ao
  computador. Funciona igual no Windows e no Mac, e com celular por cabo USB.
- Alternativa só no emulador: `http://10.0.2.2:11434` (endereço do computador visto pelo emulador), sem `adb reverse`.
- Mudou `local.properties`? Recompile o app (o valor é gravado no APK).
- **Servidor de fábrica (APK para distribuir):** `lume.servidor.url=https://...` e `lume.servidor.chave=<chave do .token do gateway>`
  fazem o APK sair já apontando para o gateway do grupo (`lume/ml/gateway`), sem a pessoa colar nada em Ajustes. Em Ajustes ela
  ainda pode trocar o servidor, e "Salvar e testar" com os campos vazios volta ao de fábrica. A chave dentro do APK é extraível por
  quem abrir o arquivo: serve para a demonstração (o gateway limita pedidos por IP e a chave se troca em um comando), não para um
  produto com usuários de verdade, que pediria uma chave por pessoa.
- O modelo treinado de pares só é ativado quando `lume.pairModel.url` está configurado. Ele precisa do modelo `lume-mpnet-ajustado` no Ollama e do servidor BERTimbau acessível; faça também `adb reverse tcp:8765 tcp:8765`. O servidor aceita `LUME_BERT_DIR` para apontar para os pesos locais do BERTimbau. Sem essa configuração, o app segue com a comparação estruturada e o recurso `paraphrase-multilingual`, se disponível.

## macOS (validação pendente)

1. **Clonar ou atualizar**
   ```bash
   git clone https://github.com/matheushpr94/challenge-fakenews-unb.git   # primeira vez
   cd challenge-fakenews-unb && git fetch origin && git checkout main && git pull --ff-only
   cd lume/prototypes/ocr-native-android
   ```
   Confirmar: `git log -1 --oneline` mostra o commit publicado; existem `SETUP.md`, `setup/` e `app/`.
2. **Android Studio e SDK:** instale o Android Studio; em *Settings › Languages & Frameworks › Android SDK* marque *Android API 37*,
   *Android SDK Platform-Tools*, *Android Emulator* (em Apple Silicon, imagem **arm64-v8a**). Abra a **pasta do app**
   (`ocr-native-android`, não a raiz do repositório) com *Open*. Aguarde o *Gradle Sync*.
   Confirmar: sync sem erros; `local.properties` criado com `sdk.dir=/Users/<você>/Library/Android/sdk`.
3. **ADB no terminal:** `export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"` (coloque no `~/.zshrc`).
   Confirmar: `adb version`.
4. **Emulador:** *Device Manager › Create device* (ex.: Medium Phone, API 37) e inicie. Confirmar: `adb devices` lista `emulator-5554  device`.
5. **Ollama:** instale o app (https://ollama.com/download) ou `brew install ollama`. Abra o Ollama (ícone na barra) ou rode `ollama serve`.
   Confirmar: `curl http://127.0.0.1:11434/api/tags` responde JSON; `ollama --version`.
6. **Modelo:** `ollama pull paraphrase-multilingual`. Confirmar: `ollama list` mostra `paraphrase-multilingual:latest  ba13c2e06707`.
   Teste do endpoint usado pelo app:
   ```bash
   curl http://127.0.0.1:11434/api/embed -d '{"model":"paraphrase-multilingual","input":["teste"]}'
   ```
   deve conter `"embeddings"`.
7. **Ponte emulador ↔ Ollama (tudo de uma vez):** `./setup/start-lume-local-ai.sh`. Ele confere Ollama, baixa o modelo se faltar,
   testa `/api/embed` e roda `adb reverse tcp:11434 tcp:11434`. Confirmar: `adb reverse --list` mostra `tcp:11434 tcp:11434`.
   Repita após reiniciar o emulador.
8. **Compilar, testar, instalar** (JDK do Android Studio: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`):
   ```bash
   ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
   ./gradlew :app:connectedDebugAndroidTest        # emulador ligado
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   `connectedDebugAndroidTest` desinstala o app ao terminar; reinstale depois. Ou use *Run* no Android Studio.
9. **Opcional — ativar o modelo treinado de pares:** no `local.properties`, configure `lume.pairModel.url=http://127.0.0.1:8765` e `lume.pairModel.embedModel=lume-mpnet-ajustado`. Inicie o Ollama com esse modelo e rode `servidor_bert.py` em um ambiente Python com PyTorch e Transformers, apontando `LUME_BERT_DIR` para os pesos locais. Encaminhe a porta com `adb reverse tcp:8765 tcp:8765` e recompile o app. O protótipo integra a combinação treinada de MPNet, sobreposição lexical e BERTimbau; ela é auxiliar e não avalia veracidade.
10. **Testar o app:** importe uma captura (ex.: arraste `app/src/androidTest/assets/uol-flamengo-stf.png` para o emulador), confira
   a leitura (autor “Igor Siqueira”, veículo UOL), pesquise e abra “Como a pesquisa foi feita”: confira se a comparação de sentido
   foi usada e, se configurou os serviços do modelo treinado, se a nota dele aparece na relação das fontes. Logcat:
   `adb logcat -s LumeResearch LumeSemantic`.

## Windows (validado neste PC)

1. `git fetch origin && git checkout main && git pull --ff-only`; abra `lume\prototypes\ocr-native-android` no Android Studio.
2. SDK igual ao passo 2 do macOS (imagem x86_64). `local.properties` com `sdk.dir=C\:\\Users\\<você>\\AppData\\Local\\Android\\Sdk`.
3. Instale o Ollama (https://ollama.com/download/windows). Inicie o emulador.
4. `powershell -ExecutionPolicy Bypass -File .\start-lume-local-ai.ps1` — inicia o Ollama se preciso, baixa `paraphrase-multilingual`
   e cria o `adb reverse`. Saída esperada: “IA local do Lume pronta…”.
5. Com o JDK do Android Studio (`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`):
   `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, depois `.\gradlew.bat :app:connectedDebugAndroidTest`
   e `adb install -r app\build\outputs\apk\debug\app-debug.apk`.

## Sem Ollama

O APK **não** traz modelo embutido: a comparação de sentido depende do Ollama no computador conectado. Sem ele:

- Continuam funcionando: OCR (ML Kit, no aparelho), leitura por papéis, afirmação, busca (Google Notícias, Bing, Wikipédia — precisa
  de internet), leitura de páginas, comparação estruturada (quem, ação, assunto, data, números, negação), exclusão da própria
  matéria, histórico.
- Fica indisponível: a similaridade de sentido usada para ordenar páginas e para decidir se o assunto é o mesmo quando quem agiu e a
  ação já coincidem. Esses casos ficam como **“relação incerta”** em vez de “mesmo acontecimento”.
- Mensagem ao usuário em “Como a pesquisa foi feita”: *“Comparação de sentido por IA local não usada nesta pesquisa (indisponível);
  a relação foi decidida só pelos textos.”* Depois de uma falha o app não tenta de novo por 60 s (logcat `LumeSemantic`:
  “Comparação de sentido indisponível…”).
- Medido na busca real da captura do UOL: mesmo resultado (10 veículos) com e sem o modelo; ver `docs/validacao-mesmo-acontecimento/`.

## Diagnóstico de erros comuns

| Sintoma | Causa provável | Como resolver |
|---|---|---|
| “Comparação de sentido… não usada” com Ollama aberto | falta `adb reverse` (emulador reiniciado) | rode o script de novo; `adb reverse --list` |
| `curl …/api/embed` → `model not found` | modelo não baixado | `ollama pull paraphrase-multilingual` |
| `connection refused` em 11434 | Ollama parado | abrir o app Ollama / `ollama serve` |
| Usando `10.0.2.2` e sem resposta | Ollama escuta só em 127.0.0.1 do host no seu sistema | use o padrão com `adb reverse`, ou `OLLAMA_HOST=0.0.0.0 ollama serve` (exponha só em rede confiável) |
| Gradle: `SDK location not found` | `local.properties` sem `sdk.dir` | abra no Android Studio ou crie a linha |
| Gradle: versão de Java | JDK errado | use o JBR do Android Studio (`JAVA_HOME`) |
| `adb: no devices` | emulador desligado | inicie o AVD; `adb devices` |
| Testes instrumentados somem o app | comportamento do `connectedAndroidTest` | reinstale o APK |
| “Abrir fonte original” para numa tela do Chrome | primeira execução do Chrome no AVD | aceite os termos uma vez no emulador |
| Busca “Não foi possível consultar as fontes” | emulador sem internet | confira Wi‑Fi do AVD |

## Diagnóstico com rede real (opcional)

`LUME_LIVE=1 LUME_LIVE_SEMANTIC=1 ./gradlew :app:testDebugUnitTest --tests '*LiveResearchDiagnostics*'` (relatórios em
`app/build/lume-live/`). Sem `LUME_LIVE_SEMANTIC`, roda sem o Ollama.


## Sinal do texto (opcional)
Mostra o estilo da escrita (boato/checagem x notícia comum) de textos longos; não verifica fatos. Servidor local em `lume/ml/sinal_texto`
(porta 8766). Ligar tudo de uma vez: `bash lume/ml/iniciar_modelos_locais.sh`. Em `local.properties`: `lume.textSignal.url=http://127.0.0.1:8766`
(sem a linha, o recurso fica desligado). Detalhes, calibração e limites: `lume/docs/SINAL-DO-TEXTO.md`.
