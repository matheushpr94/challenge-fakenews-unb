# Ficha do modelo: "mesmo fato" (6B), ciclo 2

| Campo | Valor |
|---|---|
| Pergunta | esta fonte, achada pela busca do app, relata o mesmo acontecimento que a notícia? |
| Tipo | classificação binária; regressão logística (scikit-learn 1.9.1) sobre 34 colunas mais a nota de um BERTimbau ajustado |
| Configuração | `C = 0,03`, `class_weight = "balanced"`, peso 1 para os rótulos desempatados |
| Limiar | nota >= 0,874239 (escolhido na validação, no ponto em que a precisão iguala a da regra do app) |
| Plano B | o mesmo sem a nota do BERTimbau: `C = 0,1`, limiar 0,853788 |
| Entradas | `cosseno` (MPNet ajustado), `sobreposicao`, `nota_texto` (BERTimbau), `idade_dias`, `tem_idade`, `comparou`, `titulo_cortado`, `quem`, `acao`, `assunto`, `quando`, `buscador`, `tipo_afirmacao` |
| Saída | nota de 0 a 1 (não é uma probabilidade calibrada: 0,75 vale uns 26% de "mesmo"; 0,97, uns 87%) |
| Treino | 7.920 pares de 182 notícias (rodadas 1 a 3), rótulos de dois modelos de linguagem independentes, com desempate e validação por amostra humana |
| Validação | 2.374 pares de 49 notícias (rodada 3) |
| Teste | 4.298 pares de 100 notícias (rodada 4, separada antes da busca), avaliado uma vez em 08/10/2026 |
| Resultado no teste | cobertura de 75,8% com precisão de 74,3%; a regra do app, 35,7% e 75,0%; a do MVP, 34,8% e 73,3% |
| Onde erra | fontes de 2 a 30 dias (55% de acerto) e de mais de um mês (25%); mesmo assunto contado por outro recorte |
| Uso no app | selo "provável mesmo fato" só para fonte do mesmo dia ou da véspera; nas outras, "mesmo assunto, confira a data"; a regra do app continua valendo onde ela já diz "mesmo acontecimento" |
| Dependências | o `cosseno` precisa do `lume-mpnet-ajustado` (Ollama); a `nota_texto`, do BERTimbau ajustado num servidor |
| Não usar para | dizer se a notícia é verdadeira; só diz se a fonte fala do mesmo fato |

## Calibração da nota (acrescentada em 23/10/2026, ciclo 3, exercício 4)

A nota bruta não é uma chance (nota 0,75 acerta 26%). O bloco `calibracao` do `decisor.json` traz uma correção de Platt
com dois números (inclinação 1,0095, posição −1,8969) aprendida nas notas fora da dobra do estudo e medida no simulado:
Brier de 0,062 para 0,032, erro de calibração de 0,105 para 0,014, decisão idêntica (o corte 0,874 na nota equivale a
0,515 na chance). A prova não foi reaberta. Com ela, nota 0,75 quer dizer "cerca de 3 em 10"; 0,97, "cerca de 8 em 10".
