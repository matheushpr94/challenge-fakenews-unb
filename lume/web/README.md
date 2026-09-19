# Prévia web do Lume

Site estático em `public/`: três exemplos fictícios, índice explicável e mascote animado e arrastável. Não há chamadas de IA, pesquisas reais, chaves nem servidor de análise no navegador. O rodapé permite pausar as animações; a preferência de redução de movimento do sistema é respeitada.

[Prévia publicada](https://lume-previa.vercel.app/)

## Rodar localmente

Na pasta `lume/web`:

```sh
python -m http.server 4187 --bind 127.0.0.1 --directory public
```

Abra `http://127.0.0.1:4187`. É necessário servir por HTTP porque o JavaScript usa módulos. Para testar a rubrica:

```sh
node --test tests/*.test.mjs
```

## Publicar na Vercel

Use `lume/web` como diretório raiz do projeto Vercel; a saída estática é `public`. A vinculação da conta (`.vercel/`) não é versionada. Uma mudança no Git não atualiza automaticamente o site existente, que foi publicado pela CLI e ainda não foi vinculado a este repositório.

O download aponta para o APK publicado. APKs ficam fora do Git: **antes de republicar este site**, coloque a versão a distribuir em `public/downloads/lume-demo-0.3.0.apk`, ou atualize o link para o endereço de um artefato/release permanente. Preserve também os APKs de versões anteriores ainda vinculados em mensagens/documentos. Atualize o nome de versão no link quando houver um novo APK.

O build de publicação verifica que o arquivo existe. O APK 0.3.0-demo tem SHA-256 `ff8710d51e47ca0ef83e2daa713d81bee2e82ce91b89c2e9fdeeb697e29e80b4`; builds locais com outra assinatura terão checksum diferente.
