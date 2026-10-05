import io
import json
import os
import unittest
from unittest.mock import patch

from server import analyze


class Response(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()


class EvidenceTests(unittest.TestCase):
    @patch.dict(os.environ, {"OPENAI_API_KEY": "test"})
    def test_sources_from_payload_are_returned(self):
        output = {
            "choices": [{
                "message": {
                    "content": "As fontes encontradas relatam que o evento ocorreu, mas a data não foi especificada."
                }
            }]
        }
        sources = [{"title": "Notícia Exemplo", "source": "Portal", "url": "https://example.org/noticia"}]
        result = analyze("Uma afirmação", sources_payload=sources, transport=lambda *_args, **_kwargs: Response(json.dumps(output).encode()))
        self.assertFalse(result["limited"])
        self.assertEqual(result["sources"], [{"title": "Notícia Exemplo", "url": "https://example.org/noticia"}])
        self.assertIn("evento ocorreu", result["summary"])

    @patch.dict(os.environ, {"OPENAI_API_KEY": ""})
    def test_missing_api_key_raises_runtime_error(self):
        with self.assertRaises(RuntimeError):
            analyze("Uma afirmação")


if __name__ == "__main__":
    unittest.main()
