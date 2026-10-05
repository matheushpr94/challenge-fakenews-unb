# API do protótipo Lume

Servidor local: `http://127.0.0.1:8770` (variáveis `LUME_HOST`, `LUME_PORT`).
No emulador Android use `http://10.0.2.2:8770`; num celular físico, rode com `LUME_HOST=0.0.0.0`
e use o IP do computador na rede local.

## `POST /api/search`

Corpo JSON (máx. 20 KB):

```json
{ "texto": "O Atlético Serrano tem 5 títulos nacionais", "id_consulta": "q-123" }
```

- `texto` (obrigatório, até 5000 caracteres): notícia, afirmação ou pergunta. É tratado como dado.
- `id_consulta` (opcional, até 64 caracteres): devolvido na resposta. O cliente deve **descartar
  respostas cujo `id_consulta` não seja o da pesquisa atual** (evita que uma resposta atrasada
  substitua uma pesquisa nova).

Códigos HTTP: `200` pesquisa concluída (inclusive sem resultados), `400` entrada inválida,
`502` falha técnica das fontes (`status: "erro"`), `500` erro interno.

### Campos principais da resposta

| Campo | Significado |
|---|---|
| `status` | `ok`, `insuficiente` (busca concluída sem fontes diretas), `ambigua` (ver `sugestoes`), `erro` (falha técnica, **não** é "nada encontrado"), `entrada_invalida` |
| `interpretacao.afirmacao` | Estrutura verificável: `tipo`, `entidade_principal`, `propriedade`, `quantidade` (valor, unidade, qualificadores, comparador), `periodo`, `negacao`, `qualificadores`, `detalhe_verificavel` |
| `interpretacao.consultas` | Consultas feitas e a finalidade de cada uma (`especifica`, `valor_documentado`, `pergunta_valor`, `sem_data`…) |
| `sintese.situacao` | `apoiam`, `contradizem`, `divergentes`, `insuficiente`, `resposta` (pergunta sem valor alegado), `nao_verificavel` (opinião/previsão), `sem_comparacao` (tipo que as regras não comparam), `erro_tecnico` |
| `sintese.frase` | Frase fixa correspondente à situação (descreve as evidências, não garante verdade) |
| `sintese.explicacao[]` | Comparação concreta: valor informado, o que cada fonte diz, período/categoria, com `fontes[]` (veículo + URL) |
| `sintese.evidencias[]` | Trechos de onde os valores foram extraídos, com `relacao`: `apoia`, `contradiz`, `valor`, `outra_categoria`, `outro_periodo`, `sem_categoria`, `limite` |
| `sintese.indicacao` | `{rotulo, texto, motivo, aviso}`. `rotulo`: `tende_verdadeira`, `tende_falsa`, `inconclusiva`, `resposta`, `sem_resposta`, `falha_tecnica` |
| `detalhes_adicionais[]` | Outros valores da mesma frase, cada um com sua própria `sintese` e `indicacao` |
| `modo: "partes"` | Entrada com várias afirmações: a resposta traz `partes[]` (um resultado completo por frase, com `trecho_da_entrada`) e **não** traz `sintese` geral |
| `sintese.metodo` | `regras`, `modelo` ou `nenhum` |
| `sintese_modelo` | `{disponivel, pendente}`. Se `pendente`, chame `POST /api/sintese` |
| `resultados.responde` | Fontes de onde o detalhe comparado foi extraído (com conteúdo lido) |
| `resultados.direto` / `anterior` / `contexto` / `contexto_geral` | Mesmo assunto; mesmo tema com outra data; contexto periférico; enciclopédia |
| Cada resultado | `titulo`, `url`, `veiculo`, `data`, `periodo`, `tipo_fonte`, `conteudo` (`completo` = página lida, `resumo` = resumo da Wikipédia, `trecho` = trecho do buscador, `titulo` = só título), `trechos_pagina`, `alertas`, `republicacoes` |
| `fontes_consultadas[]` | Cada chamada: fonte, consulta, `status` (`ok`/`vazio`/`erro`), `erro`, `em_cache`, `obtido_em` |
| `descartados` | Quantidade e exemplos de resultados descartados, com motivo |

## `POST /api/interpretar`

Chamar antes de `/api/search`. Corpo:

```json
{ "texto": "sol azul", "detalhes": "", "rejeitadas": [], "rodada": 0, "id_consulta": "q-1" }
```

Resposta:

```json
{ "id_consulta": "q-1", "precisa_escolher": true, "pergunta": "O que você quer saber?",
  "opcoes": [ {"texto": "O Sol é azul?", "diferenca": "caracteristica_aparencia"},
              {"texto": "O Sol pode parecer azul em alguma situação?", "diferenca": "caracteristica_aparencia"},
              {"texto": "Existem estrelas azuis?", "diferenca": "categoria"} ],
  "opcao_detalhes": "Não é isso — adicionar detalhes",
  "texto_pesquisa": "sol azul", "entrada_original": "sol azul", "detalhes": "", "rodada": 0,
  "metodo": "regras", "motivo": "característica x aparência", "aviso": "" }
```

- `precisa_escolher: false` → pesquise `texto_pesquisa` diretamente.
- Opção escolhida → `POST /api/search` com `texto` = texto da opção.
- "Não é isso" → chame de novo com os `detalhes` acumulados, as opções mostradas em `rejeitadas` e
  `rodada + 1`. A partir da rodada 2 a resposta é sempre `precisa_escolher: false`.
- `diferenca`: `significado_ou_entidade`, `caracteristica_aparencia`, `geral_especifico`, `tempo`,
  `local`, `categoria`, `outro`. `metodo`: `regras` ou `modelo`. `aviso` explica limitações (ex.:
  Wikipédia indisponível, modelo com falha).

## `POST /api/sintese` (opcional)

`{ "id_consulta": "q-123" }` → comparação por modelo de linguagem dos trechos já coletados pela
pesquisa (válida por 15 min). Só funciona com o modelo configurado (ver README). Devolve os mesmos
campos de `sintese`. Citações a fontes inexistentes são descartadas; conclusão sem citação válida
vira `insuficiente`.

## Outros

- `GET /health` → `{"status": "ok"}`
- `GET /api/docs` → este arquivo

## Exemplo de resposta (resumido, gerado com rede simulada)

```json
{
  "id_consulta": "q-exemplo-1",
  "consultado_em": "2026-09-28T12:00:00+00:00",
  "status": "ok",
  "interpretacao": {
    "assunto": "O Atlético Serrano tem 5 títulos nacionais",
    "consultas": [
      {"texto": "Atlético Serrano 5 títulos nacionais", "finalidade": "especifica"},
      {"texto": "Atlético Serrano títulos nacionais", "finalidade": "valor_documentado"},
      {"texto": "quantos títulos nacionais Atlético Serrano", "finalidade": "pergunta_valor"}
    ],
    "afirmacao": {
      "tipo": "contagem",
      "entidade_principal": "Atlético Serrano",
      "quantidade": {"valor": 5.0, "texto": "5 títulos", "unidade": "títulos",
                     "qualificadores": ["nacionais"], "comparador": "eq"},
      "negacao": false,
      "detalhe_verificavel": "Quantidade de títulos nacionais — Atlético Serrano: informado 5 títulos"
    }
  },
  "sintese": {
    "situacao": "contradizem",
    "frase": "As fontes consultadas contradizem essa informação.",
    "metodo": "regras",
    "explicacao": [
      {"texto": "Informado na entrada: 5 títulos (nacionais).", "fontes": []},
      {"texto": "serrano.example.org (página consultada; período citado: 2019; publicado em 02/05/2026): oito títulos (nacionais)",
       "fontes": [{"veiculo": "serrano.example.org", "url": "https://serrano.example.org/historia"}]},
      {"texto": "Apenas uma fonte independente com conteúdo lido informou esse valor.", "fontes": []}
    ],
    "evidencias": [
      {"veiculo": "serrano.example.org", "url": "https://serrano.example.org/historia", "conteudo": "completo",
       "valor": 8, "texto_valor": "oito títulos", "qualificadores": ["nacionais"], "periodo_citado": [2019],
       "trecho": "O Atlético Serrano conquistou oito títulos nacionais ao longo da história, o último em 2019.",
       "relacao": "contradiz"}
    ]
  },
  "sintese_modelo": {"disponivel": false, "pendente": false},
  "sugestoes": [],
  "resultados": {
    "responde": [
      {"titulo": "Atlético Serrano: história e títulos", "url": "https://serrano.example.org/historia",
       "veiculo": "serrano.example.org", "data": "2026-05-02T10:00:00+00:00", "tipo_fonte": "web",
       "relacao": "responde", "conteudo": "completo", "conteudo_texto": "Conteúdo da página consultado",
       "trechos_pagina": ["O Atlético Serrano conquistou oito títulos nacionais ao longo da história, o último em 2019."],
       "alertas": [], "republicacoes": []}
    ],
    "direto": [], "anterior": [], "contexto": [], "contexto_geral": []
  },
  "fontes_consultadas": [
    {"fonte": "bing_web", "consulta": "Atlético Serrano 5 títulos nacionais", "status": "ok",
     "quantidade": 1, "em_cache": false, "obtido_em": "2026-09-28T12:00:00+00:00"}
  ],
  "descartados": {"quantidade": 0, "exemplos": []},
  "duracao_ms": 412
}
```
