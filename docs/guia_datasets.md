# Guia e Mapeamento dos Datasets do Projeto

> **Residência em Inteligência Artificial (UnB / Instituto Eldorado)**  
> **Challenge 01: Detecção de Desinformação**

---

## 📊 Visão Geral dos Datasets

O projeto utilizou múltiplos conjuntos de dados em diferentes fases da investigação: desde a auditoria inicial de vazamento em benchmarks acadêmicos internacionais até o treinamento e prova de modelos contextualizados em português para o aplicativo **Lume**.

| Dataset | Idioma | Granularidade | Volume | Onde está no repositório | Finalidade Principal |
| :--- | :---: | :---: | :---: | :--- | :--- |
| **LIAR2** | Inglês | Afirmação (*Claim*) | ~23 mil declarações | Não versionado no Git.<br>Instruções em [`data/README.md`](../data/README.md) | Auditoria inicial e comprovação de *data leakage* (`justification` e `*_counts`). |
| **FakeRecogna** | Português | Artigo completo | ~22.300 notícias | [`notebooks/FakeRecogna.csv`](../notebooks/FakeRecogna.csv) | Treinamento do baseline SVM (`baseline_fakerecogna.ipynb`) e sinal de estilo textual do Lume. |
| **FakeTrueBr Corpus** | Português | Artigo completo | ~1.800 notícias | [`notebooks/FakeTrueBr_corpus.csv`](../notebooks/FakeTrueBr_corpus.csv) | Análises exploratórias comparativas em português. |
| **Pares "Mesmo Fato"** | Português | Par (afirmação × título) | 14.592 pares | [`notebooks/mesmo-fato/dados/entradas.csv`](../notebooks/mesmo-fato/dados/entradas.csv) | Treino, escolha e prova lacrada do modelo classificador de fontes do app Lume. |
| **Notas BERTimbau** | Português | Embeddings / Notas | 14.592 notas | [`notebooks/mesmo-fato/dados/notas_bertimbau.csv`](../notebooks/mesmo-fato/dados/notas_bertimbau.csv) | Entradas pré-computadas do modelo BERTimbau ajustado para empilhamento no modelo 6B. |

---

## 🔬 Detalhamento dos Datasets

### 1. LIAR2 (Auditoria e Análise de Vazamento)
* **Origem:** Xu & Kechadi (2024), extensão do benchmark clássico LIAR (PolitiFact).
* **Decisão Metodológica:** O grupo utilizou o LIAR2 para testar a hipótese de vazamento (*leakage*). Ficou comprovado que o texto de justificativa dos checadores (`justification`) e contadores históricos de veracidade dos políticos (`*_counts`) inflavam artificialmente a acurácia dos modelos (atingindo 81%), gerando um atalho que não se sustenta no mundo real.
* **Política de Versionamento:** Conforme explicitado em [`data/README.md`](../data/README.md), os arquivos brutos do LIAR2 (`train.csv`, `valid.csv`, `test.csv`) **não são commitados** para manter o repositório enxuto e respeitar licenças de distribuição. Devem ser baixados via Hugging Face ou clone oficial.

### 2. FakeRecogna (Classificação de Estilo em Português)
* **Origem:** Dataset brasileiro de notícias checadas em português.
* **Aplicação:** Utilizado em [`notebooks/baseline_fakerecogna.ipynb`](../notebooks/baseline_fakerecogna.ipynb) para treinar um classificador linear com TF-IDF + SVM (`modelo/modelo_fakerecogna_svm.pkl`).
* **No Produto Lume:** O modelo treinado com este dataset fornece o "sinal do texto" no app, identificando se o vocabulário e a estilometria lembram matérias jornalísticas comuns ou desinformação sensacionalista, servindo como indício orientador sem atribuir veredito absoluto.

### 3. Pares "Mesmo Fato" (App Lume — Entrega Principal)
* **Origem:** Capturas de notícias reais submetidas ao aplicativo Lume ao longo de 4 rodadas sucessivas (incluindo 100 notícias lacradas antes de qualquer inspeção humana para a prova final).
* **Estrutura dos dados:** Cada registro representa um par:
  $$\text{Par} = (\text{Afirmação da Notícia}, \text{Título da Fonte Encontrada})$$
* **Rótulo alvo:** `mesmo` (1 se a fonte trata exatamente do mesmo acontecimento; 0 caso contrário).
* **Arquivos associados:**
  * `dados/entradas.csv`: 13 features estruturadas derivadas da busca (datas, coincidência de termos, entidades).
  * `dados/notas_bertimbau.csv`: nota de similaridade semântica contextual.
  * `dados/regra_do_mvp_rodada_4.csv`: decisões da regra heurística usada como linha de base.

