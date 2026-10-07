"""Servidor local do rotulador: contrato da API e proteções (com um modelo de mentira, sem carregar pesos).

Rode na pasta lume/ml/factnews:  python -m unittest discover -s tests -v
"""
from __future__ import annotations

import http.client
import importlib.util
import json
import sys
import threading
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

_spec = importlib.util.spec_from_file_location("factnews_serve_under_test", ROOT / "scripts" / "serve.py")
serve = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(serve)

CLASSES = ["factual", "citacao", "enviesada"]


class StubPredict:
    """Substitui o módulo predict: devolve 'factual' para tudo, ou levanta erro quando pedido."""
    fail = False
    seen: list[list[str]] = []

    def predict(self, sentences, headline, bundle):
        if self.fail:
            raise RuntimeError("falha simulada")
        self.seen.append(list(sentences))
        return [{"frase": s, "rotulo": "factual", "confianca": 0.9,
                 "probabilidades": {"factual": 0.9, "citacao": 0.05, "enviesada": 0.05}} for s in sentences]


class ServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.stub = StubPredict()
        cls.original = serve.predict_mod
        serve.predict_mod = cls.stub
        serve.STATE.update(bundle=None, cfg={"classes": CLASSES}, dev="cpu", name="stub")
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), serve.Handler)
        cls.port = cls.httpd.server_address[1]
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown(); cls.httpd.server_close()
        serve.predict_mod = cls.original

    def setUp(self):
        self.stub.fail = False
        self.stub.seen.clear()

    def call(self, method, path, body=None, headers=None, host=None, raw_length=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        conn.putrequest(method, path, skip_host=True)
        conn.putheader("Host", host or f"127.0.0.1:{self.port}")
        for k, v in (headers or {}).items():
            conn.putheader(k, v)
        data = b"" if body is None else (body if isinstance(body, bytes) else body.encode("utf-8"))
        if method == "POST":
            conn.putheader("Content-Length", str(raw_length if raw_length is not None else len(data)))
        conn.endheaders()
        if data:
            conn.send(data)
        resp = conn.getresponse()
        payload = json.loads(resp.read().decode("utf-8") or "{}")
        conn.close()
        return resp.status, payload

    JSON = {"Content-Type": "application/json; charset=utf-8"}

    def classify(self, sentences, **kw):
        return self.call("POST", "/classify", json.dumps({"sentences": sentences}, ensure_ascii=False), self.JSON, **kw)

    def test_health(self):
        code, body = self.call("GET", "/health")
        self.assertEqual(200, code)
        self.assertTrue(body["ok"])
        self.assertEqual(CLASSES, body["classes"])

    def test_classify_returns_one_result_per_sentence_in_order(self):
        code, body = self.classify(["Primeira frase.", "Segunda frase.", "Terceira frase."])
        self.assertEqual(200, code)
        self.assertEqual(3, len(body["results"]))
        self.assertEqual(["factual"] * 3, [r["label"] for r in body["results"]])
        self.assertEqual({"factual", "citacao", "enviesada"}, set(body["results"][0]["probs"]))
        self.assertEqual(["Primeira frase.", "Segunda frase.", "Terceira frase."], self.stub.seen[0])

    def test_portuguese_text_survives_the_round_trip(self):
        self.classify(["A decisão da ministra não agradou à população do país."])
        self.assertEqual("A decisão da ministra não agradou à população do país.", self.stub.seen[0][0])

    def test_empty_list_is_ok(self):
        self.assertEqual((200, {"results": []}), self.classify([]))

    def test_rejects_non_json_content_type(self):
        code, _ = self.call("POST", "/classify", '{"sentences":["x"]}', {"Content-Type": "text/plain"})
        self.assertEqual(415, code)
        self.assertEqual([], self.stub.seen)

    def test_rejects_a_foreign_host_header_for_post_and_get(self):
        self.assertEqual(403, self.classify(["x"], host="evil.example:8765")[0])
        self.assertEqual(403, self.call("GET", "/health", host="evil.example")[0])
        self.assertEqual([], self.stub.seen)

    def test_accepts_localhost_as_host(self):
        self.assertEqual(200, self.classify(["x"], host=f"localhost:{self.port}")[0])

    def test_rejects_invalid_json_and_wrong_shapes(self):
        self.assertEqual(400, self.call("POST", "/classify", "não é json", self.JSON)[0])
        self.assertEqual(400, self.call("POST", "/classify", '{"sentences":"abc"}', self.JSON)[0])
        self.assertEqual(400, self.call("POST", "/classify", '{"sentences":[1,2]}', self.JSON)[0])
        self.assertEqual(400, self.call("POST", "/classify", '{"outra":[]}', self.JSON)[0])
        self.assertEqual(400, self.call("POST", "/classify", "", self.JSON)[0])
        self.assertEqual(400, self.call("POST", "/classify", b"\xff\xfe\x00", self.JSON)[0])

    def test_limits_sentences_and_body_size(self):
        self.assertEqual(413, self.classify(["a"] * (serve.MAX_SENTENCES + 1))[0])
        self.assertEqual(200, self.classify(["a"] * serve.MAX_SENTENCES)[0])
        self.assertEqual(413, self.call("POST", "/classify", None, self.JSON, raw_length=serve.MAX_BODY_BYTES + 1)[0])

    def test_very_long_sentences_are_truncated_before_the_model(self):
        self.classify(["x" * 5000])
        self.assertEqual(serve.MAX_CHARS, len(self.stub.seen[0][0]))

    def test_inference_error_gives_500_without_leaking_details(self):
        self.stub.fail = True
        code, body = self.classify(["frase qualquer para testar o erro."])
        self.assertEqual(500, code)
        self.assertEqual({"error": "falha na inferência"}, body)

    def test_unknown_paths_are_404(self):
        self.assertEqual(404, self.call("GET", "/outra")[0])
        self.assertEqual(404, self.call("POST", "/outra", "{}", self.JSON)[0])

    def test_binds_only_to_loopback(self):
        self.assertEqual("127.0.0.1", self.httpd.server_address[0])


if __name__ == "__main__":
    unittest.main()
