#!/bin/bash
# Liga TODOS os modelos locais do Lume e confere cada um. Uso: bash lume/ml/iniciar_modelos_locais.sh [parar]
#   Ollama (11434): paraphrase-multilingual + lume-mpnet-ajustado | BERTimbau do Matheus (8765) | sinal do texto (8766)
AQUI="$(cd "$(dirname "$0")" && pwd)"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PY_BERT="${LUME_PY_BERT:-/Users/aluno1/Documents/liar2-transformer/.venv/bin/python}"   # precisa de torch + transformers
PY_SINAL="${LUME_PY_SINAL:-$AQUI/sinal_texto/.venv/bin/python}"                          # spaCy + pt_core_news_sm
BERT_DIR="${LUME_BERT_DIR:-/Users/aluno1/Downloads/lume-modelo-6b/1-bertimbau-ajustado}"
MPNET_GGUF="${LUME_MPNET_GGUF:-/Users/aluno1/Downloads/lume-modelo-6b/2-mpnet-ajustado/lume-mpnet-ajustado.gguf}"
LOG="${LUME_LOG:-/tmp/lume-modelos}"; mkdir -p "$LOG"
if [ "$1" = "parar" ]; then pkill -f servidor_bert.py; pkill -f servidor_sinal.py; echo "servidores Python parados (o Ollama continua)"; exit 0; fi
ok(){ echo "  ✔ $1"; }; falha(){ echo "  ✘ $1"; FALHOU=1; }

echo "1) Ollama"
if ! curl -s -m 2 http://127.0.0.1:11434/api/tags >/dev/null; then open -a Ollama 2>/dev/null || (nohup ollama serve >"$LOG/ollama.log" 2>&1 &); for i in $(seq 1 20); do curl -s -m 1 http://127.0.0.1:11434/api/tags >/dev/null && break; sleep 1; done; fi
curl -s -m 3 http://127.0.0.1:11434/api/tags >/dev/null && ok "serviço no ar" || falha "Ollama não respondeu"
ollama list 2>/dev/null | grep -q "paraphrase-multilingual" || { echo "  baixando paraphrase-multilingual…"; ollama pull paraphrase-multilingual >/dev/null 2>&1; }
if ! ollama list 2>/dev/null | grep -q "lume-mpnet-ajustado"; then
  printf 'FROM %s\n' "$MPNET_GGUF" > "$LOG/Modelfile"; ollama create lume-mpnet-ajustado -f "$LOG/Modelfile" >/dev/null 2>&1; fi
for m in paraphrase-multilingual lume-mpnet-ajustado; do   # aquece: o primeiro embed carrega o modelo na memória
  curl -s -m 60 http://127.0.0.1:11434/api/embed -d "{\"model\":\"$m\",\"input\":[\"aquecimento\"],\"keep_alive\":\"60m\"}" | grep -q embeddings && ok "$m carregado" || falha "$m"; done

echo "2) BERTimbau ajustado (nota 'mesmo fato') — porta 8765"
if ! curl -s -m 2 http://127.0.0.1:8765/saude | grep -q ok; then
  LUME_BERT_DIR="$BERT_DIR" nohup "$PY_BERT" "$AQUI/integracao_modelo_matheus/servidor_bert.py" >"$LOG/bert.log" 2>&1 &
  for i in $(seq 1 60); do curl -s -m 1 http://127.0.0.1:8765/saude | grep -q ok && break; sleep 1; done; fi
curl -s -m 3 http://127.0.0.1:8765/saude | grep -q ok && ok "servidor no ar" || falha "servidor BERTimbau (veja $LOG/bert.log)"

echo "3) Sinal do texto (SVM do FakeRecogna) — porta 8766"
if ! curl -s -m 2 http://127.0.0.1:8766/saude | grep -q ok; then
  (cd "$AQUI/sinal_texto" && nohup "$PY_SINAL" servidor_sinal.py >"$LOG/sinal.log" 2>&1 &)
  for i in $(seq 1 40); do curl -s -m 1 http://127.0.0.1:8766/saude | grep -q ok && break; sleep 1; done; fi
curl -s -m 3 http://127.0.0.1:8766/saude | grep -q ok && ok "servidor no ar" || falha "servidor do sinal (veja $LOG/sinal.log)"

echo "4) Ponte do emulador (adb reverse)"
if "$ADB" devices 2>/dev/null | grep -q "device$"; then
  for p in 11434 8765 8766; do "$ADB" reverse tcp:$p tcp:$p >/dev/null 2>&1 && ok "porta $p" || falha "porta $p"; done
else echo "  (nenhum emulador/aparelho conectado; rode de novo depois de abri-lo)"; fi
[ -z "$FALHOU" ] && echo "TUDO LIGADO." || { echo "ALGO FALHOU."; exit 1; }
