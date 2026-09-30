#!/usr/bin/env bash
# macOS/Linux: prepara a IA local do Lume (Ollama + modelo de embeddings) e a ponte ADB para o emulador/aparelho.
# Uso: ./setup/start-lume-local-ai.sh    (variáveis opcionais: ADB, OLLAMA_MODEL)
set -euo pipefail
MODEL="${OLLAMA_MODEL:-paraphrase-multilingual}"
ADB="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
command -v ollama >/dev/null || { echo "Ollama não encontrado. Instale: brew install ollama  (ou https://ollama.com/download)"; exit 1; }
[ -x "$ADB" ] || { echo "ADB não encontrado em $ADB. Instale o Android SDK Platform-Tools ou defina ADB=/caminho/adb"; exit 1; }

if ! curl -sf -m 2 http://127.0.0.1:11434/api/tags >/dev/null; then
  echo "Iniciando o Ollama..."
  (open -a Ollama 2>/dev/null || nohup ollama serve >/tmp/ollama-lume.log 2>&1 &) ; sleep 4
  curl -sf -m 5 http://127.0.0.1:11434/api/tags >/dev/null || { echo "Ollama não respondeu em 127.0.0.1:11434 (veja /tmp/ollama-lume.log)"; exit 1; }
fi
if ! ollama list | grep -q "^${MODEL}"; then
  echo "Baixando $MODEL (aprox. 560 MB)..."; ollama pull "$MODEL"
fi
# Teste real do endpoint usado pelo app.
curl -sf -m 60 http://127.0.0.1:11434/api/embed -d "{\"model\":\"$MODEL\",\"input\":[\"teste\"]}" | grep -q embeddings \
  || { echo "O modelo $MODEL não gerou embeddings"; exit 1; }
"$ADB" get-state >/dev/null 2>&1 || { echo "Nenhum emulador/aparelho conectado ao ADB. Inicie o emulador e rode de novo."; exit 1; }
"$ADB" reverse tcp:11434 tcp:11434
"$ADB" reverse --list | grep -q 11434 && echo "IA local do Lume pronta ($MODEL). Mantenha o Ollama aberto."
