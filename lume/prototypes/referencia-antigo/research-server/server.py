"""Servidor local do protótipo Lume: interface web + API JSON.

Uso: python3 server.py   (variáveis opcionais: LUME_HOST, LUME_PORT, LUME_LOG_LEVEL)"""
import json
import logging
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from lume.service import LumeService

ROOT = Path(__file__).resolve().parent
WEB = ROOT / "web"
MAX_BODY = 20_000
STATIC_TYPES = {".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8",
                ".css": "text/css; charset=utf-8", ".svg": "image/svg+xml", ".md": "text/markdown; charset=utf-8"}

log = logging.getLogger("lume.http")
service = None


class Handler(BaseHTTPRequestHandler):
    server_version = "LumePrototipo/0.1"

    def log_message(self, fmt, *args):
        log.info("%s %s", self.address_string(), fmt % args)

    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        data = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Security-Policy",
                         "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/health":
            return self._send(200, {"status": "ok", "servico": "lume-prototipo"})
        if path == "/api/docs":
            return self._send(200, (ROOT / "docs" / "API.md").read_bytes(), STATIC_TYPES[".md"])
        name = "index.html" if path in ("/", "/index.html") else path.lstrip("/")
        allowed = {p.name: p for p in WEB.iterdir() if p.is_file()}
        if name not in allowed:  # só arquivos existentes na pasta web, sem subcaminhos
            return self._send(404, {"erro": "não encontrado"})
        file = allowed[name]
        return self._send(200, file.read_bytes(), STATIC_TYPES.get(file.suffix, "application/octet-stream"))

    def do_POST(self):
        path = self.path.split("?", 1)[0]
        if path not in ("/api/search", "/api/sintese", "/api/interpretar"):
            return self._send(404, {"erro": "não encontrado"})
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            length = -1
        if length <= 0 or length > MAX_BODY:
            return self._send(400, {"status": "entrada_invalida", "mensagem": "Corpo ausente ou grande demais."})
        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            return self._send(400, {"status": "entrada_invalida", "mensagem": "JSON inválido."})
        texto = payload.get("texto") if isinstance(payload, dict) else None
        rid = payload.get("id_consulta") if isinstance(payload, dict) else None
        if path == "/api/interpretar":
            detalhes = payload.get("detalhes") or ""
            rejeitadas = payload.get("rejeitadas") or []
            rodada = payload.get("rodada") or 0
            if not isinstance(texto, str) or not texto.strip() or len(texto) > 5000:
                return self._send(400, {"status": "entrada_invalida", "mensagem": "Informe o campo 'texto'."})
            if not isinstance(detalhes, str) or len(detalhes) > 2000 or not isinstance(rejeitadas, list) \
                    or len(rejeitadas) > 20 or not all(isinstance(r, str) and len(r) <= 300 for r in rejeitadas) \
                    or not isinstance(rodada, int) or not 0 <= rodada <= 10:
                return self._send(400, {"status": "entrada_invalida", "mensagem": "Parâmetros inválidos."})
            return self._send(200, service.disambiguate(texto, detalhes, rejeitadas, rodada, rid))
        if path == "/api/sintese":
            if not isinstance(rid, str) or not rid or len(rid) > 64:
                return self._send(400, {"status": "entrada_invalida", "mensagem": "Informe 'id_consulta'."})
            return self._send(200, service.synthesize_with_model(rid))
        if not isinstance(texto, str) or not texto.strip():
            return self._send(400, {"status": "entrada_invalida", "mensagem": "Informe o campo 'texto'."})
        if len(texto) > 5000:
            return self._send(400, {"status": "entrada_invalida", "mensagem": "Texto longo demais (máx. 5000)."})
        if rid is not None and (not isinstance(rid, str) or len(rid) > 64):
            return self._send(400, {"status": "entrada_invalida", "mensagem": "'id_consulta' inválido."})
        try:
            result = service.evaluate(texto, rid)
        except Exception:
            log.exception("erro interno na pesquisa")
            return self._send(500, {"status": "erro", "id_consulta": rid,
                                    "mensagem": "Erro interno do Lume ao processar a pesquisa."})
        code = {"entrada_invalida": 400, "erro": 502}.get(result["status"], 200)
        return self._send(code, result)


def main():
    global service
    level = os.environ.get("LUME_LOG_LEVEL", "INFO").upper()
    (ROOT / "logs").mkdir(exist_ok=True)
    logging.basicConfig(level=level, format="%(asctime)s %(levelname)s %(name)s: %(message)s",
                        handlers=[logging.StreamHandler(sys.stderr),
                                  logging.FileHandler(ROOT / "logs" / "lume.log", encoding="utf-8")])
    service = LumeService()
    host = os.environ.get("LUME_HOST", "127.0.0.1")
    port = int(os.environ.get("LUME_PORT", "8770"))
    httpd = ThreadingHTTPServer((host, port), Handler)
    log.info("Lume em http://%s:%d", host, port)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
