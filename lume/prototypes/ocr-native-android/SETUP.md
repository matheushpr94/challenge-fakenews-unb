# Preparar o ambiente do Lume Android (Windows e macOS)

Pasta do app no repositório: `lume/prototypes/ocr-native-android`. Tudo abaixo é relativo a ela.

**Estado da validação:** passos de Windows executados em 30/09/2026 (Windows 11, Android Studio com JBR, emulador
`Medium_Phone` API 37, Ollama 0.34.4). **Passos de macOS: não testados em um Mac — validação pendente.** Os comandos seguem a
documentação oficial das ferramentas e o script `setup/start-lume-local-ai.sh` foi executado apenas no Git Bash do Windows.

## O que precisa existir em cada computador (não vem do Git)

| Item | Por quê | Como obter |
|---|---|---|
| Android Studio (com JBR 17+) e Android SDK (API 37, Platform-Tools, Emulator) | compilar e rodar | instalador do Android Studio + SDK Manager |
| Um AVD (ex.: Pixel/“Medium Phone”, API 37, Google APIs) | emulador | Device Manager |
| Ollama | serviço local de IA | https://ollama.com/download (macOS: também `brew install ollama`) |
| Pesos do modelo `paraphrase-multilingual:latest` (≈562 MB) | comparação de sentido | `ollama pull paraphrase-multilingual` |
| `local.properties` | caminho do SDK e endereço do Ollama | criado pelo Android Studio; modelo em `local.properties.example` |

O GitHub guarda **só os identificadores** dos modelos (`setup/ollama-models.txt`: nome, digest e tamanho), nunca os pesos.
Baixe-os em cada máquina. Confira o digest com `ollama list` (ID `ba13c2e06707`).

## Configuração local do app

O código não tem caminhos de máquina. O endereço do Ollama e o modelo vêm de `local.properties` (ou `-P` no Gradle) e entram no
`BuildConfig` na compilação:

```properties
lume.ollama.url=http://127.0.0.1:11434
lume.ollama.embedModel=paraphrase-multilingual
```

- Padrão `http://127.0.0.1:11434` + `adb reverse tcp:11434 tcp:11434`: o `127.0.0.1` do emulador/aparelho é encaminhado ao
  computador. Funciona igual no Windows e no Mac, e com celular por cabo USB.
- Alternativa só no emulador: `http://10.0.2.2:11434` (endereço do computador visto pelo emulador), sem `adb reverse`.
- Mudou `local.properties`? Recompile o app (o valor é gravado no APK).

## Linguagem do texto (modelo FactNews, opcional)

O app pode mostrar uma seção **"Linguagem do texto"**: rotula cada frase da matéria (tom de relato, fala citada, linguagem
enviesada) e resume em *sinal de viés*, *sem sinal de viés*, *inconclusivo* ou *sem texto para analisar*. É um **sinal de estilo, não
um veredito**: não diz se a notícia é verdadeira ou falsa. Detalhes, medidas e limites: `lume/ml/factnews/README.md`.

Fica **desligada por padrão** (`lume.roleclassifier.url` vazio): sem isso o app funciona como antes. Para ligar:

1. **Ambiente e pesos do modelo** (uma vez por computador; pasta `lume/ml/factnews`, Python 3.11 — passos em `ml/factnews/README.md`):
   `pip install -r requirements.txt` e `-r requirements-bert.txt` (PyTorch: Windows com GPU NVIDIA usa o índice `cu128`; no Mac, o do PyPI)
   e `python scripts/fetch_weights.py` (baixa ~192 MB da Release do GitHub e confere o SHA-256; **a Release ainda não foi publicada**,
   então por ora use `--file <pacote.zip>` ou treine: `scripts/04_context_ensemble.py --export --use-test --ctx none --seeds 42 --tag factnews-v2`).
2. **`local.properties`** do app: `lume.roleclassifier.url=http://127.0.0.1:8765` e **recompile** o app.
3. **Ligar o servidor e a ponte do emulador** (deixe a janela aberta):
   - Windows: `lume\ml\factnews\scripts\start-factnews-server.ps1`
   - macOS: `lume/ml/factnews/scripts/start-factnews-server.sh`

   Ele escuta só em `127.0.0.1:8765` (nunca na rede), roda `adb reverse tcp:8765 tcp:8765` e carrega o modelo. Repita após reiniciar o emulador.
4. Faça uma pesquisa. Com o servidor desligado o app continua funcionando e mostra "Indisponível" nessa seção.

Como funciona: o título é rotulado primeiro e **nunca conclui sozinho** (em manchetes, "enviesada" erra metade das vezes). Depois o app
lê o corpo: o texto da captura, se tiver frases suficientes; senão a própria matéria encontrada na internet (mesmo endereço, republicação
ou resultado com o mesmo título); senão só o título ou a frase digitada. Texto em CAIXA ALTA é normalizado antes de classificar.
Logcat: `adb logcat -s LumeResearch LumeRoles`. **Validado em 06/10/2026 no emulador (API 37) de um PC com Windows 11; passos de macOS desta seção: não testados em um Mac.**

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
9. **Testar o app:** importe uma captura (ex.: arraste `app/src/androidTest/assets/uol-flamengo-stf.png` para o emulador), confira
   a leitura (autor “Igor Siqueira”, veículo UOL), pesquise e abra “Como a pesquisa foi feita”: deve dizer que a comparação de
   sentido (IA local) foi usada. Logcat: `adb logcat -s LumeResearch LumeSemantic`.

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
