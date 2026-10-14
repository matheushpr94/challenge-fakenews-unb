# Lume

Mascote para Android que ajuda a entender notícias e posts a partir de um trecho escolhido pela pessoa. Este diretório é a base versionada do produto dentro do challenge.

**Estado: protótipo nativo em desenvolvimento.** A versão atual está em [`prototypes/ocr-native-android`](prototypes/ocr-native-android): OCR no aparelho (ML Kit), leitura da matéria, busca de fontes, comparação de "mesmo acontecimento" com trechos que sustentam a análise e **sinal do texto** (estilo da escrita). O app combina comparação estruturada com recursos semânticos locais: `paraphrase-multilingual` para ordenar/relacionar textos, o modelo treinado de pares (MPNet ajustado, sobreposição lexical e BERTimbau ajustado) como apoio à comparação e um SVM do FakeRecogna para o estilo do texto. Nada disso afirma se a notícia é verdadeira ou falsa.

- [Fluxo completo do app](docs/FLUXO-COMPLETO-DO-LUME.md) e [versão resumida](docs/FLUXO-RESUMIDO-DO-LUME.md)
- [Sinal do texto: como funciona, calibração e limites](docs/SINAL-DO-TEXTO.md)
- [Como o modelo de pares entra no app](docs/RELATORIO-MODELO-MATHEUS-NO-APP.md)
- [Medições de desempenho](docs/MEDICOES-DESEMPENHO.md)
- [Validações e limitações](docs/validacao.md)
- [Histórico de mudanças](CHANGELOG.md)

A versão anterior (demo offline com respostas fictícias) continua disponível como referência: [prévia no navegador](https://lume-previa.vercel.app/) e [APK demo 0.3.0](https://lume-previa.vercel.app/downloads/lume-demo-0.3.0.apk).

## Organização

```text
lume/
├── prototypes/   # Protótipo Android nativo atual (ocr-native-android) e a versão anterior de referência
├── ml/           # Modelos e scripts: modelo de pares, sinal do texto e script que liga tudo
├── android/      # Versão anterior (demo): mascote, captura e variantes demo/connected
├── server/       # Servidor da futura análise conectada; testes com provedor simulado
├── web/          # Prévia anterior publicada na Vercel, com três cenários
├── design/       # Mascote original e direção visual
├── docs/         # Fluxo do app, IA, validação e medições
└── CHANGELOG.md
```

O modelo treinado de pares está integrado opcionalmente ao protótipo Android; ele auxilia a comparação entre uma afirmação e títulos de fontes encontradas. A integração não é o classificador de veracidade acadêmico e não dá um veredito sobre a notícia. Veja [como o modelo entra no app](docs/RELATORIO-MODELO-MATHEUS-NO-APP.md) e os requisitos locais em [`prototypes/ocr-native-android/SETUP.md`](prototypes/ocr-native-android/SETUP.md). O trabalho acadêmico com LIAR2 continua separado.

## Testar no Android

1. Siga o [`SETUP.md`](prototypes/ocr-native-android/SETUP.md) do protótipo e instale o app no emulador ou no celular (Android Studio, **Run**).
2. Para usar os modelos locais, rode `bash lume/ml/iniciar_modelos_locais.sh`. Ele liga o Ollama, o servidor do BERTimbau (porta 8765) e o do sinal do texto (porta 8766) e faz o `adb reverse`. Sem esses serviços, o app funciona só com as regras.
3. Abra o Lume e toque em **Ativar mascote**; permita **exibir sobre outros apps**.
4. Com uma notícia aberta no navegador, toque no mascote e em **Ler esta tela**; autorize a captura. A imagem fica no aparelho e é apagada depois da leitura. Também dá para compartilhar um texto, link ou imagem, colar um texto ou importar uma imagem.
5. Opcional: em **Ajustes › Endereço da matéria**, ative o serviço de acessibilidade do Lume. Ele lê só o endereço da página no navegador, no toque do mascote, para abrir a matéria completa e medir o estilo mesmo em prints só com a manchete.

O funcionamento em aparelhos físicos ainda precisa ser validado. Os testes até aqui foram em emulador e com o Chrome.

O mascote pisca, respira suavemente e inclina ao ser arrastado. Toque no mascote grande para cumprimentá-lo. Em **Ajustes › Animações suaves**, é possível pausar o movimento. A animação também respeita a configuração do sistema, a visibilidade e a economia de bateria. [Arte e comportamento](design/animation.md).

## Desenvolver

**Android (protótipo atual):** JDK 17+ e Android SDK 37. Na pasta `prototypes/ocr-native-android/`, execute:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

No Windows, use `.\gradlew.bat`. Saída: `app/build/outputs/apk/debug/app-debug.apk`. O endereço do Ollama e dos servidores locais vem do `local.properties` (modelo em `local.properties.example`), gravado no app na compilação. Detalhes de Windows e macOS no [`SETUP.md`](prototypes/ocr-native-android/SETUP.md).

**Versão anterior (demo):** na pasta `android/`, `./gradlew :app:assembleDemoDebug :app:testDemoDebugUnitTest :app:lintDemoDebug`. O manifesto da variante `demo` remove a permissão INTERNET; a variante `connected` tem identificador separado, `app.lume.mvp.connected`, e depende de um servidor configurado. Veja [como conectar o piloto](docs/piloto-conectado.md).

**Servidor:** Node.js 22+. Testes sem credenciais e sem chamadas pagas:

```sh
cd server
npm test
```

**Prévia web:** veja [web/README.md](web/README.md). Os testes da rubrica rodam com `node --test tests/evidence.test.mjs` dentro de `web/`.

## Versionar aos poucos

Faça mudanças pequenas por capacidade: captura, interface, integração da IA ou avaliação. Registre comportamento, validação e limitações no changelog. O código desta pasta é a referência do produto; cópias exportadas e o site publicado são entregas derivadas.

Não versionar chaves, configurações locais da Vercel, dados pessoais, caches, APKs ou pesos de modelos. O Gradle Wrapper é incluído para reproduzir o build. APKs devem ser distribuídos como artefatos ou releases, com versão e checksum.

O APK público atual foi assinado com uma chave debug local não versionada. Um build em outra máquina terá outra assinatura e não poderá atualizar por cima dele sem reinstalação. Antes de um piloto permanente, definir uma chave de assinatura protegida e um processo de release.

## Próximos incrementos

1. Validar instalação, permissões, captura e o serviço de endereço em aparelhos físicos e em navegadores além do Chrome.
2. Respeitar o `robots.txt` e limitar os pedidos por domínio no app (hoje só o coletor de calibração faz isso).
3. Expor os modelos locais por uma API para o celular funcionar sem cabo, mantendo o app útil só com as regras quando ela estiver fora do ar.
4. Ampliar a calibração do sinal do texto (hoje 81 notícias de um dia) e confirmar a origem e a licença do modelo exportado.
5. Avaliar classificação, evidências, abstenção e calibração em dados separados antes de qualquer resultado numérico para o usuário.

Licença: [MIT do repositório](../LICENSE).
