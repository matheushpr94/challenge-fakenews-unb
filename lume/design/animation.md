# Movimento do Lume — 0.3

Tese visual: preservar a gotinha jade e dar presença com movimentos pequenos, sem chamar a atenção durante a leitura. A interface mantém as ações existentes, com uma opção discreta de pausa.

## Comportamento

- Repouso: respiração de 6,2 segundos e inclinação de aproximadamente 0,6 grau.
- Piscada: intervalos entre 3,6 e 7,2 segundos, fechamento breve e reabertura suave.
- Cumprimento: reação de 650 ms ao tocar no mascote grande; também pode ser acionada pelo teclado.
- Arraste: inclinação limitada a oito graus, sem modificar a posição ou o tamanho da área de toque.
- Espera: movimento curto e repetido no cabeçalho durante a análise; não indica que o conteúdo foi confirmado como verdadeiro.

A web pausa em aba oculta e fora da área visível. **Pausar animações**, no rodapé, guarda a preferência local. `prefers-reduced-motion` prevalece sobre a preferência de animação.

O Android pausa quando a view é removida/oculta, a tela é apagada, o aparelho entra em economia de bateria ou as animações do sistema estão desativadas. **Mais opções → Animar mascote** controla a preferência local. Animadores e observadores são removidos junto da view. Nenhum movimento depende da IA ou de conexão.

## Arquivos

- [Imagem original, olhos abertos](lume-mascot.png).
- [Novo quadro, olhos fechados](lume-mascot-blink.png), PNG 1254 × 1254 com transparência.
- A cópia usada pela web está em `web/public/assets/lume-mascot-blink.png` e a cópia nativa em `android/app/src/main/res/drawable-nodpi/lume_mascot_blink.png`, relativas a `lume/`.
- Código: `web/public/motion.mjs` e `android/app/src/main/java/app/lume/mvp/MascotView.kt`.

O novo quadro foi criado com a ferramenta integrada de geração de imagens (`image_gen`), em modo de edição da imagem original. O original permanece intacto. Não houve chamada ao servidor de IA do aplicativo para produzir ou executar a animação.

## Prompt final do quadro de piscada

```text
Use case: precise-object-edit. This PNG is the edit target, an existing Lume mascot UI animation frame. Create exactly ONE new animation frame, square and on a genuinely transparent background. Change ONLY the eyes: both eyes are gently fully CLOSED during a natural blink, forming two thin dark-green slightly curved eyelid lines at the vertical centers of the original eyes. The eyelid area must use the same pale jade body material and shading, without black eye ovals visible underneath. Preserve the EXACT original silhouette, body shape, tilt, top bump, mouth/smile, expression, material texture, lighting, color, camera, size, framing, position and alpha boundary. Do not redesign, move, crop, zoom, rotate or recolor the mascot. Original placement and scale within the square must remain identical, because this frame will alternate with the original at 120ms. Keep transparent margins. No floor, shadow background, text, extra objects or new facial features. Return a single square transparent PNG, preferably matching the original 1280x1280 canvas.
```

## Validação

O quadro foi inspecionado como imagem; proporção quadrada e transparência foram verificadas. Testes de lógica cobrem piscada, reabertura, pausa, redução de movimento, aba oculta, arraste e visibilidade na web. Android compilou nas variantes demo e connected. Ainda falta observar as animações em execução no navegador e em aparelhos físicos; não foram medidas fluidez ou bateria.

Referências de implementação: [ValueAnimator](https://developer.android.com/reference/android/animation/ValueAnimator#areAnimatorsEnabled()) e [prefers-reduced-motion](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-reduced-motion).
