# Ficha do modelo: "mesmo fato" (6B), ciclo 2

| Campo | Valor |
|---|---|
| Pergunta | esta fonte, achada pela busca do app, relata o mesmo acontecimento que a notícia? |
| Tipo | classificação binária; regressão logística (scikit-learn 1.9.1) sobre 34 colunas mais a nota de um BERTimbau ajustado |
| Configuração | `C = 0,03`, `class_weight = "balanced"`, peso 1 para os rótulos desempatados |
| Corte | nota >= 0,874239 (escolhido no simulado no ponto em que o modelo erra o mesmo que a regra do app) |
| Plano B | o mesmo sem a nota do BERTimbau: `C = 0,1`, corte 0,853788 |
| Entradas | `cosseno` (MPNet ajustado), `sobreposicao`, `nota_texto` (BERTimbau), `idade_dias`, `tem_idade`, `comparou`, `titulo_cortado`, `quem`, `acao`, `assunto`, `quando`, `buscador`, `tipo_afirmacao` |
| Saída | nota de 0 a 1 (não é uma probabilidade calibrada: 0,75 vale uns 26% de "mesmo"; 0,97, uns 87%) |
| Treino | 7.920 pares de 182 notícias (rodadas 1 a 3), rótulo por dois juízes de linguagem cegos, desempate e âncora humana |
| Escolha | 2.374 pares de 49 notícias (rodada 3) |
| Prova | 4.298 pares de 100 notícias (rodada 4, lacrada antes da busca), aberta uma vez em 08/10/2026 |
| Resultado na prova | acha 7,58 de cada 10 fontes do mesmo fato com precisão de 74,3%; a regra do app, 3,57 com 75,0%; a do MVP, 3,48 com 73,3% |
| Onde erra | fontes de 2 a 30 dias (55% de acerto) e de mais de um mês (25%); mesmo assunto contado por outro recorte |
| Uso no app | selo "provável mesmo fato" só para fonte do mesmo dia ou da véspera; nas outras, "mesmo assunto, confira a data"; a regra do app continua valendo onde ela já diz "mesmo acontecimento" |
| Dependências | o `cosseno` precisa do `lume-mpnet-ajustado` (Ollama); a `nota_texto`, do BERTimbau ajustado num servidor |
| Não usar para | dizer se a notícia é verdadeira; só diz se a fonte fala do mesmo fato |
