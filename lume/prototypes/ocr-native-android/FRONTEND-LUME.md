# Frontend nativo do Lume

Implementação em Kotlin/Jetpack Compose no projeto Android existente. A pesquisa é executada pelo módulo nativo `research/`; esta interface não utiliza WebView nem servidor do protótipo web. OCR, extração de metadados, interpretação, cancelamento, fontes e comparação existentes foram preservados.

## Identidade e assets

- `app/src/main/res/drawable/lume_home.png`: personagem arredondado original, recortado do screenshot fornecido (180 × 180 px). Não foi redesenhado. Um original de maior resolução poderá substituir este arquivo mantendo as proporções.
- `app/src/main/res/drawable/lume_mascot.png`: gotinha original transparente já disponível no projeto. Usada na sobreposição, sem deformação.
- Paleta: fundo `#FAFAF5`, botão principal `#2B4035`, secundário `#D9E5D0`.

## Fluxos

A tela inicial oferece **Ativar mascote**, **Colar texto ou link** e **Simular captura**. A última ação abre a seleção de imagem do Android. A leitura dessa imagem usa ML Kit; não há captura automática da tela. Links públicos são lidos pelo fetcher existente, com timeout e validação de URL; título e descrição orientam a pesquisa. Links fechados ou ilegíveis pedem que o usuário cole o texto ou importe um print.

A análise mantém a imagem, texto editado e resultados na sessão mesmo ao visitar Perfil ou girar a tela. `AnalysisState` é um ViewModel em memória, sem persistência de capturas em disco. Encerrar o processo encerra essa sessão. Preferências de animação são armazenadas localmente.

Afirmações continuam separadas internamente. Quando uma notícia tem múltiplos detalhes, a interface agrupa a apresentação, deduplica fontes por URL e evita uma classificação global. As fontes que esclarecem detalhes ficam visíveis; publicações apenas relacionadas ficam recolhidas. Limitações da comparação continuam disponíveis, sem repetir cartões técnicos para cada frase.

**Perfil** contém preferências locais de animação, estado/desativação do mascote e informações reais de funcionamento. Não há login nem configurações de endereço, porta ou backend na interface normal.

## Sobreposição

`MascotService` usa WindowManager e serviço em primeiro plano. A permissão de sobreposição é explicada e solicitada pelo fluxo oficial do Android. “Ativado” depende de uma janela adicionada e serviço iniciado, não apenas de tocar no botão. Android 13+ solicita permissão de notificação. A notificação do serviço contém uma ação de desativação.

A gotinha tem uma janela de 76 dp, permite arraste e encaixa na borda com animação controlada. O painel tem ações para abrir a análise, fechar opções e desativar. O painel é rolável quando o espaço é pequeno ou a fonte ampliada. Barras do sistema e recortes são descontados dos limites.

Não há MediaProjection, leitura contínua, monitoramento de outros apps nem envio de conteúdo ao ativar a gotinha. Abrir a análise leva ao fluxo de texto/importação. Não se simula captura real.

## Animações

Entrada e respiração discretas do personagem, reação ao toque, transição de navegação, expansão de fontes e entrada/saída do painel. A sobreposição acompanha o dedo diretamente e só anima o encaixe depois de soltar. Movimentos de espera do personagem param quando sua tela está oculta. O serviço interrompe animações com a tela desligada.

As animações respeitam a escala do Android e a preferência local **Animações suaves**. Preferência local desativada remove também as transições de expansão. Não há progresso inventado, nem atraso de ação para esperar animações.

## Verificação

No terminal do projeto, com JDK do Android Studio:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

Os testes instrumentados exigem emulador ou aparelho conectado. Espresso foi atualizado para 3.7.0 porque a versão antiga acessava `InputManager.getInstance`, removido no Android usado pelo emulador. Referência: https://developer.android.com/jetpack/androidx/releases/test

APK de teste: `app/build/outputs/apk/debug/app-debug.apk`.

A validação deste trabalho cobre compilação, testes unitários da pesquisa/OCR, testes de navegação e preservação do texto na rotação, lint e inspeção visual no emulador. Não substitui avaliação em aparelhos físicos com diferentes fabricantes e níveis de desempenho. A lógica de interpretação/pesquisa continua baseada nas regras do módulo existente; este trabalho não acrescenta um modelo de linguagem nem garante que toda pergunta possa ser esclarecida.

## Resultado da conferência em 28/09/2026

- Compilação de debug concluída.
- 78 testes unitários aprovados (OCR, interpretação, pesquisa, relevância, evidências e concorrência).
- 4 testes instrumentados aprovados: contexto, ações da tela inicial, navegação/rotação com texto preservado e análise integrada com publicações relacionadas recolhidas.
- Lint concluído sem erros bloqueantes; advertências existentes podem ser consultadas no relatório gerado.
- Conferência manual no emulador: permissão de sobreposição não concedida, concessão pelas configurações do Android, retorno ao app, notificação negada, serviço em primeiro plano, presença no launcher e Chrome, arraste/encaixe, abertura da análise, desativação pelo painel (serviço e janelas removidos), rotação e fonte ampliada a 160%. Importação real de imagem e leitura pelo ML Kit também conferidas.
- O Android pode ocultar sobreposições em telas protegidas, como certas configurações de segurança; a gotinha continua em aplicativos que permitem sobreposição.
