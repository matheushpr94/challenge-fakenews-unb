# Lume — protótipo Android de OCR e pesquisa (0.1)

Protótipo experimental separado de `lume/android/`. Cópia do projeto LumeOCRTest de 28/09/2026 para continuar o desenvolvimento em outro computador.

Interface Kotlin/Jetpack Compose, ML Kit OCR local, extração de metadados, pesquisa nativa de notícias/contexto, fontes e gotinha flutuante. O fluxo principal não exige servidor no Mac, Docker, chave de API ou treinamento. Exige internet para pesquisar. Não há captura automática da tela nem modelo de veracidade integrado.

## Abrir em casa

1. Use **Code → Download ZIP** na raiz do repositório e extraia; ou `git clone https://github.com/matheushpr94/challenge-fakenews-unb.git`.
2. Android Studio → **Open** → selecione `lume/prototypes/ocr-native-android`.
3. Sincronize o Gradle; instale SDK Platform 37 e use JDK 25. Gradle 9.6.0 e AGP 9.4.1 estão preservados.
4. Execute em emulador ou aparelho Android API 24+.
5. **Simular captura** importa um print para OCR; **Colar texto ou link** permite pesquisar.

Windows: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
macOS/Linux: `chmod +x gradlew` e `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.

APK gerado em `app/build/outputs/apk/debug/app-debug.apk`. `local.properties` é criado no seu computador pelo Android Studio.

## Documentação

- [Frontend, mascotes e animações](FRONTEND-LUME.md)
- [Pesquisa nativa e limitações](RESEARCH-NATIVE.md)

Validação no Mac: build e lint concluídos, 78 testes unitários e 4 instrumentados aprovados. Provedores públicos e interpretação por regras têm limitações; resultados inconclusivos não provam falsidade.

A exportação inclui fontes, recursos, testes e wrapper. Não inclui caches, ambientes virtuais, modelos, datasets, APKs, segredos ou configurações do Mac. As pastas auxiliares são referências antigas e não precisam ser executadas para usar o aplicativo atual.

Nenhum arquivo do produto oficial foi substituído.
