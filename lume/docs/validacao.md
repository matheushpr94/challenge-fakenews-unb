# Validação

## Organização no repositório — 18/09/2026

- 17 testes passaram no novo diretório: 11 do servidor (provedor simulado) e seis da prévia web.
- Os 25 arquivos Android copiados são idênticos aos da versão compilada e validada em 17/09; não houve nova compilação nem teste físico nesta importação.
- Sintaxe dos módulos, links locais de documentação e arquivos referenciados pela página conferidos.
- Exclusões do Git verificadas para APK, `.env`, configuração local da Vercel e SDK local. Nenhuma chave ou configuração de conta foi incluída.
- Verificador de publicação web conferido: falha sem o APK e passa com o artefato local ignorado pelo Git. O site existente não foi republicado nesta organização.

## Atualização 0.2.0-demo

- APK demo compilado com sucesso; variante connected também compilada para preservar a integração existente.
- Cinco testes unitários passaram, incluindo os três cenários, abstenção, dados inválidos e as 81 combinações da rubrica.
- Lint demo: zero erros e quatro avisos (versões de SDK/AGP/dependência e concatenação de texto na UI em português).
- Assinatura do novo APK verificada; certificado igual ao da versão 0.1. Identificador `app.lume.mvp`, versionCode 2, Android mínimo 8.
- Manifesto do **APK empacotado** inspecionado: permissão `android.permission.INTERNET` ausente. A função de API também rejeita a variante demo antes de abrir qualquer conexão.
- Respostas, fontes e índices da demo são fixtures locais. A captura real não é processada para gerar os exemplos.
- ADB consultado novamente: nenhum aparelho conectado. Não houve instalação, inspeção visual nativa, teste de arraste ou captura em celular físico/emulador nesta atualização.
- A versão connected não foi conectada a credenciais nem a uma IA real nesta atualização.
- Site e download público do APK verificados com HTTP 200; arquivo baixado idêntico ao APK local, MIME `application/vnd.android.package-archive`. SHA-256: `d617a29fbcba4774669671f0ee06f830ea500d86c322616a0058bc913e4ec510`.

## Verificações anteriores — 0.1

## Executado

- Android: `:app:assembleDebug` — compilação concluída, APK debug gerado.
- Android: `:app:lintDebug` — passou sem erros; avisos de versões mais recentes de target/compile SDK e Gradle. As versões fixadas são uma base de piloto, não uma declaração de conformidade para publicação.
- Assinatura: `apksigner verify --verbose` — assinatura v2 válida.
- Servidor: `npm test` — 11 testes passaram, incluindo autenticação, validação de entradas, ausência de credenciais, exigência de busca/fontes, saída incompleta e tratamento de erros. O provedor foi simulado nesses testes; nenhuma chamada paga foi feita.
- Prévia no navegador: abertura e arraste do mascote, autorização simulada, revisão, explicação com pergunta, resultado de checagem, cancelamento/fechamento e reinício conferidos.
- Prévia: composição desktop e largura de 390 px inspecionadas; sem overflow horizontal no tamanho móvel testado. Console sem erros/avisos no fluxo inspecionado.

## Ainda não executado

- Instalação e uso em celular físico ou emulador. Nenhum dispositivo estava conectado ao ADB.
- Captura sobre Chrome, Instagram e outros aplicativos reais; rotação, tela bloqueada e permissões negadas/revogadas em fabricantes diferentes.
- Teste ponta a ponta da IA com chave, faturamento e acesso ao modelo configurados.
- Medição de latência, bateria, custo por análise e qualidade factual em notícias reais.
- Revisão para publicação na Play Store. Este pacote não foi publicado.

Compilar e passar no lint não comprova o funcionamento do overlay e MediaProjection em todos os aparelhos. A primeira sessão de teste físico é a próxima etapa do MVP.
