#!/usr/bin/env python3
"""Servidor local do BERTimbau ajustado do Matheus (nota "mesmo fato" por título). Só biblioteca padrão + torch/transformers.
Rode: ../factnews/.venv/bin/python servidor_bert.py     Emulador: adb reverse tcp:8765 tcp:8765
POST /score {"claim": "...", "titles": ["...", ...]} -> {"scores": [0..1, ...]}   GET /saude -> ok"""
import json, os, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import torch
from transformers import AutoTokenizer, AutoModelForSequenceClassification

PASTA = Path(os.environ.get("LUME_BERT_DIR", "/Users/aluno1/Downloads/lume-modelo-6b/1-bertimbau-ajustado"))
tok = AutoTokenizer.from_pretrained(PASTA)
bert = AutoModelForSequenceClassification.from_pretrained(PASTA).eval()

def pontuar(claim, titles):
    out = []
    for i in range(0, len(titles), 32):
        lote = titles[i:i+32]
        enc = tok([claim] * len(lote), [t[:110] for t in lote], truncation=True, max_length=128, padding=True, return_tensors="pt")
        with torch.no_grad():
            out += torch.sigmoid(bert(**enc).logits.squeeze(-1)).reshape(-1).tolist()
    return out

class H(BaseHTTPRequestHandler):
    def _json(self, code, obj):
        b = json.dumps(obj).encode(); self.send_response(code); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def do_GET(self):
        self._json(200, {"ok": True}) if self.path == "/saude" else self._json(404, {})
    def do_POST(self):
        if self.path != "/score": return self._json(404, {})
        try:
            d = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
            self._json(200, {"scores": pontuar(str(d["claim"]), [str(t) for t in d["titles"]])})
        except Exception as e:
            self._json(400, {"erro": str(e)})
    def log_message(self, fmt, *a): print("pedido", fmt % a, file=sys.stderr, flush=True)

if __name__ == "__main__":
    print("BERTimbau pronto em http://127.0.0.1:8765", file=sys.stderr)
    ThreadingHTTPServer(("127.0.0.1", 8765), H).serve_forever()
