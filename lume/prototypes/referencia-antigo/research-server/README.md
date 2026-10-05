# Lume — protótipo web de pesquisa de contexto

Recebe uma notícia, afirmação ou pergunta, identifica o detalhe verificável, pesquisa fontes na
internet e mostra: o que foi possível esclarecer, as fontes que respondem ao detalhe e o contexto.
**Não** classifica nada como verdadeiro ou falso e não dá notas. Não usa DeBERTa nem modelos em treino.

## Executar

Requisitos: Python 3.10+ (só biblioteca padrão; nada para instalar).

```bash
python3 server.py
```

Abra http://localhost:8770. Logs em `logs/lume.log` (consultas, fontes, falhas e descartes).
Para ver os descartes um a um: `LUME_LOG_LEVEL=DEBUG python3 server.py`.

Buscas pelo terminal: `python3 scripts/try_search.py "texto 1" "texto 2"`.

## Testes

```bash
python3 -m unittest discover -s tests -t .
```

87 testes com **rede simulada** (reproduzíveis, sem internet; nomes fictícios). Cobrem: quantidade
alegada diferente da documentada, fato estável com notícias recentes irrelevantes, informação antiga
que mudou, unidade/categoria/período diferentes, negação, fonte relacionada que não esclarece,
fontes divergentes, republicações, ambiguidade real (homônimos e entrada incompleta), ausência de
evidência, falha técnica × resultado vazio, XML/JSON inválidos, URLs inseguras, instruções
maliciosas em páginas, cache e validação da resposta do modelo opcional. `tests/test_disambiguation.py`
cobre termos com vários significados, nomes compartilhados, característica × aparência, geral ×
episódio, tempo/local/categoria (modelo simulado), entrada clara, "Não é isso" com detalhes, limite
de rodadas e falha da Wikipédia/modelo. `tests/test_indicacao.py` cobre apoio, contradição, divergência,
ausência de evidência, período e categoria diferentes (inclusive categoria não informada), conteúdo só
relacionado, número sem a entidade, muitos títulos repetindo a alegação, página mais nova sem período do
fato, negação, afirmação que as regras não interpretam, opinião, falha de rede, pergunta sem afirmação,
várias afirmações e vários valores na mesma frase.

O descarte de respostas antigas no navegador está em `tests/controller.test.mjs`
(`node --test tests/controller.test.mjs`; sem Node, o mesmo teste foi executado no navegador).

## Indicação provisória (versão sem modelo)

Depois de pesquisar e comparar, cada detalhe verificável recebe uma indicação:

| Indicação | Quando |
|---|---|
| **Tende a ser verdadeira** | Trechos lidos, que **nomeiam a entidade**, informam valor compatível na mesma unidade, categoria e período |
| **Tende a ser falsa** | Nas mesmas condições, os trechos informam valor incompatível |
| **Inconclusiva** | Faltam evidências comparáveis; fontes divergem; categoria ou período diferentes; número sem a entidade na frase; só títulos/trechos do buscador; opinião/previsão; ou afirmação que as regras não interpretam (explicado na tela) |
| **Resposta encontrada / Informação insuficiente** | Perguntas sem valor alegado ("Quantos…?"): não recebem verdadeira/falsa |
| **Falha técnica** | As fontes não puderam ser consultadas (não é falta de evidência) |

Regras de decisão (gerais, sem respostas cadastradas):
- a pesquisa e a leitura vêm antes da comparação; valores contrários e divergentes são mantidos na explicação;
- número de resultados, reputação, coincidência de palavras e ausência de notícias não geram tendência;
- republicações contam uma vez; páginas de título parecido com valores diferentes não são tratadas como cópias;
- "mais recente" só vale pelo **período do fato citado no trecho** (ex.: "em 2025"), nunca pela data da página;
- a entrada sem categoria ("8 títulos") não é comparada com números de categoria específica ("8 títulos nacionais");
- negação sobre o valor ("não tem 5 títulos") é aplicada à comparação;
- várias frases com afirmação própria são avaliadas separadamente (até 3); vários valores na mesma frase
  também; a notícia inteira não recebe uma classificação única.

A tela mostra: indicação provisória, "O que encontramos", contexto (só quando útil; ver abaixo), fontes que esclarecem o detalhe (abertas) e "Outras publicações relacionadas" (recolhida),
com o aviso "Indicação baseada nas fontes consultadas; não é uma garantia de veracidade."

### Seleção de contexto (`lume/context.py`)

A consulta de contexto usa entidade + propriedade (+ período), não só a entidade. Um trecho só vira
contexto se for uma **frase completa** (sem corte, sem começar com pronome/conector que dependa da frase
anterior) de uma página lida ou resumo de enciclopédia, que:
- tenha a entidade como sujeito (frases que falam primeiro de outra entidade são descartadas);
- cite a propriedade inteira (unidade e todas as categorias da afirmação);
- explique o dado: definição/método ("estimado", "calculado", "considera", "segundo o censo"…) ou período
  explícito ("desde", "data de referência", ano da própria afirmação); um ano solto não basta;
- não traga o próprio valor comparado (isso é resposta, não contexto).
No máximo 2 trechos, com fonte e link. Sem trecho que cumpra tudo, a seção não aparece. Avisos de
conteúdo não lido aparecem só no cartão do resultado correspondente. Limitação: por ser conservadora e
baseada em palavras, a seleção costuma omitir contexto útil escrito com sinônimos ("população" ×
"habitantes").

### Limitações desta versão sem modelo

- Só são comparados **quantidade + unidade (+ categoria, período)** e **ano de um fato** ("fundado em
  1895"). Qualquer outra afirmação ("X não foi à reunião", "vacina causa Y", relações de causa, citações,
  acontecimentos descritos em texto livre) fica **Inconclusiva**, com essa limitação explicada.
- A exigência de citar a entidade na mesma frase torna o Lume conservador: matérias que usam "o clube",
  "a cidade" etc. não geram tendência (exceto artigos da Wikipédia cujo título é a própria entidade).
- Categorias são comparadas por palavras; sinônimos ("nacionais" × "brasileiros") ficam Inconclusivos.
- Com uma só fonte independente a tendência aparece, mas o motivo avisa que se baseia em uma única fonte.
- Valores extra na mesma frase usam as fontes da primeira consulta; podem ficar Inconclusivos por falta de
  busca específica. Frases seguintes sem nome próprio herdam as entidades da primeira frase.
- As indicações podem estar erradas; elas descrevem os trechos encontrados, não a verdade.

## Esclarecimento antes da pesquisa ("O que você quer saber?")

Antes de pesquisar, `POST /api/interpretar` decide se a entrada admite interpretações que mudam a
resposta. Se sim, a tela mostra 2–3 perguntas completas e "Não é isso — adicionar detalhes"; tocar
numa opção pesquisa direto. "Não é isso" abre um campo, combina os detalhes com a entrada original e
interpreta de novo, sem repetir opções rejeitadas (no máximo 2 rodadas; depois pesquisa com os
detalhes). Durante a pesquisa aparece "Pesquisando: [pergunta]" com "Corrigir", que permite editar
ou voltar às opções. Entradas claras seguem direto.

- **Sem modelo (padrão)**: só padrões estruturais fundamentados — sentidos/homônimos vindos da
  Wikipédia ("Mercúrio", "Porto Seguro"), característica × aparência em entradas telegráficas
  ("sol azul" → "O Sol é azul?", "…pode parecer azul…?", "Existem estrelas azuis?", a categoria vem da
  definição da Wikipédia) e caso específico × pergunta geral ("tubarão atacou surfista").
- **Com o modelo opcional** (mesma configuração da síntese): também tempo, local, categoria e outros
  casos. As opções são validadas: nomes e números que não estão na entrada, nos detalhes ou no contexto
  da Wikipédia são descartados; palavras de veredito e opções repetidas também.
- **Limitação**: sem o modelo, ambiguidades de tempo, local ou categoria **não são detectadas**; a
  pesquisa segue com a entrada como está.

Defina `LUME_CONTACT` (ex.: um e-mail ou URL do projeto) para o User-Agent enviado à Wikipédia,
conforme a política da Wikimedia; sem isso as requisições podem ser limitadas (HTTP 429).

## Como funciona

1. **Interpretação** (`lume/interpret.py`, `lume/claim.py`): entidade principal, propriedade,
   quantidade + unidade + categoria + comparador ("mais de", "cerca de"), datas/período, negação,
   condições ("segundo X"). Tipo: contagem que muda com o tempo, quantidade de um período, fato
   histórico, acontecimento recente, opinião ou previsão.
2. **Consultas**: poucas e complementares. Para quantidades, uma consulta com o valor alegado e outras
   **sem** ele (para achar o valor documentado); a comparação continua usando o valor alegado.
   Fatos estáveis priorizam a web e a Wikipédia em vez de só notícias recentes.
3. **Fontes** (`lume/sources.py`): Google Notícias (RSS), Bing Notícias e Bing Web (RSS), Wikipédia
   (API). As páginas mais promissoras são lidas (limite de tempo e tamanho; bloqueio de IPs internos,
   portas e esquemas inseguros; redirecionamentos revalidados; texto com instruções para IA descartado).
4. **Relevância** (`lume/relevance.py`): entidades + termos do acontecimento + números; republicações
   agrupadas e contadas uma vez; publicações antigas separadas.
5. **Comparação** (`lume/evidence.py`): por regras, só para formatos estruturados — valor + unidade
   (com categoria e período) e ano de um fato. Resultado: *apoiam*, *contradizem*, *divergentes* ou
   *não encontramos evidência suficiente*, sempre com o trecho, o período e o link. Título ou trecho
   de buscador sozinho não sustenta conclusão (vira "pista"). Categoria ou período diferente não é
   contradição. Nomes que correspondem a entidades diferentes geram "Você quis dizer…?".

API documentada em [docs/API.md](docs/API.md) (também em `GET /api/docs`).

## Modelo de linguagem (opcional, desligado)

As regras **não entendem** afirmações de texto livre ("X não foi à reunião", "vacina causa Y"):
nesses casos a síntese diz que não há comparação automática e mostra os trechos. Para comparar o
sentido é preciso um modelo de linguagem. Já existe um adaptador (`lume/llm.py`) para a API Responses
da OpenAI, na mesma linha do `evidence-server` do projeto Android:

- **Ativar**: no terminal do servidor, `export LUME_LLM=openai` e `export OPENAI_API_KEY=...`
  (opcional `LUME_MODEL`, padrão `gpt-5.4-mini`). A chave fica só no servidor.
- **Custo**: cobrado por uso pela OpenAI (tokens de entrada e saída; ~2–4 mil tokens por análise).
  Confira os preços atuais antes de ativar.
- **Como é usado**: só depois que os links já foram exibidos (`POST /api/sintese`), e só para os
  casos que as regras não comparam. O modelo não navega; recebe a afirmação e os trechos coletados,
  marcados como dados. Citações inválidas são descartadas.
- **Não validado com a API real**: o fluxo foi testado apenas com respostas simuladas.

Alternativas possíveis (não implementadas): Claude API (Anthropic), ou um modelo local (Ollama),
que é gratuito, mas exige baixar vários GB e tende a ser menos preciso em português.

## Limitações conhecidas

- Google Notícias RSS e Bing RSS não são APIs oficiais para este uso; podem mudar ou bloquear. O RSS
  do Bing restringe o uso a fins pessoais e não comerciais. Links do Google Notícias passam por
  redirecionamento e não são lidos pelo Lume ("apenas título").
- GDELT foi testado e ficou de fora (limite de requisições e tempo esgotado).
- Muitas páginas não são lidas (paywall, conteúdo gerado por JavaScript, bloqueio). Sites oficiais
  com dados em tabelas ou gráficos (ex.: IBGE) raramente fornecem o número em texto corrido; nesses
  casos o resultado tende a "insuficiente" com uma pista.
- As regras identificam o sujeito de forma simples: um número numa frase sem sujeito explícito pode
  ser de outra coisa (o Lume prioriza frases que nomeiam a entidade, mas não resolve pronomes).
- Categorias são comparadas por palavras ("nacionais" × "brasileiros" são tratadas como diferentes).
- Para contagens que mudam, vale o dado mais recente encontrado; uma única fonte recente errada pode
  levar a uma síntese errada (a explicação mostra os valores e as datas para conferência).
- Com apenas uma fonte independente lida, a síntese avisa isso, mas ainda pode estar errada.
- Negações, relações de causa e acontecimentos descritos em texto livre não são comparados sem o
  modelo opcional.
- A proteção contra DNS rebinding é parcial (o IP é verificado antes da conexão, não durante).
- Cache em memória (notícias 10 min, web 30 min, Wikipédia 6 h, páginas 1 h); a data de cada
  consulta aparece em "Detalhes da pesquisa".

O Lume pode errar. As frases de síntese descrevem as evidências encontradas, não garantem a verdade.
