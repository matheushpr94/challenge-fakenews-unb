#!/bin/bash
# Liga os modelos, o gateway (porta 9000) e um túnel HTTPS gratuito para o celular usar SEM cabo.
# Uso: bash lume/ml/iniciar_servidor_remoto.sh            (liga e fica mostrando o endereço)
#      bash lume/ml/iniciar_servidor_remoto.sh parar      (desliga gateway e túnel)
AQUI="$(cd "$(dirname "$0")" && pwd)"
LOG="${LUME_LOG:-/tmp/lume-modelos}"; mkdir -p "$LOG"
BIN_DIR="${LUME_BIN_DIR:-$HOME/.lume/bin}"; CLOUDFLARED="${CLOUDFLARED:-$BIN_DIR/cloudflared}"
if [ "$1" = "parar" ]; then
  pkill -f "gateway/gateway.py"; pkill -f "cloudflared tunnel"; pkill -f "caffeinate -dimsu -w"; echo "gateway e túnel parados"; exit 0
fi

echo "1) Modelos locais"
bash "$AQUI/iniciar_modelos_locais.sh" || { echo "Os modelos não subiram; corrija antes de continuar."; exit 1; }

echo "2) Gateway (porta 9000)"
if ! curl -s -m 2 http://127.0.0.1:9000/ >/dev/null 2>&1; then
  nohup python3 "$AQUI/gateway/gateway.py" >"$LOG/gateway.log" 2>&1 &
  for i in $(seq 1 10); do curl -s -m 1 http://127.0.0.1:9000/ >/dev/null 2>&1 && break; sleep 1; done
fi
TOKEN="$(cat "$AQUI/gateway/.token" 2>/dev/null)"
[ -n "$TOKEN" ] || { echo "  ✘ gateway não criou a chave (veja $LOG/gateway.log)"; exit 1; }
curl -s -m 5 -H "X-Lume-Token: $TOKEN" http://127.0.0.1:9000/saude | grep -q '"ok": true' && echo "  ✔ gateway e serviços respondendo" \
  || { echo "  ✘ gateway não confirmou os serviços"; exit 1; }

echo "3) Túnel (cloudflared, gratuito, sem conta)"
if [ ! -x "$CLOUDFLARED" ] && ! command -v cloudflared >/dev/null 2>&1; then
  mkdir -p "$BIN_DIR"
  URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-arm64.tgz"
  echo "  baixando cloudflared (oficial da Cloudflare, ~40 MB) de $URL"
  curl -fsSL "$URL" -o "$BIN_DIR/cloudflared.tgz" && tar -xzf "$BIN_DIR/cloudflared.tgz" -C "$BIN_DIR" && rm -f "$BIN_DIR/cloudflared.tgz" \
    || { echo "  ✘ não consegui baixar o cloudflared"; exit 1; }
  chmod +x "$BIN_DIR/cloudflared"
fi
command -v cloudflared >/dev/null 2>&1 && CLOUDFLARED="$(command -v cloudflared)"
pkill -f "cloudflared tunnel" 2>/dev/null; sleep 1
nohup "$CLOUDFLARED" tunnel --no-autoupdate --url http://127.0.0.1:9000 >"$LOG/tunel.log" 2>&1 &
ENDERECO=""
for i in $(seq 1 40); do
  ENDERECO="$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' "$LOG/tunel.log" | head -1)"
  [ -n "$ENDERECO" ] && break; sleep 1
done
[ -n "$ENDERECO" ] || { echo "  ✘ o túnel não devolveu um endereço (veja $LOG/tunel.log)"; exit 1; }
# espera o endereço público responder
for i in $(seq 1 30); do
  curl -s -m 4 -H "X-Lume-Token: $TOKEN" "$ENDERECO/saude" | grep -q '"ok": true' && PUBLICO=1 && break; sleep 2
done
nohup caffeinate -dimsu -w $$ >/dev/null 2>&1 &   # mantém o Mac acordado enquanto este script rodar

echo
echo "================ COLE NO CELULAR (Lume > Ajustes > Servidor dos modelos) ================"
echo " Endereço: $ENDERECO"
echo " Chave:    $TOKEN"
[ -n "$PUBLICO" ] && echo " Teste público: OK (o endereço já responde)" || echo " Aviso: o endereço público ainda não respondeu; espere um minuto e teste no app."
echo "=========================================================================================="
echo "Mantenha esta janela aberta. Para desligar: bash lume/ml/iniciar_servidor_remoto.sh parar"
wait
