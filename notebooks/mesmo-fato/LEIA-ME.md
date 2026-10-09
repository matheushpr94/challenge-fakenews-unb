# Entrega de 09/10/2026: o modelo "mesmo fato" do Lume

O modelo que diz se uma fonte achada pela busca do app fala do mesmo fato que a notícia. Na prova (100 notícias que
ninguém tinha olhado), ele acha 7,6 de cada 10 fontes do mesmo fato, contra 3,5 da regra do MVP do grupo, errando na
mesma proporção.

| Arquivo | O que é |
|---|---|
| `relatorio-tecnico.md` (e `.pdf`) | preparo dos dados e treino, critérios de escolha, métricas e resultados, desafios, aprendizados |
| `avaliar_o_modelo.ipynb` | carrega o modelo, dá a nota de cada fonte e refaz as métricas (simulado e prova); refaz o treino da logística e confere com o salvo |
| `modelo/modelo_6b.pkl` | o modelo escolhido (pré-processamento + regressão logística, um `Pipeline` do scikit-learn) |
| `modelo/modelo_plano_b.pkl` | o mesmo modelo sem a nota do BERTimbau, para quando o servidor não responde |
| `modelo/ficha-do-modelo.md` | o que entra, o que sai, os cortes, os limites |
| `modelo/decisor.json`, `decisor_plano_b.json` | as receitas (pesos, médias, cortes) que o app em Kotlin copia |
| `modelo/modelo_escolhido.json` | a configuração congelada (C, corte, entradas, impressão digital das notas) |
| `modelo/preprocessamento.py` | o módulo que o `.pkl` precisa para abrir |
| `modelo/bertimbau-ajustado/` | os pesos do BERTimbau ajustado (436 MB): não estão no GitHub (limite de 100 MB por arquivo); estão na pasta do grupo no Teams |
| `dados/entradas.csv` | as 14.592 fontes: as entradas, o rótulo (`mesmo`) e o monte (estudo, simulado, prova) |
| `dados/notas_bertimbau.csv` | a nota do BERTimbau de cada fonte (no estudo, de uma versão que não viu a fonte) |
| `dados/regra_do_mvp_rodada_4.csv` | a regra do MVP do grupo refeita nas fontes da prova |
| `figuras/` | as figuras das 16 etapas do pipeline |
| `cadernos-do-pipeline/` | os 4 cadernos do pipeline (marimo), exportados para `.ipynb`: o código de cada etapa, para leitura |

## Como rodar

1. Python 3.12 com `pandas`, `numpy`, `scikit-learn` **1.9.1** (o `.pkl` abre na mesma versão), `matplotlib` e
   `jupyter`. Para a célula opcional do BERTimbau, também `torch` e `transformers`.
2. Abra `avaliar_o_modelo.ipynb` **de dentro desta pasta** (os caminhos são relativos a ela) e rode tudo. Roda em
   processador comum, em menos de um minuto.

Os cadernos de `cadernos-do-pipeline/` são para leitura: eles rodam junto com o resto do pipeline e o cofre da prova
(já aberto uma vez), que ficam no repositório de trabalho.
