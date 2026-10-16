# Histórico do Lume

## 0.4.3 (16/10/2026): ícone do Lume

- O ícone do app passa a ser o mascote (a gotinha) sobre o creme do app e do site, nos três formatos do Android
  (adaptativo, redondo e quadrado) e em todas as densidades, com a camada monocromática para os temas do Android 13+.
  Antes era o robô padrão do Android Studio.

## 0.4.1 (proposta, 15/10/2026): servidor dos modelos de fábrica

- O APK pode sair já apontando para o gateway do grupo (`lume.servidor.url` e `lume.servidor.chave` no `local.properties`
  → `BuildConfig.SERVER_URL` e `SERVER_TOKEN`). Sem preferência salva em Ajustes, vale o de fábrica; com os campos vazios em
  "Salvar e testar", volta ao de fábrica. Sem os dois valores na compilação, nada muda em relação ao 0.4.0.
- Modelos no ar em `https://lume.191-252-218-172.sslip.io` (VPS do grupo; só HTTPS, chave obrigatória, limite por IP).

## Protótipo Android — integração do modelo de pares — 2026-10-09

- Documentada a integração opcional do modelo treinado de pares ao fluxo Android: combinação de MPNet ajustado, sobreposição lexical e BERTimbau ajustado, além do uso separado de `paraphrase-multilingual` para similaridade/ordenação.
- Registrado que a IA local auxilia a comparação de fontes, não declara veracidade, e depende de serviços e pesos locais que não são versionados.

## 0.3.0-demo — 2026-09-19

- Piscada com um segundo quadro do mascote, mantendo a imagem original.
- Respiração discreta, cumprimento ao tocar no mascote grande e inclinação ao arrastar.
- Estado de espera no cabeçalho durante a análise simulada da web e a análise da variante connected.
- Pausa manual: rodapé da web e **Mais opções → Animar mascote** no Android.
- Web respeita redução de movimento e pausa em aba oculta/fora do viewport; Android pausa fora de vista, com tela apagada, economia de bateria ou animações do sistema desativadas.
- Nove testes web e cinco testes Android passaram; APK mantém a assinatura anterior e continua sem permissão INTERNET. Uso físico e inspeção visual de execução pendentes.

## 0.2.0-demo — 2026-09-17

- APK demo offline sem permissão de internet, sem configuração de API e sem consumo de IA.
- Mascote flutuante arrastável; captura autorizada e revisão local.
- Três exemplos fictícios com índices de 50%, 90% e abstenção.
- Critérios e explicações recolhidos; tela inicial com duas ações principais.
- Variante connected separada no código, preparada para o servidor.
- Download do APK na prévia pública da Vercel.
- Cinco testes Android, seis testes web e onze testes do servidor disponíveis. Teste físico e integração real de IA pendentes.

## 0.1.0 — 2026-09-17

- Identidade inicial e mascote original.
- Primeira prévia web, app Android e servidor de análise com imagens e busca web.
- APK inicial sem respostas locais; análise dependia de conexão ao servidor.

## Organização no challenge — 2026-09-18

- Código do produto reunido em `lume/`, preservando as pastas acadêmicas existentes.
- Prévia web atual incluída; prévia antiga, APKs, caches e configurações locais excluídos do Git.
- Documentadas as diferenças entre classificação, probabilidade calibrada e índice de evidências.
