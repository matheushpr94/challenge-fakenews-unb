"""Servidor local do rotulador FactNews (factual / citacao / enviesada) para o app Lume.

Escuta SÓ em 127.0.0.1 (nunca na rede). O emulador/aparelho alcança por `adb reverse tcp:8765 tcp:8765`.
Sem chave, sem API paga, sem envio de texto para fora do computador.

  python scripts/serve.py                  # porta 8765
  python scripts/serve.py --port 9000 --model-dir models/factnews-v2

GET  /health    -> {"ok": true, "model": "...", "device": "cuda", "classes": [...]}
POST /classify  corpo: {"sentences": ["...", ...], "headline": "..."}   (headline opcional, só se o modelo usar contexto)
                resposta: {"results": [{"label": "...", "confidence": 0.93, "probs": {"factual": ..., ...}}, ...]}

O servidor só devolve probabilidades. As regras de decisão (limites, "inconclusivo") ficam no app e vêm de
reports/calibracao_dev.md. O rótulo descreve o PAPEL da frase; não diz se ela é verdadeira.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
MAX_BODY_BYTES = 1_000_000
MAX_SENTENCES = 300
MAX_CHARS = 600            # o modelo usa no máximo 128 tokens; o resto é descartado de qualquer forma

_spec = importlib.util.spec_from_file_location("factnews_predict", HERE / "predict.py")
predict_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(predict_mod)

STATE: dict = {}
LOCK = threading.Lock()    # uma inferência por vez (GPU/MPS compartilhada)


def reply(handler: BaseHTTPRequestHandler, code: int, payload: dict) -> None:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    handler.send_response(code)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(data)))
    handler.end_headers()
    handler.wfile.write(data)


class Handler(BaseHTTPRequestHandler):
    server_version = "FactNewsLocal/1"

    def log_message(self, fmt, *args):  # não registra o texto das frases, só o caminho e o status
        sys.stderr.write(f"[{self.log_date_time_string()}] {self.command} {self.path.split('?')[0]} -> {args[1] if len(args) > 1 else ''}\n")

    def do_GET(self):
        if self.path.split("?")[0] != "/health":
            return reply(self, 404, {"error": "não encontrado"})
        if not self._local_host_only():
            return reply(self, 403, {"error": "Host não permitido"})
        reply(self, 200, {"ok": True, "model": STATE["name"], "device": str(STATE["dev"]), "classes": STATE["cfg"]["classes"]})

    def _local_host_only(self) -> bool:
        """Contra DNS rebinding: só aceita requisições cujo Host seja o endereço local em que o servidor escuta."""
        host = (self.headers.get("Host") or "").strip().lower()
        return host in {f"127.0.0.1:{self.server.server_address[1]}", f"localhost:{self.server.server_address[1]}"}

    def do_POST(self):
        if self.path.split("?")[0] != "/classify":
            return reply(self, 404, {"error": "não encontrado"})
        if not self._local_host_only():
            return reply(self, 403, {"error": "Host não permitido"})
        # Exigir application/json força o navegador a fazer uma checagem prévia (CORS) em páginas de outros sites, que falha.
        if not (self.headers.get("Content-Type") or "").lower().startswith("application/json"):
            return reply(self, 415, {"error": "use Content-Type: application/json"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return reply(self, 400, {"error": "Content-Length inválido"})
        if length <= 0 or length > MAX_BODY_BYTES:
            return reply(self, 413 if length > MAX_BODY_BYTES else 400, {"error": f"corpo vazio ou maior que {MAX_BODY_BYTES} bytes"})
        try:
            body = json.loads(self.rfile.read(length).decode("utf-8"))
            sentences, headline = body["sentences"], str(body.get("headline", ""))[:MAX_CHARS]
            if not isinstance(sentences, list) or not all(isinstance(s, str) for s in sentences):
                raise ValueError("sentences deve ser uma lista de textos")
        except (ValueError, KeyError, UnicodeDecodeError, TypeError) as e:
            return reply(self, 400, {"error": f"requisição inválida: {e}"})
        if len(sentences) > MAX_SENTENCES:
            return reply(self, 413, {"error": f"no máximo {MAX_SENTENCES} frases por chamada"})
        sentences = [s.strip()[:MAX_CHARS] or "." for s in sentences]
        if not sentences:
            return reply(self, 200, {"results": []})
        try:
            with LOCK:
                res = predict_mod.predict(sentences, headline, STATE["bundle"])
        except Exception as e:  # noqa: BLE001
            sys.stderr.write(f"erro na inferência: {type(e).__name__}\n")
            return reply(self, 500, {"error": "falha na inferência"})
        reply(self, 200, {"results": [{"label": r["rotulo"], "confidence": r["confianca"], "probs": r["probabilidades"]} for r in res]})


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8765)
    ap.add_argument("--model-dir", default=str(predict_mod.MODEL_DIR))
    a = ap.parse_args()
    model_dir = Path(a.model_dir)
    print(f"Carregando o modelo de {model_dir} ...", flush=True)
    bundle = predict_mod.load(model_dir)
    STATE.update(bundle=bundle, cfg=bundle[1], dev=bundle[2], name=model_dir.name)
    predict_mod.predict(["Aquecimento do modelo."], "", bundle)   # primeira chamada é a mais lenta
    srv = ThreadingHTTPServer(("127.0.0.1", a.port), Handler)
    print(f"Pronto: http://127.0.0.1:{a.port}  (dispositivo: {bundle[2]}).  Ctrl+C para parar.", flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\nEncerrado.")


if __name__ == "__main__":
    main()
