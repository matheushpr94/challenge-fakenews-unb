"""Acesso HTTP com limite de tempo e tamanho, e bloqueio de endereços internos."""
import http.client
import ipaddress
import os
import re
import socket
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass

USER_AGENT = os.environ.get(
    "LUME_USER_AGENT", "Mozilla/5.0 (compatible; LumePrototipo/0.1; uso educacional)"
)
ALLOWED_PORTS = (None, 80, 443)
BLOCKED_HOST_SUFFIXES = (".localhost", ".local", ".internal", ".lan", ".home.arpa")


class FetchError(Exception):
    """Falha técnica ao acessar uma URL. `kind` é um código curto para logs e para a API."""

    def __init__(self, kind, message):
        super().__init__(message)
        self.kind = kind


@dataclass
class Response:
    url: str
    status: int
    content_type: str
    body: bytes
    truncated: bool = False

    def text(self):
        m = re.search(r"charset=([\w\-]+)", self.content_type or "", re.I)
        enc = m.group(1) if m else None
        if not enc:
            head = self.body[:4096].decode("ascii", "ignore")
            m = re.search(r"<meta[^>]+charset=[\"']?([\w\-]+)", head, re.I) or re.search(
                r"encoding=[\"']([\w\-]+)", head
            )
            enc = m.group(1) if m else "utf-8"
        try:
            return self.body.decode(enc, errors="replace")
        except LookupError:
            return self.body.decode("utf-8", errors="replace")


def _check_ip(ip_text):
    try:
        ip = ipaddress.ip_address(ip_text.split("%")[0])
    except ValueError:
        raise FetchError("url_invalida", f"endereço inválido: {ip_text}")
    if not ip.is_global or ip.is_multicast:
        raise FetchError("url_bloqueada", "endereço interno ou reservado não permitido")


def check_url(url, resolve=True):
    """Valida esquema, porta e host. Com `resolve`, confere todos os IPs do DNS."""
    if not isinstance(url, str) or len(url) > 2048:
        raise FetchError("url_invalida", "URL ausente ou longa demais")
    try:
        parts = urllib.parse.urlsplit(url.strip())
        port = parts.port
    except ValueError:
        raise FetchError("url_invalida", "URL malformada")
    if parts.scheme not in ("http", "https"):
        raise FetchError("url_bloqueada", f"esquema não permitido: {parts.scheme or '(vazio)'}")
    host = (parts.hostname or "").lower()
    if not host:
        raise FetchError("url_invalida", "URL sem host")
    if parts.username or parts.password:
        raise FetchError("url_bloqueada", "URL com credenciais não permitida")
    if port not in ALLOWED_PORTS:
        raise FetchError("url_bloqueada", f"porta não permitida: {port}")
    if host == "localhost" or host.endswith(BLOCKED_HOST_SUFFIXES):
        raise FetchError("url_bloqueada", "host interno não permitido")
    try:
        ipaddress.ip_address(host)
        _check_ip(host)
        return parts
    except ValueError:
        pass
    if resolve:
        try:
            infos = socket.getaddrinfo(host, port or (443 if parts.scheme == "https" else 80),
                                       proto=socket.IPPROTO_TCP)
        except (socket.gaierror, UnicodeError):
            raise FetchError("dns", f"não foi possível resolver {host}")
        for info in infos:
            _check_ip(info[4][0])
    return parts


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    # Redirecionamentos são seguidos manualmente para validar cada destino.
    def redirect_request(self, *args, **kwargs):
        return None


def _ssl_context():
    """Python do python.org no macOS não usa o chaveiro do sistema; procura um pacote de CAs."""
    candidates = [os.environ.get("SSL_CERT_FILE")]
    try:
        import certifi
        candidates.append(certifi.where())
    except ImportError:
        pass
    candidates.append("/etc/ssl/cert.pem")
    for path in candidates:
        if path and os.path.exists(path):
            return ssl.create_default_context(cafile=path)
    return ssl.create_default_context()


_opener = urllib.request.build_opener(_NoRedirect, urllib.request.HTTPSHandler(context=_ssl_context()))


def fetch(url, timeout=8.0, max_bytes=1_500_000, max_redirects=4, accept="*/*", user_agent=None):
    """GET com prazo total, limite de bytes (trunca) e validação de cada redirecionamento."""
    deadline = time.monotonic() + timeout
    for _ in range(max_redirects + 1):
        check_url(url)
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise FetchError("tempo_esgotado", "tempo esgotado")
        req = urllib.request.Request(url, headers={
            "User-Agent": user_agent or USER_AGENT,
            "Accept": accept,
            "Accept-Language": "pt-BR,pt;q=0.9,en;q=0.5",
        })
        try:
            resp = _opener.open(req, timeout=remaining)
        except urllib.error.HTTPError as e:
            if e.code in (301, 302, 303, 307, 308):
                location = e.headers.get("Location")
                if not location:
                    raise FetchError("http", f"redirecionamento sem destino (HTTP {e.code})")
                url = urllib.parse.urljoin(url, location)
                continue
            raise FetchError("http", f"HTTP {e.code}")
        except urllib.error.URLError as e:
            reason = e.reason
            if isinstance(reason, ssl.SSLError):
                raise FetchError("tls", f"falha TLS/certificado: {reason}")
            if isinstance(reason, (socket.timeout, TimeoutError)) or "timed out" in str(reason):
                raise FetchError("tempo_esgotado", "tempo esgotado")
            raise FetchError("rede", f"falha de rede: {reason}")
        except (socket.timeout, TimeoutError):
            raise FetchError("tempo_esgotado", "tempo esgotado")
        except (http.client.HTTPException, ValueError, OSError) as e:
            raise FetchError("rede", f"falha de conexão: {e}")
        with resp:
            chunks, total, truncated = [], 0, False
            try:
                while True:
                    if time.monotonic() > deadline:
                        raise FetchError("tempo_esgotado", "tempo esgotado durante a leitura")
                    chunk = resp.read(65536)
                    if not chunk:
                        break
                    chunks.append(chunk)
                    total += len(chunk)
                    if total >= max_bytes:
                        truncated = True
                        break
            except (socket.timeout, TimeoutError):
                raise FetchError("tempo_esgotado", "tempo esgotado durante a leitura")
            except (http.client.HTTPException, OSError) as e:
                raise FetchError("rede", f"leitura interrompida: {e}")
            return Response(url=resp.geturl(), status=resp.status,
                            content_type=resp.headers.get("Content-Type", ""),
                            body=b"".join(chunks)[:max_bytes], truncated=truncated)
    raise FetchError("redirecionamentos", "redirecionamentos demais")


def post_json(url, payload, headers=None, timeout=30.0, max_bytes=2_000_000):
    """POST JSON para um serviço fixo (ex.: API do modelo). Não segue redirecionamentos."""
    import json
    check_url(url)
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=body, method="POST", headers={
        "Content-Type": "application/json", "User-Agent": USER_AGENT, **(headers or {})})
    try:
        with _opener.open(req, timeout=timeout) as resp:
            data = resp.read(max_bytes)
    except urllib.error.HTTPError as e:
        raise FetchError("http", f"HTTP {e.code}")
    except urllib.error.URLError as e:
        if isinstance(e.reason, (socket.timeout, TimeoutError)) or "timed out" in str(e.reason):
            raise FetchError("tempo_esgotado", "tempo esgotado")
        raise FetchError("rede", f"falha de rede: {e.reason}")
    except (socket.timeout, TimeoutError):
        raise FetchError("tempo_esgotado", "tempo esgotado")
    except (http.client.HTTPException, OSError) as e:
        raise FetchError("rede", f"falha de conexão: {e}")
    try:
        return json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, ValueError):
        raise FetchError("conteudo_invalido", "resposta não é JSON")
