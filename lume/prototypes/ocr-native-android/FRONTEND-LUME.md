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

## Redesign editorial (30/09/2026)

Só a camada de interface mudou; OCR, pesquisa, `ResearchService` e a integração com o Ollama ficaram como estavam.

- **Base visual** (`ui/theme`): fundo de papel claro, cartões brancos com fio fino, sem sombra nem gradiente. Verde só em ações, títulos de seção e etiquetas; âmbar para avisos. Títulos de notícia e trechos citados em serifa; interface em sem serifa.
- **Peças comuns** (`LumeUi.kt`): `LumeCard`, `SectionHeader`, `Overline`, `Tag`, `QuoteBlock`, `Collapsible`/`ExpanderRow`, `Reveal`, `StageProgress`, `LumePresence`.
- **Leitura da imagem** (`ReadingSummary.kt`): prévia da captura enquadrada (toque para ampliar), manchete ao lado e dados em duas colunas alinhadas, cada um com a origem. O que não foi identificado vira uma linha só; texto lido e blocos ignorados ficam em "Ver o texto lido na imagem".
- **Resultados** (`ResearchResults.kt`): resumo com contagens no topo; cada publicação segue a ordem veículo e data, título, relação, trecho, "Por que esta relação?" e "Abrir fonte". Resumos que só repetem o título ou a etiqueta não são mostrados. Grupos secundários e "Como a pesquisa foi feita" começam fechados.
- **Movimento**: chegada escalonada dos cartões, expansão suave, resposta ao toque e Lume discreto no andamento. Tudo passa por `LocalLumeMotion`, que desliga com a preferência do app ou com as animações do Android desativadas.
- **Acessibilidade**: com fonte a partir de 130% os botões e as contagens empilham; alvos de toque de 48dp; o teclado recolhe ao iniciar a pesquisa.

Conferido em emulador Pixel 7 com fonte 100% e 160% e com animações desligadas: 127 testes unitários e 13 instrumentados passando.

## Gotinha flutuante com vida (30/09/2026)

`mascot/MascotMotion.kt` cuida só dos movimentos; `MascotService` continua dono da janela, do painel, das permissões e da captura.

- **Repouso**: uma respiração leve (sobe 1 dp e estica 1,8%) a cada 6–10 s, e piscadas trocando pela imagem de olhos fechados do mesmo desenho a cada 3,5–8 s. Sem toque por 90 s fica mais calma; depois de 5 min só pisca.
- **Reações**: achata ao encostar o dedo, ergue levemente ao arrastar, pequeno rebote ao soltar, ergue ao abrir o painel e assenta ao fechar, aceno curto ao pedir "Abrir análise" ou "Ler esta tela", acomoda ao encaixar na borda e reaparece piscando depois da captura. Nenhuma indica pesquisa ou leitura em andamento.
- **Arraste e encaixe**: segue o dedo sem atraso; ao soltar, desliza até a borda mais próxima desacelerando (150–320 ms), sem passar dela. Ao girar a tela, fica na mesma borda e na mesma altura relativa.
- **Ciclo de vida**: não há laço contínuo, só tarefas agendadas no Handler principal. Tudo para com a gotinha oculta (captura), a tela desligada, a desativação, a preferência "Animações suaves" desligada ou as animações do Android em 0. As imagens são decodificadas no tamanho exibido.

## Painel da gotinha em balão (30/09/2026)

`mascot/MascotPanel.kt` desenha o painel; `MascotService` só decide quando abrir e fechar e o que cada ação faz (sem mudança de lógica).

- Cartão branco compacto (248 dp), cantos de 20 dp, fio fino, sombra discreta e uma ponta de balão voltada para a gotinha.
- Título "Posso ajudar?" e um "×" pequeno (alvo de 48 dp) para fechar. "Ler esta tela" é o botão principal (verde escuro), "Abrir análise" o secundário (verde claro), "Desativar mascote" um texto discreto no rodapé.
- Posição (`PanelLayout.place`): abre para o lado oposto à borda da gotinha, alinhado à altura dela; se a largura não couber, vai para cima ou para baixo. Sempre dentro da área útil (sem barras do sistema); com fonte grande em tela baixa, o conteúdo rola.
- A janela tem só o tamanho do cartão: toques fora passam para o app de baixo e fecham o painel; tocar na gotinha também fecha.
- Abre a partir da ponta (escala 0,92 → 1 e opacidade, 170 ms) e fecha em 120 ms; sem animação quando o Android ou o app pedem menos movimento.

## Gotinha de slime: linguagem de movimento (30/09/2026)

- **Técnica**: `mascot/SlimeView.kt` desenha a mesma imagem com `Canvas.drawBitmapMesh` numa malha 10×12. A forma cede (achata, estica, inclina a partir da base, com a barriga cedendo mais que a ponta) sem trocar o desenho. `mascot/MascotMotion.kt` move essa malha e a janela com molas amortecidas (`Spring`), num laço do Choreographer que só roda enquanto algo se move.
- **Entrada**: a gotinha começa atrás da borda, espia, se encolhe para tomar impulso, sai esticada com o corpo atrasado, passa um pouco do lugar, achata ao frear, balança e pisca. Ao voltar da captura, a mesma entrada, mais curta. Ao desativar, se encolhe e escorrega de volta pela borda.
- **Toque e arraste**: cede no mesmo quadro do toque e volta com um balanço; no arraste a janela segue o dedo e o corpo fica erguido, atrasa e estica no sentido do movimento; ao soltar, desliza com a velocidade do gesto (um gesto rápido escolhe a borda) e amassa ao encostar nela.
- **Painel**: enquanto aberto, a gotinha fica levemente esticada e inclinada para ele; ao fechar, solta e balança.
- **Repouso**: respiração lenta de vez em quando e piscadas (só troca de imagem); depois de 5 min sem toque, só pisca.
- **Limites**: animações reduzidas no Android ou desligadas no app → tudo vai direto ao estado final; tela desligada, gotinha oculta ou serviço parado → sem quadros nem tarefas pendentes. A escala de duração do sistema é respeitada.
- **Asset**: com uma imagem única, a boca e os olhos deformam junto com o corpo. Para uma deformação mais orgânica (olhos que acompanham, boca que muda, contorno que se estica sem distorcer o rosto) seria preciso o desenho em camadas: corpo, olhos, brilho dos olhos e boca separados, com o contorno do corpo como forma vetorial.
