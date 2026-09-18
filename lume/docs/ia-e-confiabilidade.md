# IA e confiabilidade no Lume

Status: proposta para os próximos incrementos, sem modelo novo treinado ou conectado. A decisão acadêmica de usar LIAR2 em inglês permanece válida para o challenge.

## NLP faz parte do problema

NLP (processamento de linguagem natural) abrange extrair afirmações, representar texto, recuperar documentos e comparar uma afirmação com evidências. A extração de texto de uma captura pode usar OCR ou um modelo que aceite imagem; ela precede a avaliação factual. Um LLM também é uma forma de aplicar NLP, não uma alternativa a NLP.

Não é necessário treinar tudo do zero. Podemos combinar o baseline do challenge com componentes pré-treinados e busca. O baseline textual mede padrões aprendidos no dataset; sozinho, não verifica acontecimentos atuais na web.

## Binário também pode gerar percentuais

O número de classes descreve os rótulos possíveis, não o formato da interface. Um classificador binário pode devolver tanto uma classe quanto uma probabilidade estimada, por exemplo `P(classe verdadeira | texto) = 0.8`.

Esse valor só merece uma interpretação probabilística após avaliação e, se necessário, calibração. Em dados representativos, entre casos que recebem aproximadamente 0.8, esperamos que aproximadamente 80% pertençam à classe positiva. Isso é uma propriedade estatística do conjunto; não comprova uma notícia individual. A documentação do [scikit-learn sobre calibração](https://scikit-learn.org/stable/modules/calibration.html) detalha essa distinção.

Mostrar 0–100% não exige regressão. Regressão faria sentido se definíssemos um alvo contínuo mensurável e dispuséssemos de rótulos apropriados. Também não basta tirar a média ponderada das seis classes do LIAR2 e chamar o resultado de probabilidade de verdade: a escala e o significado precisariam ser definidos e validados.

## Três valores que não devem ser confundidos

| Valor | Pergunta que responde | Situação no Lume |
|---|---|---|
| Classe prevista | Qual rótulo o modelo atribui à afirmação? | Futuro modelo/avaliador |
| Probabilidade calibrada | Com que frequência previsões semelhantes acertam a classe, no domínio avaliado? | Ainda não disponível |
| Índice de evidências | Quanto de uma rubrica explícita está atendido? | Hoje é apenas uma demonstração com fixtures |

Os atuais 50% e 90% somam pontos de apoio direto, rastreabilidade, confirmação independente e contexto. Os pesos são uma hipótese de produto. Não são probabilidades calibradas, e não devem ser ligados à autoconfiança textual de um LLM como se fossem validação estatística.

## Saída recomendada para o produto

Avaliar cada afirmação relevante, pois uma notícia pode misturar fatos corretos, interpretações e erros. Proposta de estados:

- **Sustentada:** evidências consultadas apoiam a afirmação no contexto indicado.
- **Refutada:** evidências consultadas contradizem a afirmação.
- **Evidência insuficiente:** faltam informações para concluir.
- **Evidências em conflito:** há material relevante em direções diferentes que ainda não foi reconciliado.

Essa é uma recomendação de produto, não um mapeamento automático dos rótulos do LIAR2. O benchmark [FEVER](https://aclanthology.org/N18-1074/) exemplifica a verificação com evidências e os estados sustentada, refutada e informação insuficiente; o [AVeriTeC](https://fever.ai/2025/task.html) leva a tarefa para evidências da web e inclui casos de conflito.

“Sem evidência suficiente” não é sinônimo de 0%, de 50%, de mentira ou de dúvida aleatória. A interface pode se abster e mostrar a informação que falta. Mesmo uma rubrica completa pode encontrar refutação: qualidade da evidência e veracidade da afirmação são dimensões distintas.

## Fluxo proposto

1. A pessoa revisa o trecho extraído da captura ou do conteúdo compartilhado.
2. O sistema identifica afirmações verificáveis e preserva datas, locais e contexto.
3. A busca recupera evidências e rastreia fontes originais; cópias da mesma notícia não contam automaticamente como confirmações independentes.
4. O avaliador compara cada afirmação com trechos das fontes, considerando apoio, contradição e ausência de informação.
5. O Lume apresenta conclusão, fontes, limites e data da consulta; eventual probabilidade só entra após validação. Nenhuma média geral da notícia deve esconder afirmações conflitantes.

NLI (inferência em linguagem natural, como apoio/contradição) ou um LLM podem compor a etapa 4. Ainda precisamos comparar alternativas; um pipeline com busca não garante correção por si só.

## Como aproveitar o challenge

Manter o baseline previsto pelo grupo: `statement` → TF-IDF → regressão logística. “Regressão logística”, apesar do nome, é usada para classificação. Comparar binário e/ou seis classes conforme a tarefa acadêmica acordada, sem trocar o alvo apenas por causa do percentual da interface.

Seguir os splits oficiais e as regras de leakage já registradas em [decisões do challenge](../../docs/decisoes.md). Não usar `justification` nem `*_counts` como atalhos. Material posterior de checagem não pode vazar para a avaliação de uma tarefa que supõe acesso apenas à afirmação ou a fontes disponíveis no momento da consulta.

O modelo acadêmico pode ser um baseline ou componente experimental do produto. Ele não está validado para artigos longos, português, redes sociais e assuntos fora do LIAR2. Antes dessa extensão, precisamos de um conjunto representativo do uso real, com afirmações, evidências, rótulos e datas. Tradução não elimina essa diferença de domínio.

## Avaliação antes de exibir probabilidades reais

- Usar treino, seleção de modelo e calibração sem consumir o teste final lacrado. Separar a calibração ou usar previsões fora da amostra dentro do treino; não calibrar com as próprias previsões de treino.
- Avaliar F1/macro-F1, precisão, recall e matriz de confusão, conforme a tarefa.
- Para probabilidades, combinar curva de calibração, Brier/log loss e análise por faixa. Brier/log loss medem mais que calibração; não basta olhar um número isolado.
- Medir se as fontes citadas realmente sustentam a conclusão e se a evidência necessária foi recuperada.
- Medir abstenção: quais casos o sistema consegue cobrir e com que erro nos casos respondidos; evitar melhorar a métrica simplesmente recusando tudo.
- Revisar erros por idioma, tema, período e origem; testar conteúdo sem evidência e com conflito.

Primeiro objetivo: uma análise útil com fontes e limites claros. O percentual deve vir acompanhado de uma definição verificável, não servir como substituto da evidência.
