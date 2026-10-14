# Como os modelos locais apoiam a comparação de fontes (09/10/2026)

## Integração atual

O protótipo Android pode usar **dois recursos semânticos com funções diferentes**, além das regras estruturadas:

1. `paraphrase-multilingual`, no Ollama, ajuda a ordenar as fontes e comparar o assunto quando os papéis e a ação já coincidem.
2. O modelo treinado de pares, ativado por configuração local, combina o cosseno do MPNet ajustado, sobreposição lexical e a nota do BERTimbau ajustado. A nota apoia a triagem: pode sinalizar fontes como incertas e, com cortes mais altos, confirmar uma relação de mesmo acontecimento. A interface informa quando o modelo participou da decisão.

O modelo treinado não faz a busca, não substitui as regras e não determina se a notícia é verdadeira ou falsa. Para usá-lo, são necessários `lume-mpnet-ajustado` no Ollama, o servidor local do BERTimbau e `lume.pairModel.url` configurado no `local.properties`; os pesos não são incluídos no Git. Sem essa configuração, o app mantém a comparação estruturada e pode usar `paraphrase-multilingual` se estiver disponível. As instruções estão em [`SETUP.md`](../prototypes/ocr-native-android/SETUP.md).

## Avaliação exploratória anterior (09/10/2026)

Teste no emulador com 8 pesquisas reais do dia, depois das otimizações de tempo. Cada fonte recebeu a decisão da regra do app e a
nota do modelo (log `modelo par:`). **A avaliação de "certo/errado" abaixo foi feita por mim (IA), lendo os títulos; não é rótulo humano.**
Notícias ao vivo: refazer o teste muda as fontes.

Esta avaliação registra o comportamento observado antes dos ajustes descritos adiante. Os números são exploratórios e não substituem a avaliação controlada do conjunto reservado.

## Números das 8 pesquisas (392 pares com nota)
| Decisão da regra | modelo abaixo do corte | modelo acima do corte |
|---|---|---|
| mesmo acontecimento / direto | 17 | 30 |
| contexto | 105 | 20 |
| outro acontecimento / descartado | 193 | 18 |
| incerta | 7 | 2 |

40 fontes foram promovidas (a regra não confirmou e o modelo passou do corte).

## Onde ele acertou (ganho real)
- **Nobel da Paz 2026**: a regra não reconheceu a ação na frase e não confirmou nenhuma fonte. O modelo trouxe 24 fontes,
  praticamente todas sobre o mesmo prêmio (algumas são perfis "Quem é Navi Pillay", que tratam do mesmo fato).
- **Seleção, amistosos de outubro**: trouxe "Seleção brasileira: veja convocação para amistosos de outubro" (0,95), que a regra deixou incerta.

## Onde ele errou (falsos positivos)
- **Flamengo no STF**: promoveu 7 fontes e nenhuma é o mesmo fato. São etapas seguintes do mesmo assunto ("Fux autoriza Flamengo
  a participar…", "Palmeiras seguirá Flamengo…", "Após Flamengo, 17 times…"). A regra já as tinha corretamente como contexto.
- **Incêndio no Pantanal** (afirmação vaga): promoveu incêndios em quitinetes de Cuiabá, boxes em Ponta Porã e um de 9 de janeiro.
- É o mesmo limite já medido nos testes: o modelo confunde **mesmo assunto em outra etapa, data ou lugar** com o mesmo fato.

## Onde ele discorda da regra, e parece ter razão
A regra disse "mesmo fato" e o modelo deu nota baixa:
- Lula/IR: "Lula **vai sancionar** … na próxima semana" (0,40) e "O que muda com a nova lei…" (0,20): outra etapa e texto explicativo.
- Vacina/SUS: "SUS **suspende** uso de vacina da dengue" (0,17–0,22): outro fato.
- Mas também errou ao discordar: Selic "BC mantém taxa em 15%…" (0,53–0,78) é o mesmo fato.
Na rodada histórica descrita aqui, essas notas baixas não alteravam a tela. O ajuste posterior abaixo passou a exibir um aviso "confira" para notas inferiores a 0,20, sem rebaixar a fonte.

## Leitura
- **Vale manter ligado**: ele cobre o caso em que a regra falha por completo (frases sem ação reconhecida, como o Nobel), e os erros
  ficam contidos na seção "Incerta", com selo próprio e aviso.
- **Não vale promover mais do que isso**: nos assuntos com várias etapas (Flamengo), ele erra muito.
- Ajustes sugeridos, a medir antes: (1) subir o corte para ~0,90 só quando a regra reconheceu a ação (no Flamengo, sobraria 2 de 7
  erros; o Nobel quase não muda); (2) usar a nota muito baixa (< 0,3) como **aviso** "confira" em fontes que a regra confirmou,
  sem rebaixar. As duas mudanças precisam ser medidas nos 1.650 pares antes de entrar.
- Ressalvas: 8 pesquisas, julgamento meu (IA), e não se sabe se os dados de treino do Matheus incluem os nossos pares.

## Velocidade (com a etapa "entendendo a pergunta" em paralelo)
Do toque em Pesquisar: **prévia em 2,5 a 3,7 s** e **resultado final em 6,6 a 7,8 s** (antes, de manhã: 10 a 14 s até o resultado).
A pesquisa agora começa junto com o esclarecimento; se o esclarecimento precisar perguntar algo, a pesquisa antecipada é cancelada e
nada dela aparece.

## Ajuste aplicado (09/10, à tarde): cortes escolhidos no desenvolvimento dos 1.650 pares
Script: `lume/ml/integracao_modelo_matheus/5_escolher_cortes.py` (treino+validação; o teste não foi lido). Nenhum modelo foi retreinado:
só mudaram os limites da nota.
- Promoção quando a regra não confirmou: 0,78 → 80% das promovidas certas (119 recuperadas); 0,86 → 86% (67); 0,90 → 87% (45).
  Subir o corte tira pouco erro e muito acerto. Aplicado: **0,86 quando a regra reconheceu a ação**; **0,78 quando não reconheceu**.
- Aviso "confira" quando a regra confirmou e a nota é **< 0,20**: no desenvolvimento, a regra estava errada em 10 de 11 desses casos.
  Texto: "O modelo de linguagem indica que pode ser outro fato; confira a fonte." A fonte não é rebaixada.
- Ressalva: a regressão foi ajustada no treino, então os números do treino são otimistas.

Teste no app depois do ajuste: Flamengo 7 → 3 promoções erradas; Nobel continua com 24 promovidas; avisos em
"O que muda com a nova lei que isenta IR…" (0,20), "SUS suspende uso de vacina da dengue…" (0,17) e "Chikungunya e dengue: quem pode
tomar as vacinas no SUS" (0,16). 200 testes passam.

## Melhoria geral (09/10, fim da tarde): correção do hífen e confirmação pelo modelo
Medido com o harness da v3 (`dataset-piloto-2026-10-05/evaluation/run_stories_eval.sh`, modo trecho) e os scripts
`6_avaliar_regra.py` e `7_regra_mais_modelo.py`, só no desenvolvimento (1.398 pares; o teste não foi lido).
1. **Hífen**: termos como "sul-africana", "ex-presidente" e "Covid-19" nunca casavam na triagem por termos (o texto da fonte é quebrado
   em palavras). Corrigido em `Relevance.kt`. Efeito na regra: recall 48,2% → 48,6%, falso positivo 7,3% → 7,6%.
2. **Modelo confirma "mesmo fato" com nota alta**: 0,90 quando a regra não reconheceu a ação; 0,93 quando reconheceu (onde os erros
   são outra etapa do mesmo assunto). Regra sozinha: recall 48,6%, FP 7,6%, precisão 81,0%. Regra + modelo: **recall 54,6%, FP 8,1%,
   precisão 81,8%**. Só na validação (fora do ajuste da regressão): recall 40,0% → 44,6%, FP igual (7,8%).
   Na tela, essas fontes aparecem em "Mesmo fato" com o selo azul "Confirmado pelo modelo de linguagem".
3. Entre 0,78/0,86 e o corte de confirmação, o modelo continua só promovendo para "Incerta".

No app (6 pesquisas): "Nobel da Paz 2026 vai para Navi Pillay, jurista sul-africana" passou de 0 para 10 fontes "mesmo fato";
a versão longa da frase teve 5. Flamengo: nenhum erro foi para "Mesmo fato" (as notas 0,91 ficaram em "Incerta"). 200 testes passam.
