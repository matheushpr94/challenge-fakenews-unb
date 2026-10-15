# 📚 Central de Documentação — Challenge FakeNews UnB (Lume)

> **Residência em Inteligência Artificial — Universidade de Brasília (UnB) / Instituto Eldorado**  
> **Repositório Oficial:** [github.com/matheushpr94/challenge-fakenews-unb](https://github.com/matheushpr94/challenge-fakenews-unb)

Esta pasta reúne a documentação técnica, metodológica, de engenharia de software e de ciência de dados desenvolvida pelo grupo ao longo do desafio.

---

## 🗂️ Índice de Documentos

### 1. Engenharia de Requisitos & Produto
* 📋 **[Especificação de Requisitos Funcionais e Não Funcionais](requisitos.md)**  
  *Contexto, objetivos estratégicos (OKRs), 29 Requisitos Funcionais (RF01 a RF29) e 22 Requisitos Não Funcionais (RNF01 a RNF22) do aplicativo Lume.*
* 📱 **[Visão Geral do Produto Lume](../lume/README.md)**  
  *Descrição do mascote Android, APK de demonstração (0.3.0-demo), prévia web na Vercel e arquitetura do app.*
* ⚖️ **[IA, NLP e Confiabilidade no Produto](../lume/docs/ia-e-confiabilidade.md)**  
  *Critérios de transparência, ausência de veredito absolutista e significado ético das métricas no app.*

---

### 2. Gestão Ágil e Equipe
* 👥 **[Organização da Equipe e Metodologia Scrum](scrum_e_equipe.md)**  
  *Alocação oficial de papéis (Product Owner, Scrum Master e Desenvolvedores), dinâmica das Sprints, rituais ágeis e Critérios de Pronto (Definition of Done).*

---

### 3. Dados e Investigação Científica
* 📊 **[Guia e Mapeamento dos Datasets](guia_datasets.md)**  
  *Consolidação de todos os corpora utilizados: LIAR2 (auditoria e leakage), FakeRecogna (baseline em PT), FakeTrueBr e o corpus de 14.592 pares "Mesmo Fato".*
* 📖 **[Revisão Bibliográfica](revisao_bibliografica.md)**  
  *Fundamentação teórica em detecção de desinformação, corpora de referência no Brasil e trabalhos correlatos.*
* ❓ **[Guiding Question 02: Características da Desinformação](q02-caracteristicas-desinformacao.md)**  
  *Investigação de marcadores de estilo, tópicos frequentes e análise quantitativa de desinformação no dataset.*
* 🧠 **[Decisões de Projeto e Memória do Grupo](decisoes.md)**  
  *Registro das escolhas técnicas: por que descartamos o dataset ISOT, comprovação de leakage no LIAR2 e integridade de splits.*

---

### 4. Modelagem de IA e Relatórios Técnicos
* 📑 **[Relatório Técnico Principal (Markdown)](../notebooks/mesmo-fato/relatorio-tecnico.md)** / **[Versão PDF](../notebooks/mesmo-fato/relatorio-tecnico.pdf)**  
  *Relatório científico completo sobre o treinamento do modelo "mesmo fato", critérios de escolha, intervalo de confiança (bootstrap 95%), prova lacrada e desafios.*
* 🏷️ **[Ficha do Modelo (Model Card)](../notebooks/mesmo-fato/modelo/ficha-do-modelo.md)**  
  *Especificação técnica dos modelos 6B e Plano B: entradas, saídas, limiares de corte e limites operacionais.*
* 📓 **[Caderno de Avaliação e Reprodução](../notebooks/mesmo-fato/avaliar_o_modelo.ipynb)**  
  *Jupyter Notebook reprodutível que carrega os modelos salvos, recalcula as métricas e confere os resultados.*

