# Lume em resumo (versão simples)

Estado do protótipo em 14/10/2026. Os termos técnicos vêm com uma explicação entre parênteses.
A versão completa e detalhada é o arquivo `FLUXO-COMPLETO-DO-LUME.md`.

## O que é o Lume

Um app de celular (Android) com um **mascote flutuante**, uma gotinha. Você está lendo uma notícia ou um post, toca na gotinha, e o
Lume mostra **o que outras fontes dizem sobre aquele mesmo fato**.

## O que o Lume NÃO faz

- **Não diz se algo é verdadeiro ou falso.** Ele mostra fontes e como elas se relacionam com o texto.
- Quando mostra algo sobre o jeito de escrever, usa palavras como "tende a lembrar…" ou "sem sinal claro", nunca uma sentença.

## Passo a passo

1. **Você manda o conteúdo.** Toca na gotinha ("Ler esta tela"), ou compartilha, cola um texto/link ou escolhe uma imagem.
2. **O Lume lê a imagem no próprio celular.** Usa OCR (reconhecimento de texto em imagem). A foto não sai do aparelho e é apagada depois.
3. **Ele descobre o endereço da página.** Se você ativou o recurso, um serviço de acessibilidade (função do Android que "enxerga" a tela)
   lê o endereço do site no navegador, só quando você toca. Serve para abrir a matéria inteira depois.
4. **Entende o que é a notícia.** Separa título, texto, veículo, autor e data. Escolhe a **afirmação principal**
   (a frase que será pesquisada). Se houver dúvida, pergunta: "O que você quer saber?".
5. **Interpreta a frase com regras.** Identifica quem fez o quê, números, datas e negações.
   (São regras escritas à mão, não uma IA "adivinhando".)
6. **Busca outras fontes na internet.** Google Notícias, Bing Notícias, Bing Web e Wikipédia, sem chave paga. Depois abre as páginas mais
   relevantes para ler o texto delas.
7. **Tira repetições e a própria matéria.** Cópias da mesma notícia não contam como fontes diferentes
   (republicação: o mesmo texto publicado em outro site).
8. **Compara cada fonte com o texto lido.** Pergunta: é o **mesmo fato**? (mesma pessoa, mesma ação, mesma etapa, mesma data, mesmos
   números). Cada resposta vem com o **trecho exato** que a justifica.
9. **Calcula o estilo da escrita.** Se o texto tem 60 palavras ou mais, um modelo mede se o jeito de escrever lembra notícia comum ou
   boato. Se você só mandou a manchete, o Lume abre a página da própria matéria para ter texto suficiente.
10. **Mostra tudo na tela**, separado por tipo, com explicação de cada decisão.

## O que a IA faz (e o que não faz)

| Peça | Em palavras simples | Papel no app |
|---|---|---|
| **OCR** (ML Kit) | lê o texto que aparece na imagem | transforma o print em texto |
| **Regras do app** | conjunto de condições escritas pela equipe | **decidem** se a fonte trata do mesmo fato |
| **paraphrase-multilingual** (modelo de similaridade) | mede o quanto duas frases "querem dizer a mesma coisa" | ajuda a **ordenar** quais fontes ler primeiro |
| **Modelo de pares** (MPNet + BERTimbau + regressão logística) | três medidas de semelhança entre a frase e o título de cada fonte, combinadas numa nota | só **promove** uma fonte a "incerta" ou **avisa** "confira"; nunca rebaixa nem decide verdade |
| **SVM do FakeRecogna** (modelo de classificação treinado em notícias falsas e verdadeiras) | olha as palavras usadas e diz se o estilo lembra boato ou notícia | mostra o **estilo da escrita**, não a veracidade |

Tudo roda **no seu computador ou no celular**. Nenhuma IA em nuvem, nada pago.

## O que aparece na tela

- **Mesmo fato**: fontes que contam o mesmo acontecimento.
- **Incerta**: pode ser o mesmo fato, mas o app não tem certeza ("confira").
- **Anteriores / Contexto**: matérias do mesmo assunto, mas de outro momento ou etapa.
- **Outro acontecimento**: parece igual, mas é outra coisa.
- **Sinal do texto**: uma escala do estilo da escrita e as palavras que mais pesaram.
- **Já checado por…**: quando uma agência de checagem (Aos Fatos, Lupa etc.) tratou do assunto.
- **"Por que esta relação?"**: abre o motivo, com os trechos usados.
- **"Como a pesquisa foi feita"**: lista o que foi consultado e o que falhou.

## Privacidade

- A imagem é lida no celular e apagada em seguida.
- Os modelos de IA rodam localmente; o texto não vai para serviço de IA externo.
- O histórico fica só no aparelho, e você pode apagá-lo.
- O serviço de acessibilidade só lê o **endereço do site**, só quando você toca, e só em navegadores.

## Números principais (medidos)

- **Tempo:** a primeira prévia aparece em cerca de 2,5 a 3,7 s; o resultado final leva em torno de 5 a 10 s (emulador, varia com a carga
  da máquina).
- **Testes automáticos:** 214 passam, nenhum falha.
- **Modelo de pares:** com a regra sozinha, o app acha 48,6% das fontes do mesmo fato (e aceita 7,6% de fontes erradas); com o modelo
  junto, acha 54,6% (e aceita 8,1% erradas). Medido em 1.398 pares de desenvolvimento.
- **Sinal do texto:** em 81 notícias reais do dia, 2,5% foram marcadas como "tende a lembrar desinformação" (margem de erro de
  0,7% a 8,6%). Nos boatos testados, 69,6% foram marcados assim.

## Limites (ditos com franqueza)

- Mostra **fontes e estilo**, não a verdade.
- O estilo do texto reconhece o **tipo de texto** (por exemplo, um texto de checagem também parece "boato"), não a verdade do conteúdo.
- A calibração do sinal usou **81 notícias de um dia**, de veículos grandes.
- O app **ainda não respeita o robots.txt** (arquivo em que o site diz o que robôs podem ler) **nem limita pedidos por segundo**; precisa
  ser tratado antes de publicar.
- Foi testado em **emulador**; falta testar em aparelho físico e em outros navegadores além do Chrome.
- Páginas com assinatura paga (paywall) ou que carregam o texto com JavaScript podem não ser lidas.
- Para o modelo funcionar, o computador precisa estar com os servidores locais ligados.

## Mini-glossário

- **OCR**: tecnologia que transforma texto de uma imagem em texto digital.
- **Acessibilidade (serviço)**: recurso do Android que permite a um app ler elementos da tela, com permissão do usuário.
- **Modelo de linguagem**: programa treinado com muito texto para medir semelhança ou classificar frases.
- **SVM**: tipo de modelo que separa dois grupos (aqui, "estilo de boato" e "estilo de notícia").
- **Lematizar**: reduzir cada palavra à forma base ("correram" → "correr").
- **Regressão logística**: fórmula que combina várias medidas em uma nota entre 0 e 1.
- **Corte**: nota mínima para o app tomar uma atitude (promover, avisar).
- **Recall**: de todas as fontes certas, quantas o app achou. **Falso positivo**: fonte errada aceita como certa.
- **Calibração**: ajustar os cortes usando dados reais para controlar quantos erros o modelo comete.
- **Republicação**: a mesma matéria publicada por outro veículo.
