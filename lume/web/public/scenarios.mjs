// All articles, sources and assessments below are fictional demo fixtures.
export const articles = [
  {
    title: 'Mais árvores, ruas mais frescas?',
    deck: 'Medição inicial sugere queda de 2 °C em ruas que receberam novas árvores.',
    body: 'Um boletim do projeto relata temperaturas menores em uma tarde de medição. Faltam detalhes sobre o clima, o período de comparação e ruas sem o plantio. [Notícia fictícia.]',
    address: '◈  notícias.exemplo',
    explanation: 'O trecho relata uma queda de temperatura, mas uma medição isolada não mostra se as árvores causaram a mudança.',
    question: 'Comparar horários, condições do tempo e ruas com e sem plantio antes de atribuir a diferença às árvores.',
    assessment: {
      assessable: true,
      claim: 'A temperatura caiu 2 °C nas ruas do teste após o plantio.',
      criteria: {
        support: { level: 0.5, reason: 'O boletim descreve a diferença, mas a comparação é incompleta.' },
        original: { level: 1, reason: 'Neste exemplo, o boletim original pode ser identificado.' },
        independent: { level: 0, reason: 'Não há outro levantamento independente neste cenário.' },
        context: { level: 0.5, reason: 'O local está identificado; clima e período de comparação estão incompletos.' },
      },
      sources: 'O cenário supõe um boletim do projeto. Esse documento é fictício; nenhuma fonte foi consultada.',
    },
  },
  {
    title: '“Esse estudo muda tudo!”',
    deck: 'Um post compartilha um título chamativo, mas não traz o link da pesquisa.',
    body: 'Vi essa novidade e achei incrível. Será que já podemos tirar essa conclusão? Compartilhe sua opinião. [Post fictício para demonstrar o fluxo.]',
    address: '◉  rede.exemplo',
    explanation: 'O post não identifica a pesquisa nem apresenta uma afirmação específica que possamos conferir. Isso não permite concluir se é verdadeiro ou falso.',
    question: 'Pedir o link da pesquisa original e identificar exatamente qual resultado o post está anunciando.',
    assessment: {
      assessable: false,
      claim: 'O post não traz uma afirmação verificável suficientemente clara.',
      sources: 'Nenhuma fonte está identificada neste exemplo. Sem material suficiente, o Lume não atribui um percentual.',
    },
  },
  {
    title: 'Mais sombra nas ruas do teste',
    deck: 'Levantamento registra queda de 2 °C nos trechos monitorados, em condições comparáveis.',
    body: 'O relatório apresenta dados, datas e método de medição. Uma equipe independente encontrou resultados compatíveis em parte dos trechos. O resultado se limita aos locais estudados. [Notícia fictícia.]',
    address: '◈  notícias.exemplo',
    explanation: 'Neste cenário, os dados sustentam o resultado nos trechos medidos. Ele não deve ser generalizado para toda a cidade.',
    question: 'Ampliar a confirmação independente para os demais trechos e acompanhar outros períodos do ano.',
    assessment: {
      assessable: true,
      claim: 'Nos trechos monitorados, a temperatura caiu 2 °C em condições comparáveis.',
      criteria: {
        support: { level: 1, reason: 'Os dados do cenário sustentam a afirmação, limitada aos locais medidos.' },
        original: { level: 1, reason: 'O cenário inclui o relatório original com dados e método.' },
        independent: { level: 0.5, reason: 'Um segundo levantamento confirma apenas parte dos trechos.' },
        context: { level: 1, reason: 'Datas, condições de comparação e limites do resultado estão explícitos.' },
      },
      sources: 'O cenário supõe um relatório original e um segundo levantamento independente. Ambos são fictícios; nenhuma fonte foi consultada.',
    },
  },
];
