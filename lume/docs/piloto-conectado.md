# Piloto com IA real

Esta configuração se aplica à variante `connected`. O APK demo distribuído ao grupo permanece offline e não usa estes dados.

## Servidor

Requer Node.js 22+, chave do provedor com faturamento e um modelo disponível na conta que aceite imagens e busca web. O modelo atualmente configurado no código é um valor inicial; ainda não foi validado com chamadas reais.

Na pasta `lume/server`, copie `.env.example` para `.env`. Preencha `OPENAI_API_KEY`, confira `OPENAI_MODEL` e gere um token do piloto:

```sh
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

Use o valor em `LUME_CLIENT_TOKEN` (mínimo de 32 caracteres). Guarde a chave apenas no servidor e não versione `.env`. Execute `npm start`. O endpoint `/health` informa se as variáveis estão preenchidas; não valida acesso ao modelo nem crédito.

## Android conectado

Na pasta `lume/android`:

```powershell
.\gradlew.bat :app:assembleConnectedDebug
adb install -r app/build/outputs/apk/connected/debug/app-connected-debug.apk
adb reverse tcp:8787 tcp:8787
```

O app tem identificador `app.lume.mvp.connected`, separado da demo. Em **Mais opções → Configurar conexão**, use `http://127.0.0.1:8787` com o redirecionamento USB e o token do piloto. HTTP é permitido apenas no build debug; para acesso remoto, hospedar com HTTPS.

Ao tocar em Explicar ou Checar, essa variante envia o conteúdo para análise real e pode gerar cobrança. Não apresenta os índices fictícios da demo. Capturas ficam em memória no app, mas o provedor recebe o conteúdo enviado. `store:false` não deve ser apresentado como garantia de retenção zero pelo provedor.

## Antes do piloto remoto

Validar o modelo e a qualidade das citações, revisar privacidade/retenção, substituir o token compartilhado por autenticação apropriada e definir limites de gasto. Os limites atuais de 10 tentativas/minuto e duas análises simultâneas por instância não são cotas individuais. Não habilitar chamadas pagas por padrão na demo.
