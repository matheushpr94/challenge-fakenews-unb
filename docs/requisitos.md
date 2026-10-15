# Especificação de Requisitos — Projeto Lume

> **Residência em Inteligência Artificial (UnB / Instituto Eldorado)**  
> **Challenge 01: Detecção de Desinformação**  
> **Projeto:** Lume — Assistente de Investigação de Informações apoiado por IA

---

## 1. Introdução

Vivemos em um ambiente de alta circulação de informações, no qual conteúdos verdadeiros, falsos, imprecisos ou fora de contexto convivem nos mesmos canais e frequentemente possuem aparência semelhante. Nesse cenário, avaliar a confiabilidade de uma informação exige mais do que identificar se uma afirmação é simplesmente verdadeira ou falsa. É necessário considerar evidências, contexto, precisão, completude e os limites do que pode ser verificado.

Este projeto surge a partir do desafio de investigar como sistemas de Inteligência Artificial podem ajudar pessoas a avaliar a confiabilidade de informações **sem substituir seu pensamento crítico**. A proposta do desafio enfatiza a investigação de evidências, identificação de vieses e construção de critérios de confiança como elementos centrais da solução.

Durante a investigação utilizando o dataset LIAR2, observamos que uma classificação puramente binária pode eliminar nuances importantes. Aproximadamente **46,8% das afirmações analisadas** no conjunto de treino pertencem a categorias intermediárias de veracidade, como *barely-true*, *half-true* e *mostly-true*. A análise qualitativa desses casos também mostrou que uma informação pode conter fatos corretos e ainda assim apresentar problemas relacionados a correção factual, completude, contexto ou precisão.

Esses resultados indicam que simplesmente apresentar ao usuário uma classificação como "verdadeiro" ou "falso" pode ser insuficiente. Da mesma forma, apresentar um percentual arbitrário de confiança pode criar uma falsa sensação de precisão. O produto proposto parte, portanto, de uma abordagem diferente: **auxiliar o usuário a compreender como uma informação foi avaliada e quais evidências sustentam essa avaliação**.

A solução é concebida como um assistente de investigação de informações apoiado por IA. Em vez de entregar apenas uma conclusão, o sistema organiza e apresenta o nível geral de veracidade, as evidências utilizadas, os pontos confirmados, os pontos problemáticos, o contexto necessário para interpretação e os aspectos que ainda não puderam ser verificados.

> **Objetivo do documento:** Descrever os requisitos necessários para o desenvolvimento de funcionalidades, conectando-os aos objetivos de negócios e às necessidades dos usuários. Este documento é utilizado para planejamento, priorização e acompanhamento.

---

## 2. Objetivos do Projeto (OKRs)

Alinhados aos objetivos estratégicos e resultados esperados do desafio:

* **Objetivo 01: Apoiar o usuário na avaliação da confiabilidade de uma informação de forma mais crítica e contextualizada.**
  * **KR 1.1:** Pelo menos **80% dos usuários testados** devem conseguir identificar, após utilizar a ferramenta, quais partes da informação foram confirmadas, quais são problemáticas e quais exigem contexto adicional.

* **Objetivo 02: Tornar a análise realizada pela IA transparente e baseada em evidências.**
  * **KR 2.1:** **100% das análises** apresentadas pelo produto devem exibir o nível geral de veracidade, as evidências utilizadas, os pontos confirmados, os pontos problemáticos, o contexto necessário e os aspectos não verificados.

* **Objetivo 03: Estimular o usuário a investigar a informação em vez de apenas aceitar uma conclusão da IA.**
  * **KR 3.1:** Em testes de usabilidade, pelo menos **70% dos usuários** devem consultar ao menos uma evidência, fonte ou informação contextual antes de concluir sua própria avaliação.

---

## 3. Requisitos Funcionais (RF)

### Entrada e Análise da Informação
* **RF01. Submissão de conteúdo para análise:** O sistema deve permitir que o usuário envie conteúdos em diferentes formatos, incluindo texto, links, imagens e capturas de tela.
* **RF02. Identificação de afirmações verificáveis:** O sistema deve identificar quais afirmações presentes no conteúdo podem ser verificadas.
* **RF03. Ausência de elementos verificáveis:** Quando o conteúdo não possuir afirmações verificáveis, o sistema deve informar explicitamente ao usuário que não foi possível realizar uma checagem factual.
* **RF04. Análise do tom do conteúdo:** O sistema deve analisar características do tom e da linguagem utilizada, como sensacionalismo, urgência, exagero ou apelo emocional.
* **RF05. Tratamento dos sinais linguísticos como indícios:** O sistema deve apresentar sinais de alerta relacionados ao texto apenas como elementos de orientação, nunca como evidência suficiente para determinar a veracidade da informação.

### Evidências e Fontes
* **RF06. Busca de evidências:** O sistema deve buscar evidências relacionadas às afirmações identificadas no conteúdo analisado.
* **RF07. Exibição das evidências utilizadas:** O sistema deve permitir que o usuário visualize as evidências consideradas pela IA durante a análise.
* **RF08. Identificação da fonte primária:** Sempre que possível, o sistema deve identificar e disponibilizar a fonte primária relacionada a cada evidência.
* **RF09. Relação entre evidência e afirmação:** Para cada evidência encontrada, o sistema deve indicar sua relação com a afirmação analisada, utilizando estados como:
  * *corrobora*;
  * *diverge*;
  * *não encontrado*;
  * *inconclusivo*.
* **RF10. Identificação de aspectos verificados e não verificados:** O sistema deve informar claramente quais partes da afirmação puderam ser verificadas e quais permaneceram sem evidências suficientes.
* **RF11. Apresentação de perspectivas divergentes:** Quando existirem evidências ou interpretações relevantes em sentidos diferentes, o sistema deve apresentá-las ao usuário.
* **RF12. Notícias relacionadas de múltiplas fontes:** O sistema deve apresentar conteúdos relacionados publicados por diferentes fontes, permitindo ao usuário comparar abordagens sobre o mesmo assunto.
* **RF13. Filtro por assunto:** O sistema deve permitir que o usuário filtre fontes, evidências e conteúdos relacionados por assunto ou tema.

### Avaliação da Veracidade
* **RF14. Escala de veracidade:** O sistema deve apresentar a avaliação da informação em uma escala gradual, evitando limitar a resposta exclusivamente às categorias verdadeiro e falso. A escala não deve ser convertida automaticamente em percentuais de confiança sem fundamentação metodológica.
* **RF15. Explicação da avaliação:** O sistema deve explicar quais elementos levaram ao nível geral de veracidade apresentado.
* **RF16. Pontos confirmados:** O sistema deve destacar quais partes da informação foram sustentadas pelas evidências encontradas.
* **RF17. Pontos problemáticos:** O sistema deve identificar partes incorretas, imprecisas, incompletas, exageradas ou potencialmente enganosas.
* **RF18. Contexto necessário:** O sistema deve apresentar informações adicionais consideradas importantes para interpretar adequadamente a afirmação.
* **RF19. Aspectos não verificados:** O sistema deve apresentar separadamente informações que não puderam ser confirmadas nem refutadas, evitando a premissa de que *ausência de evidência = falso*.

### Transparência da Análise
* **RF20. Explicação do motivo da classificação:** O usuário deve conseguir entender por que determinada informação recebeu aquela avaliação.
* **RF21. Separação entre evidência e interpretação da IA:** O sistema deve distinguir visualmente:
  * Informação encontrada nas fontes;
  * Interpretação realizada pela IA;
  * Conclusão derivada da análise.
* **RF22. Independência da identidade da fonte:** O sistema não deve considerar automaticamente o nome de uma pessoa, veículo ou organização como prova de confiabilidade. A fonte pode ser apresentada como contexto, mas a avaliação deve considerar as evidências relacionadas à afirmação.

### Estímulo ao Pensamento Crítico
* **RF23. Exploração ativa das evidências:** O sistema deve permitir que o usuário navegue pelas evidências antes ou durante a visualização da conclusão.
* **RF24. Orientações de investigação:** O sistema deve sugerir perguntas ou pontos que o usuário pode investigar para aprofundar a análise (ex.: *A fonte original está disponível? Outras fontes independentes confirmam essa informação? O dado está sendo utilizado no contexto correto?*).
* **RF25. Comparação entre fontes:** O usuário deve conseguir comparar como diferentes fontes tratam a mesma afirmação ou acontecimento.

### Gamificação e Educação Midiática
* **RF26. Pontuação por comportamento investigativo:** O sistema deve permitir que o usuário acumule pontos ao realizar ações relacionadas à investigação (como consultar fontes primárias ou comparar evidências), premiando boas práticas de checagem em vez de marcas/veículos específicos.
* **RF27. Medalhas e conquistas:** O sistema deve conceder conquistas relacionadas ao desenvolvimento de hábitos de investigação crítica.
* **RF28. Desafios periódicos:** O sistema deve oferecer desafios educativos periódicos (ex.: *"Leia sobre o mesmo acontecimento em três fontes diferentes"*).
* **RF29. Quizzes e atividades educativas:** O sistema deve oferecer quizzes ou atividades rápidas sobre identificação de evidências, contexto, vieses e desinformação.

---

## 4. Requisitos Não Funcionais (RNF)

### Privacidade e Segurança dos Dados
* **RNF01. Privacidade dos dados:** O sistema deve proteger os dados fornecidos pelo usuário contra acesso, exposição ou compartilhamento não autorizado.
* **RNF02. Minimização de dados:** O sistema deve coletar e armazenar apenas os dados estritamente necessários para o funcionamento da aplicação.
* **RNF06. Segurança dos dados:** O sistema deve utilizar mecanismos adequados de proteção dos dados durante sua transmissão e armazenamento.
* **RNF07. Controle de acesso:** O sistema deve utilizar mecanismos de autenticação e autorização nas funcionalidades vinculadas à conta do usuário.
* **RNF08. Proteção contra uso indevido:** O sistema deve possuir mecanismos para reduzir tentativas de abuso, entradas maliciosas e manipulação indevida do processo de análise.

### Ética, Transparência e Confiabilidade de IA
* **RNF03. Clareza sobre o propósito da aplicação:** O sistema deve informar de forma clara que seu objetivo é apoiar a investigação e a avaliação de informações, e não determinar uma verdade absoluta para o usuário.
* **RNF04. Transparência sobre o uso dos dados:** O sistema deve informar de forma clara se os conteúdos enviados pelo usuário são armazenados, por quanto tempo são mantidos e se podem ser utilizados para melhoria dos modelos de IA.
* **RNF05. Transparência sobre conteúdo gerado por IA:** O sistema deve permitir que o usuário diferencie informações provenientes de fontes externas, interpretações realizadas pela IA e conclusões produzidas pelo sistema.
* **RNF14. Resiliência a serviços externos:** Caso uma fonte, mecanismo de busca ou serviço de IA esteja indisponível, o sistema deve informar essa limitação ao usuário e não apresentar uma conclusão incompleta como definitiva.
* **RNF15. Atualidade das informações:** O sistema deve apresentar a data das fontes e evidências utilizadas sempre que a temporalidade puder influenciar a interpretação da informação.
* **RNF16. Rastreabilidade das evidências:** O sistema deve permitir identificar quais fontes e evidências foram utilizadas para produzir uma determinada análise.
* **RNF17. Explicabilidade das avaliações:** O sistema deve apresentar uma justificativa compreensível para cada avaliação de veracidade realizada.
* **RNF18. Comunicação de incerteza:** O sistema deve informar quando as evidências disponíveis forem insuficientes, inconclusivas ou conflitantes, evitando conclusões categóricas nesses casos.
* **RNF19. Neutralidade na avaliação das fontes:** O sistema não deve utilizar exclusivamente o nome, reputação ou identidade de uma pessoa, veículo ou organização como prova da veracidade de uma informação.
* **RNF20. Consistência das avaliações:** O sistema deve aplicar os mesmos critérios de avaliação a conteúdos analisados sob condições equivalentes.
* **RNF21. Auditabilidade:** O sistema deve manter informações suficientes para permitir compreender quais elementos contribuíram para uma análise e seu respectivo resultado.
* **RNF22. Proteção contra falsa precisão:** O sistema não deve apresentar percentuais, notas ou índices de confiança sem que exista uma metodologia definida e fundamentada para justificar esses valores.

### Desempenho e Disponibilidade
* **RNF09. Desempenho da aplicação:** O sistema deve permanecer responsivo durante a análise de informações, mesmo quando forem necessárias buscas externas ou processamento por modelos de IA.
* **RNF10. Feedback de processamento:** O sistema deve informar ao usuário quando uma análise estiver em andamento, evitando que o usuário interprete a espera como falha da aplicação.
* **RNF13. Disponibilidade:** O sistema deve estar disponível de forma contínua durante os períodos previstos de utilização.

### Usabilidade e Acessibilidade
* **RNF11. Usabilidade:** O sistema deve apresentar informações, análises e resultados de forma simples e compreensível para usuários sem conhecimento técnico.
* **RNF12. Acessibilidade:** O sistema deve seguir boas práticas de acessibilidade, incluindo contraste adequado, navegação por teclado, textos alternativos e compatibilidade com tecnologias assistivas.

