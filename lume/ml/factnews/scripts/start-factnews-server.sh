#!/usr/bin/env bash
# macOS/Linux: liga o servidor local do rotulador FactNews e a ponte ADB para o emulador/aparelho.
# Uso (na pasta lume/ml/factnews):  ./scripts/start-factnews-server.sh   (variáveis opcionais: PORT, ADB, NO_ADB=1)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${PORT:-8765}"
PY="$ROOT/.venv/bin/python"
[ -x "$PY" ] || { echo "Ambiente Python não encontrado em $PY. Siga o README (seção 'Como reproduzir')."; exit 1; }
[ -f "$ROOT/models/factnews-v2/factnews_config.json" ] || {
  echo "Pesos do modelo não encontrados em models/factnews-v2. Baixe-os (README, seção 'Usar o modelo treinado') ou treine: scripts/04_context_ensemble.py --export --use-test --ctx none --seeds 42 --tag factnews-v2"; exit 1; }
if [ -z "${NO_ADB:-}" ]; then
  ADB="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
  if [ -x "$ADB" ] && "$ADB" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null 2>&1; then echo "Ponte ADB pronta (tcp:$PORT)."
  else echo "Aviso: sem adb ou sem emulador/aparelho conectado; rode de novo depois de abri-lo (ou NO_ADB=1)."; fi
fi
PYTHONIOENCODING=utf-8 exec "$PY" "$ROOT/scripts/serve.py" --port "$PORT"
