#!/usr/bin/env python3
"""Servidor local do 'sinal do texto' (127.0.0.1:8766). Só biblioteca padrão + spaCy; sem scikit-learn em tempo de execução.
POST /sinal {"texto": "..."} -> faixa (desinformacao | sem_sinal | noticia), posição 0..1 (1 = estilo de boato), termos que pesaram.
GET /saude. Não registra o texto. Aceita só Host local (protege contra páginas web abrindo o endereço no navegador)."""
import json, sys, time
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from sinal import Sinal

AQUI = Path(__file__).parent
FAIXAS = json.loads((AQUI / "faixas.json").read_text(encoding="utf-8"))
MODELO = Sinal()
MODELO.pontuar("A prefeitura anunciou nesta segunda-feira o início das obras de drenagem em três bairros da cidade.")  # aquece o spaCy
MAX_BYTES = 256 * 1024

def faixa_de(decisao, palavras):
    regime = "curto" if palavras < FAIXAS["limite_curto"] else "normal"
    c = FAIXAS["cortes"][regime]
    f = "desinformacao" if decisao < c["desinformacao"] else "noticia" if decisao >= c["noticia"] else "sem_sinal"
    return f, regime

def posicao(decisao):
    lo, hi = FAIXAS["posicao"]["minimo"], FAIXAS["posicao"]["maximo"]
    return round(1.0 - min(1.0, max(0.0, (decisao - lo) / (hi - lo))), 3)   # 1 = estilo de boato

def analisar(texto):
    palavras = len(texto.split())
    if palavras < FAIXAS["min_palavras"]:
        return {"ok": False, "motivo": "texto_curto", "palavras": palavras, "minimo": FAIXAS["min_palavras"]}
    inicio = time.time()
    lem = MODELO.lematizar(texto)
    s2, _, _ = MODELO.decisao(lem)
    # contribuições: todos os termos conhecidos, para listar os que mais empurraram para cada lado
    conta = Counter(g for g in MODELO.ngramas(lem) if g in MODELO.indice)
    pesos = {MODELO.indice[g]: n_ * MODELO.idf[MODELO.indice[g]] for g, n_ in conta.items()}
    norma = (sum(v * v for v in pesos.values()) ** 0.5) or 1.0
    ordem = sorted(((MODELO.coef[i] * v / norma, MODELO.termos[i]) for i, v in pesos.items()))
    boato = [{"termo": t, "peso": round(c, 3)} for c, t in ordem[:8] if c <= -0.03][:5]
    noticia = [{"termo": t, "peso": round(c, 3)} for c, t in reversed(ordem[-8:]) if c >= 0.03][:5]
    f, regime = faixa_de(s2, palavras)
    c = FAIXAS["cortes"][regime]
    return {"ok": True, "faixa": f, "regime": regime, "decisao": round(s2, 3), "posicao": posicao(s2), "palavras": palavras,
            "limites": {"boato": posicao(c["desinformacao"]), "noticia": posicao(c["noticia"])},
            "pesam_boato": boato, "pesam_noticia": noticia, "ms": round((time.time() - inicio) * 1000)}

class H(BaseHTTPRequestHandler):
    def _json(self, code, obj):
        b = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code); self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def _host_ok(self): return (self.headers.get("Host", "").split(":")[0] in ("127.0.0.1", "localhost"))
    def do_GET(self):
        if not self._host_ok(): return self._json(403, {"erro": "host"})
        self._json(200, {"ok": True, "versao": FAIXAS["versao"], "calibrado_em": FAIXAS["data"]}) if self.path == "/saude" else self._json(404, {})
    def do_POST(self):
        if not self._host_ok(): return self._json(403, {"erro": "host"})
        if self.path != "/sinal": return self._json(404, {})
        try:
            n = int(self.headers.get("Content-Length", 0))
            if n <= 0 or n > MAX_BYTES: return self._json(413, {"erro": "tamanho"})
            if "application/json" not in self.headers.get("Content-Type", ""): return self._json(415, {"erro": "tipo"})
            texto = str(json.loads(self.rfile.read(n)).get("texto", ""))
            r = analisar(texto)
            self._json(200, r)
        except Exception as e:
            self._json(400, {"ok": False, "erro": type(e).__name__})
    def log_message(self, fmt, *a): print("sinal", fmt % a, file=sys.stderr, flush=True)

if __name__ == "__main__":
    print("sinal do texto pronto em http://127.0.0.1:8766", file=sys.stderr, flush=True)
    ThreadingHTTPServer(("127.0.0.1", 8766), H).serve_forever()
