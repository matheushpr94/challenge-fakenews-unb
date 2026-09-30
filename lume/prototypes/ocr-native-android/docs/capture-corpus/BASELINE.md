# Avaliação de capturas do Lume

Mede OCR e seleção do texto; **não avalia se o conteúdo é verdadeiro**. As frases esperadas são apenas o foco visível nas capturas.

**7 capturas**: OCR 6/7; tipo 7/7; foco 6/7; separação 6/7.

## eval-x-huck

- Cenário: Post principal do X com imagens e resposta de outro perfil abaixo.
- Foco esperado: Mensagens da PF revelam que Luciano Huck foi sócio, amigo e conselheiro de Daniel Vorcaro, diz Veja
- Tipo: esperado=post, observado=post, OK
- Título detectado: (nenhum)
- Sugestão editável: EURGENTE - Mensagens da PF revelam que Luciano Huck foi sócio, amigo e conselheiro de Daniel Vorcaro, diz Veja
- Foco: OK; detalhes ausentes na sugestão: nenhum
- OCR: OK; detalhes ausentes já no OCR: nenhum
- Mistura indevida: não detectada; na sugestão: nenhuma; no corpo: nenhuma
- Etapa a investigar: nenhuma falha medida
- Pede esclarecimento: false

## eval-x-almoco

- Cenário: Post do X com fotos, chamada de veículo e resposta de outro perfil.
- Foco esperado: Imagens mostram como foi o almoço 'ultra vip' organizado por Vorcaro para Moraes, diz O Globo
- Tipo: esperado=post, observado=post, OK
- Título detectado: (nenhum)
- Sugestão editável: Imagens mostram como foio almoço 'ultra vip' organizado por Vorcaro para Moraes, diz O Globo
- Foco: OK; detalhes ausentes na sugestão: nenhum
- OCR: OK; detalhes ausentes já no OCR: nenhum
- Mistura indevida: não detectada; na sugestão: nenhuma; no corpo: nenhuma
- Etapa a investigar: nenhuma falha medida
- Pede esclarecimento: false

## eval-x-sobretaxa

- Cenário: Post do X com número, indicação temporal, texto dentro de imagem e resposta abaixo.
- Foco esperado: China impõe sobretaxa de 55% sobre as carnes bovinas brasileiras a partir de amanhã
- Tipo: esperado=post, observado=post, OK
- Título detectado: (nenhum)
- Sugestão editável: China impõe sobretaxa de 55% sobre as carnes bovinas brasileiras a partir de amanhã
- Foco: OK; detalhes ausentes na sugestão: nenhum
- OCR: OK; detalhes ausentes já no OCR: nenhum
- Mistura indevida: não detectada; na sugestão: nenhuma; no corpo: nenhuma
- Etapa a investigar: nenhuma falha medida
- Pede esclarecimento: false

## eval-x-flamengo

- Cenário: Resultado de busca no X; consulta no topo e post com vários parágrafos.
- Foco esperado: Flamengo entra com pedido no STF contra Medida Provisória que proibiu as bets
- Tipo: esperado=post, observado=post, OK
- Título detectado: (nenhum)
- Sugestão editável: Flamengo se antecipa aos clubes e entra com pedido no STF contra Medida Provisória que proibiu as bets
- Foco: OK; detalhes ausentes na sugestão: nenhum
- OCR: OK; detalhes ausentes já no OCR: nenhum
- Mistura indevida: não detectada; na sugestão: nenhuma; no corpo: nenhuma
- Etapa a investigar: nenhuma falha medida
- Pede esclarecimento: false

## eval-g1-investigacao

- Cenário: Matéria com barra do portal, anúncio, botões de compartilhamento e aviso de cookies.
- Foco esperado: EUA abrem investigação inédita sobre OpenAI e Anthropic após agentes de IA invadirem sistemas
- Tipo: esperado=article, observado=matéria, OK
- Título detectado: EUA abrem investigação inédita sobre OpenAl e Anthropic após agentes de IA invadirem sistemas
- Sugestão editável: EUA abrem investigação inédita sobre OpenAl e Anthropic após agentes de IA invadirem sistemas
- Foco: FALHA; detalhes ausentes na sugestão: OpenAI
- OCR: FALHA; detalhes ausentes já no OCR: OpenAI
- Mistura indevida: não detectada; na sugestão: nenhuma; no corpo: nenhuma
- Etapa a investigar: OCR
- Pede esclarecimento: false

## eval-g1-whatsapp

- Cenário: Matéria com anúncio e texto de outra funcionalidade do WhatsApp dentro da imagem.
- Foco esperado: WhatsApp anuncia novos controles parentais para adolescentes no Brasil
- Tipo: esperado=article, observado=matéria, OK
- Título detectado: WhatsApp anuncia novos controles parentais para adolescentes no Brasil
- Sugestão editável: WhatsApp anuncia novos controles parentais para adolescentes no Brasil
- Foco: OK; detalhes ausentes na sugestão: nenhum
- OCR: OK; detalhes ausentes já no OCR: nenhum
- Mistura indevida: não detectada; na sugestão: nenhuma; no corpo: nenhuma
- Etapa a investigar: nenhuma falha medida
- Pede esclarecimento: false

## eval-metropoles-lei-seca

- Cenário: Título de guia com dois-pontos e ordinal; botão do site e crédito de imagem.
- Foco esperado: Lei Seca: saiba em quais estados é proibido beber no 1º turno das eleições
- Tipo: esperado=article, observado=matéria, OK
- Título detectado: Lei Seca: saiba em quais estados é proibido beber no 1° turno das eleições
- Sugestão editável: Lei Seca: em quais estados é proibido beber no 1° turno das eleições
- Foco: OK; detalhes ausentes na sugestão: nenhum
- OCR: OK; detalhes ausentes já no OCR: nenhum
- Mistura indevida: FALHA; na sugestão: nenhuma; no corpo: Adicione o Metrópoles
- Etapa a investigar: organização dos blocos
- Pede esclarecimento: false

