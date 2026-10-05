#!/bin/sh
set -eu
cd "$(dirname "$0")"
# Isolated port preserves the original Claude prototype on 8770.
export LUME_PORT="${LUME_PORT:-8772}"
export LUME_LLM=""
exec python3 server.py
