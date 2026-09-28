> **Substituído.** O app agora pesquisa diretamente no aparelho (ver `RESEARCH-NATIVE.md`).
> Este documento descreve a integração intermediária pelo Mac, mantida só como referência.

# Pesquisa integrada ao Android

O OCR ML Kit, seleção de imagens, limpeza de texto, metadados e visual Kotlin/Compose foram preservados.
A pesquisa principal usa uma cópia do protótipo Claude em `research-server/`.
O projeto original do Claude não foi alterado. Esta cópia é independente, não sincroniza alterações futuras automaticamente.
Os antigos clientes de pesquisa e servidor de evidências foram preservados, mas não fazem parte do novo fluxo principal.

## Executar

No terminal:

```sh
cd /Users/aluno1/AndroidStudioProjects/LumeOCRTest
./research-server/run-local.sh
```

Depois execute Run no Android Studio. Selecione uma imagem ou edite a pergunta e toque em Buscar contexto e notícias.
No emulador o endereço é `http://10.0.2.2:8772`. A porta separada mantém o protótipo original na 8770.
A configuração “Conexão com a pesquisa” permite mudar o endereço sem recompilar.
Para aparelho físico: inicie com `LUME_HOST=0.0.0.0 ./research-server/run-local.sh`, use `http://IP_DO_MAC:8772` na mesma rede e permita acesso no firewall.

O backend precisa continuar rodando no computador. Não está hospedado nem embutido no APK.
O script desativa o modelo opcional: esta versão não precisa de chave e não usa API paga.
Para contato no User-Agent da Wikipédia, configure LUME_CONTACT com um contato real do projeto.

## Fluxo

Imagem → ML Kit → texto/afirmação editável → /api/interpretar → opções quando necessário → /api/search.
As respostas antigas são descartadas e cancelar uma pesquisa cancela a conexão Android.
A síntese, fontes com trechos e contexto útil são apresentados; publicações secundárias ficam recolhidas.
Falhas técnicas aparecem separadas de ausência de fontes.

## Limitações

As regras do backend comparam principalmente quantidades e anos. Não entendem qualquer afirmação livre.
Correção ortográfica ampla e comparação semântica ainda não foram adicionadas.
As limitações de fontes RSS, leitura de páginas, relevância e comparação do README do backend continuam valendo.
A integração não torna os resultados garantidos ou conclusivos.

## Verificação

```sh
cd research-server
python3 -m unittest discover -s tests -t .
cd ..
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```
