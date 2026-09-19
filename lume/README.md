# Lume

Mascote para Android que ajuda a entender notícias e posts a partir de um trecho escolhido pela pessoa. Este diretório é a base versionada do produto dentro do challenge.

**Estado: 0.3.0-demo.** O APK distribuído funciona offline, tem respostas fictícias e não analisa o conteúdo capturado. Não faz pesquisas nem consome tokens de IA. O índice de evidências demonstrativo não é uma probabilidade de verdade.

- [Experimentar no navegador](https://lume-previa.vercel.app/)
- [Baixar APK demo 0.3.0](https://lume-previa.vercel.app/downloads/lume-demo-0.3.0.apk)
- [IA, NLP e significado do percentual](docs/ia-e-confiabilidade.md)
- [Validações e limitações](docs/validacao.md)
- [Histórico de mudanças](CHANGELOG.md)

## Organização

```text
lume/
├── android/   # Kotlin: mascote, captura e variantes demo/connected
├── server/    # Servidor da futura análise conectada; testes com provedor simulado
├── web/       # Prévia atual publicada na Vercel, com três cenários
├── design/    # Mascote original e direção visual
├── docs/      # Decisões de IA, validação e conexão do piloto
└── CHANGELOG.md
```

O trabalho acadêmico com LIAR2 continua nas pastas originais do repositório. A interface, o servidor e o app vivem aqui. Não há modelo treinado nem integração do classificador do challenge com o produto neste commit.

## Testar no Android

1. Baixe e instale o APK. O Android pode pedir autorização para instalar pelo navegador ou aplicativo de arquivos.
2. Abra o Lume e toque em **Ativar mascote**; permita **exibir sobre outros apps**.
3. Use **Testar um exemplo** para explorar os cenários de 50%, 90% e sem base para avaliar.
4. Para testar uma captura real, toque no mascote sobre outro aplicativo e autorize a captura. Ela permanece no aparelho; a resposta da demo usa o exemplo escolhido.

Critérios e fontes fictícias ficam recolhidos em **Entender a avaliação**. As opções secundárias ficam em **Mais opções**. O funcionamento em aparelhos físicos ainda precisa ser validado.

O mascote pisca, respira suavemente e inclina ao ser arrastado. Toque no mascote grande para cumprimentá-lo. Em **Mais opções → Animar mascote**, é possível pausar o movimento. A animação também respeita a configuração do sistema, a visibilidade e a economia de bateria. [Arte e comportamento](design/animation.md).

## Desenvolver

**Android:** JDK 17+ e Android SDK 35. Na pasta `android/`, execute:

```powershell
.\gradlew.bat :app:assembleDemoDebug :app:testDemoDebugUnitTest :app:lintDemoDebug
```

Em Linux/macOS, use `./gradlew`. Saída: `android/app/build/outputs/apk/demo/debug/app-demo-debug.apk`.

O manifesto da variante `demo` remove a permissão INTERNET e a função da API bloqueia chamadas nessa variante. A variante `connected` tem identificador separado, `app.lume.mvp.connected`, e depende de um servidor configurado. Veja [como conectar o piloto](docs/piloto-conectado.md).

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

1. Validar instalação, permissões, captura e navegação em aparelhos físicos.
2. Conectar a variante connected à análise por afirmação com fontes reais, mantendo a demo offline.
3. Avaliar classificação, evidências, abstenção e calibração em dados separados.
4. Só então definir o significado de um percentual para uso real, com validação específica em português.

Licença: [MIT do repositório](../LICENSE).
