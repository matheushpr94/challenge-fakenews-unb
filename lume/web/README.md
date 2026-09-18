# Prévia web do Lume

Site estático em `public/`: três exemplos fictícios, índice explicável e mascote arrastável. Não há chamadas de IA, pesquisas reais, chaves nem servidor de análise no navegador.

[Prévia publicada](https://lume-previa.vercel.app/)

## Rodar localmente

Na pasta `lume/web`:

```sh
python -m http.server 4187 --bind 127.0.0.1 --directory public
```

Abra `http://127.0.0.1:4187`. É necessário servir por HTTP porque o JavaScript usa módulos. Para testar a rubrica:

```sh
node --test tests/evidence.test.mjs
```

## Publicar na Vercel

Use `lume/web` como diretório raiz do projeto Vercel; a saída estática é `public`. A vinculação da conta (`.vercel/`) não é versionada. Uma mudança no Git não atualiza automaticamente o site existente, que foi publicado pela CLI e ainda não foi vinculado a este repositório.

O download aponta para o APK já publicado. APKs ficam fora do Git: **antes de republicar este site**, coloque a versão a distribuir em `public/downloads/lume-demo-0.2.0.apk`, ou atualize o link para o endereço de um artefato/release permanente. Isso evita remover o arquivo atualmente hospedado na Vercel. Atualize também o nome de versão no link quando houver um novo APK.

O build de publicação verifica que o arquivo existe. O APK público 0.2.0-demo tem SHA-256 `d617a29fbcba4774669671f0ee06f830ea500d86c322616a0058bc913e4ec510`; builds locais com outra assinatura terão checksum diferente.
