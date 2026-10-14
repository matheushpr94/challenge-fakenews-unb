"""Gateway do Lume: UMA porta (127.0.0.1:9000) para o celular falar com os modelos locais do Mac.
Repassa /sinal (8766), /score (8765) e /api/embed (Ollama 11434). Toda chamada exige a chave no cabeçalho
X-Lume-Token; sem ela, 401. Não grava o texto recebido. Só biblioteca padrão do Python."""
import hmac, json, os, secrets, sys, urllib.request, urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

AQUI = Path(__file__).parent
PORTA = int(os.environ.get("LUME_GATEWAY_PORT", "9000"))
TOKEN_ARQ = Path(os.environ.get("LUME_GATEWAY_TOKEN_FILE", AQUI / ".token"))
MAX_CORPO = 2_000_000          # 2 MB por pedido
PRAZO = 60                     # segundos esperando o serviço local
ROTAS = {                      # caminho do app -> serviço local (mesmos caminhos que os servidores já usam)
    "/sinal": "http://127.0.0.1:8766/sinal",
    "/score": "http://127.0.0.1:8765/score",
    "/api/embed": "http://127.0.0.1:11434/api/embed",
}
SAUDE = {"sinal": "http://127.0.0.1:8766/saude", "bertimbau": "http://127.0.0.1:8765/saude",
         "ollama": "http://127.0.0.1:11434/api/tags"}


def carregar_token() -> str:
    if TOKEN_ARQ.exists() and TOKEN_ARQ.read_text().strip():
        return TOKEN_ARQ.read_text().strip()
    token = secrets.token_urlsafe(24)
    TOKEN_ARQ.write_text(token)
    try: TOKEN_ARQ.chmod(0o600)
    except OSError: pass
    return token


TOKEN = carregar_token()


def vivo(url: str) -> bool:
    try:
        with urllib.request.urlopen(url, timeout=3) as r: return r.status == 200
    except Exception:
        return False


class Gateway(BaseHTTPRequestHandler):
    server_version = "LumeGateway/1"

    def log_message(self, fmt, *a):   # só método, rota e status; nunca o corpo
        sys.stderr.write("%s %s\n" % (self.command, self.path.split("?")[0]))

    def _json(self, status: int, obj) -> None:
        corpo = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(corpo)))
        self.end_headers(); self.wfile.write(corpo)

    def _autorizado(self) -> bool:
        return hmac.compare_digest(self.headers.get("X-Lume-Token", ""), TOKEN)

    def do_GET(self):
        if not self._autorizado(): return self._json(401, {"erro": "chave ausente ou incorreta"})
        if self.path.split("?")[0] == "/saude":
            estado = {nome: vivo(url) for nome, url in SAUDE.items()}
            return self._json(200, {"ok": all(estado.values()), "servicos": estado})
        self._json(404, {"erro": "rota inexistente"})

    def do_POST(self):
        if not self._autorizado(): return self._json(401, {"erro": "chave ausente ou incorreta"})
        destino = ROTAS.get(self.path.split("?")[0])
        if destino is None: return self._json(404, {"erro": "rota inexistente"})
        tam = int(self.headers.get("Content-Length") or 0)
        if tam <= 0 or tam > MAX_CORPO: return self._json(413, {"erro": "corpo ausente ou grande demais"})
        corpo = self.rfile.read(tam)
        req = urllib.request.Request(destino, data=corpo, method="POST",
                                     headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=PRAZO) as r:
                dados, status = r.read(), r.status
        except urllib.error.HTTPError as e:
            dados, status = e.read(), e.code
        except Exception:
            return self._json(502, {"erro": "serviço local indisponível"})
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(dados)))
        self.end_headers(); self.wfile.write(dados)


if __name__ == "__main__":
    srv = ThreadingHTTPServer(("127.0.0.1", PORTA), Gateway)
    print(f"gateway no ar em 127.0.0.1:{PORTA}", flush=True)
    srv.serve_forever()
