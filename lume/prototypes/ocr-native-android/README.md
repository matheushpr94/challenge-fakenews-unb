# Lume — protótipo Android de OCR e pesquisa (0.1)

**Protótipo experimental separado do produto oficial em `lume/android/`.** Esta pasta contém uma cópia completa das fontes do projeto LumeOCRTest de 28/09/2026, para continuar o desenvolvimento em outro computador. Não substitui a demo oficial nem os experimentos do challenge.

## O que já funciona

- Interface nativa em Kotlin/Jetpack Compose, com identidade visual verde e creme.
- Personagem original na tela inicial; gotinha flutuante com permissão explícita, arraste, encaixe na borda, painel e desativação.
- “Simular captura”: escolher um print → ML Kit OCR no aparelho → limpeza de texto e extração de metadados → pesquisa.
- Entrada de texto ou link; links públicos acessíveis fornecem título/descrição para orientar a pesquisa.
- Pesquisa nativa de fontes públicas, leitura de páginas acessíveis, contexto e agrupamento de republicações.
- Apresentação integrada dos detalhes; publicações apenas relacionadas ficam recolhidas.
- Animações suaves, preferências locais e preservação da sessão ao girar a tela.

**Não há captura automática da tela nem integração com um modelo de veracidade.** As comparações usam as regras já existentes; muitos casos ficam inconclusivos. Ausência de fontes não prova falsidade. O fluxo principal não precisa de servidor no Mac, Docker, chave de API nem serviço pago; exige internet para pesquisar.

## Abrir em casa — caminho mais simples

1. No GitHub, use **Code → Download ZIP** e extraia o repositório. Também pode usar Git:

   ```bash
   git clone https://github.com/matheushpr94/challenge-fakenews-unb.git
   ```

2. Instale/abra o **Android Studio** e escolha **Open**.
3. Selecione a pasta **`lume/prototypes/ocr-native-android`**, onde estão `settings.gradle.kts`, `gradlew` e `app/`. Não abra a raiz do repositório como projeto Android.
4. Aguarde a sincronização do Gradle e aceite os downloads necessários do SDK/JDK pelo Android Studio.
5. Em **SDK Manager**, instale **Android SDK Platform 37**, ferramentas de build solicitadas e Platform Tools. O projeto mantém exatamente a configuração usada no Mac: AGP 9.4.1, Gradle 9.6.0 e daemon JDK 25. Se a sua versão do Android Studio não suportar essa configuração, atualize-a antes de sincronizar; não altere versões aleatoriamente.
6. Em **Gradle JDK**, use JDK 25. `gradle/gradle-daemon-jvm.properties` também contém a configuração de provisionamento automático do JDK para macOS, Windows e Linux.
7. Crie um emulador ou conecte um aparelho Android 7/API 24 ou superior, com depuração USB, e clique em **Run**.
8. Toque em **Simular captura** e escolha um print para testar; ou **Colar texto ou link**. Ativar a gotinha exige a autorização de sobreposição do Android. Ela abre as opções de análise, sem ler outros apps automaticamente.

O Android Studio cria `local.properties` com o SDK do seu computador. Esse arquivo não foi exportado porque o caminho do SDK do Mac não funciona em outro computador. Não são necessários Python, MPS, GPU, pesos de modelos ou treinamento para executar o app.

## Terminal integrado

Dentro desta pasta, com o SDK configurado e JDK 25:

**Windows / PowerShell**

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

**macOS / Linux**

```bash
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK gerado: `app/build/outputs/apk/debug/app-debug.apk`. APKs, caches e chaves de assinatura não são versionados. Um build debug feito em outro computador pode exigir reinstalar o app por ter assinatura diferente.

Com emulador/aparelho conectado:

```bash
./gradlew :app:connectedDebugAndroidTest
```

No Windows, troque `./gradlew` por `.\gradlew.bat`.

## Organização

| Pasta / arquivo | Finalidade |
| --- | --- |
| `app/` | Aplicativo completo, recursos e testes |
| `app/src/main/java/com/example/lumeocrtest/ocr/` | OCR, limpeza e metadados |
| `app/src/main/java/com/example/lumeocrtest/research/` | Pesquisa, relevância, contexto e comparação nativa |
| `app/src/main/java/com/example/lumeocrtest/mascot/` | Serviço da gotinha flutuante |
| `gradle/`, `gradlew`, `gradlew.bat` | Build reproduzível e wrapper incluído |
| `FRONTEND-LUME.md` | Interface, animações e validação |
| `RESEARCH-NATIVE.md` | Arquitetura nativa e limitações dos provedores |
| `docs/frontend/` | Capturas de referência da versão implementada |
| `research-server/`, `evidence-server/`, `local-vlm-server/` | Referências de etapas anteriores; não necessárias ao fluxo atual |
| `ml-training/` | Scripts de experimentos anteriores; sem datasets ou modelos |
| `EXPORT-MANIFEST.json` | SHA-256 das fontes copiadas, para conferir a integridade |

As pastas auxiliares foram preservadas como referência porque pertenciam ao projeto original. Seus READMEs podem descrever integrações antigas; siga este README e `RESEARCH-NATIVE.md` para executar a versão atual. Caminhos `/Users/aluno1/...` em documentos históricos não devem ser usados no seu computador.

## Validação e limites

No Mac de origem: build debug e lint concluídos (com avisos), **78 testes unitários e 4 instrumentados aprovados**, inspeção visual, OCR real, permissões, gotinha sobre outros apps, arraste, desativação, rotação e fonte a 160%.

A exportação conserva os mesmos fontes e versões. A execução em Windows/Linux e em diferentes aparelhos físicos ainda deve ser conferida. Google/Bing RSS e páginas públicas podem mudar ou limitar acesso; a qualidade da interpretação continua limitada pelas regras. A gotinha pode ser ocultada pelo Android em telas protegidas.

Os mascotes foram preservados: gotinha original do projeto e personagem da tela inicial recortado do screenshot fornecido. O original de maior resolução poderá melhorar sua nitidez.

## Continuar o desenvolvimento

Depois de baixar por Git:

```bash
git pull --ff-only
# trabalhe apenas nesta pasta do protótipo
git add lume/prototypes/ocr-native-android
git commit -m "feat(lume-prototype): descreva a melhoria"
git push
```

Execute esses últimos comandos na raiz do repositório; evite incluir configurações locais, modelos ou arquivos gerados. Para colaborar, prefira uma branch e pull request. Nenhum arquivo anterior do produto oficial foi alterado por esta exportação.
