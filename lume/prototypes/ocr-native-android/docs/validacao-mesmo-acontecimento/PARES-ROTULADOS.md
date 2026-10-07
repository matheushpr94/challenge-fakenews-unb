# Pares rotulados: 1.362 pares reais (afirmação da matéria × título da fonte) com a relação dada por gente

**Arquivo:** `app/src/test/resources/pairs/pares-rotulados.json` (mesmo formato do `pares-dificeis.json`). **Data:** 07/10/2026.
**Quem:** Matheus, com a rotulagem conduzida no repositório privado da residência (scripts e registros lá; este é o entregável).

## O que é

Cada item é um par que o próprio app produziu: a **afirmação** que o `ClaimExtractor` tirou de uma matéria capturada e o
**título de uma fonte** que a busca ao vivo devolveu (`LiveResearchDiagnostics`, candidatas independentes depois da triagem).
Para cada par, uma pessoa decidiu a relação da fonte com a matéria. São 25 matérias:

| Rodada | Matérias | Pares | De onde |
|---|---|---|---|
| 1 (30/09) | as 5 capturas do Guilherme (uol-flamengo-stf, estadao-flamengo-stf, agencia-senado-bets, bbc-fux-article, g1-inadimplencia) | 379 | os registros `docs/validacao-mesmo-acontecimento/depois/live-*.txt` |
| 2 (06/10) | 20 manchetes reais de 8 temas (saúde, economia, ciência, tecnologia, esportes, entretenimento, Brasil, mundo) | 983 | buscas rodadas em 06/10 pelo diagnóstico ao vivo do app |

Os 40 pares difíceis do `pares-dificeis.json` não estão repetidos aqui.

## Como foi rotulado

1. **Regra escrita** com cinco relações e desempates fixados: A mesmo acontecimento, B detalhe divergente (mesmo fato, um dado
   bate de frente), C contexto relacionado, D outro acontecimento, E incerto. O juiz vê só o que o modelo vê: afirmação, título,
   veículo, datas. Sem internet, sem abrir a página.
2. **Dois juízes independentes** (Claude Opus 5.5 e GPT via Codex CLI), cegos um ao outro, com trecho literal obrigatório.
   Concordância (κ de Cohen): 0,91 na rodada 1, 0,85 na rodada 2.
3. **Âncora humana:** 100 pares por rodada rotulados pelo Matheus **antes** de ver qualquer juiz (binário: 92 e 96 de acordo com
   o consenso em cada 100). As 113 discordâncias entre juízes foram decididas por ele.
4. Critério escrito antes da rotulagem (κ ≥ 0,60; âncora ≥ 85 no binário, ≥ 70 nas cinco) passou nas duas rodadas.

## Formato

Chaves do `pares-dificeis.json`: `id`, `tipo` (`rotulado-rodada-1` ou `-2`), `original` (`{"captura": ...}` na rodada 1, que o
corpus do app conhece; forma inline na rodada 2), `candidata` (`veiculo`, `titulo`, `data` em ISO; `url`, `dominio`, `trecho` e
`pagina` vazios, porque o registro da busca não os guarda), `esperado`. Mais quatro campos: `afirmacao` (o texto que o juiz viu),
`relacao_das_cinco` (A–E por extenso), `origem_do_rotulo` (`consenso` ou `decisão do Matheus`) e `regra_do_app_respondeu`.

| `esperado` | vem de | pares |
|---|---|---|
| `mesmo_acontecimento` | A | 325 |
| `divergente` | B | 5 |
| `contexto` | C | 295 |
| `outro` | D | 710 |
| `incerta` | E | 27 |

## Para que serve

- **Teste de regressão da regra**, 34 vezes maior que os 40 pares: toda mudança no `EventFrame` pode ser medida aqui, por
  matéria, sem rotular nada de novo.
- **Treinar ou avaliar qualquer decisor** de "mesmo acontecimento". Uma regra: a divisão entre treino e avaliação tem de ser
  **por matéria**, nunca por par (pares da mesma matéria se parecem), e `uol-flamengo-stf` e `estadao-flamengo-stf` são o
  **mesmo acontecimento** (34 títulos em comum): ficam do mesmo lado.

## O que o dataset já mostra sobre a regra atual

Medido na saída da própria regra, nas 25 matérias (nas 20 novas, com o modelo `lume-mpnet-ajustado` no Ollama; o modelo só é
consultado num caso de empate, 1 candidata em 384, então o número é da regra):

| | Fonte confirma a matéria (330) | Não confirma (1.032) |
|---|---|---|
| Regra mostrou como "mesmo acontecimento" | 176 | 18 |
| Regra não mostrou | 154 | 1.014 |

A regra reconhece **53 em cada 100** confirmações verdadeiras, com precisão 91. Nas 5 matérias de política, 73; **nas 20 matérias
de outros temas, 45**. O motivo mais frequente: "quem agiu" não bate quando o título da fonte não nomeia o agente ("Quem é
Francis Halzen, vencedor do Nobel" para "Nobel de Física: entenda como o gelo capta partículas"), e a regra desiste.

## O que não está aqui

Os modelos em avaliação (a decisão aprendida sobre as respostas da regra + cosseno; um classificador sobre os vetores; um
cross-encoder). Cada um tem expectativa e critério escritos antes de rodar; o que passar vem numa proposta à parte, com a tabela
de cobertura por precisão para o grupo escolher onde o app opera.
