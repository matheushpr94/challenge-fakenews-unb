"""Desambiguação antes da pesquisa, com Wikipédia e modelo simulados. Nomes fictícios quando possível."""
import json
import os
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lume import netfetch  # noqa: E402
from lume.service import LumeService, TTLCache  # noqa: E402
from tests.fakeweb import NOW, FakeWeb  # noqa: E402


def analyze(wiki, texto, **kw):
    svc = LumeService(fetch=FakeWeb(wiki=wiki), cache=TTLCache(), clock=lambda: NOW)
    return svc.disambiguate(texto, kw.get("detalhes", ""), kw.get("rejeitadas", []), kw.get("rodada", 0), "d1")


def texts(r):
    return [o["texto"] for o in r["opcoes"]]


MERCURIO = [
    {"title": "Mercúrio", "extract": "Mercúrio pode referir-se a:", "disambiguation": True},
    {"title": "Mercúrio (planeta)", "extract": "Mercúrio é o menor planeta do Sistema Solar."},
    {"title": "Mercúrio (elemento químico)", "extract": "O mercúrio é um metal líquido e tóxico à temperatura ambiente."},
    {"title": "Mercúrio (mitologia)", "extract": "Mercúrio é um deus da mitologia romana."},
]
SOL = [{"title": "Sol", "extract": "O Sol (do latim sol) é a estrela central do Sistema Solar."}]
TERRA = [{"title": "Terra", "extract": "A Terra é o terceiro planeta mais próximo do Sol."}]
PORTO_NOVO = [
    {"title": "Porto Novo Futebol Clube", "extract": "O Porto Novo Futebol Clube é um clube de futebol da cidade de Serra."},
    {"title": "Esporte Clube Porto Novo", "extract": "O Esporte Clube Porto Novo é um clube do litoral."},
    {"title": "Lista de clubes chamados Porto Novo", "extract": "Esta é uma lista."},
]


class RuleDisambiguationTests(unittest.TestCase):
    def test_word_with_several_meanings(self):
        r = analyze(MERCURIO, "Mercúrio")
        self.assertTrue(r["precisa_escolher"])
        self.assertEqual(r["pergunta"], "O que você quer saber?")
        self.assertEqual(r["opcao_detalhes"], "Não é isso — adicionar detalhes")
        self.assertEqual(texts(r), ["O que é Mercúrio (planeta)?", "O que é Mercúrio (elemento químico)?",
                                    "O que é Mercúrio (mitologia)?"])
        self.assertTrue(all(t.endswith("?") for t in texts(r)))

    def test_context_word_selects_meaning_and_goes_direct(self):
        r = analyze(MERCURIO, "Mercúrio é tóxico")
        self.assertFalse(r["precisa_escolher"])

    def test_name_shared_by_institutions(self):
        r = analyze(PORTO_NOVO, "Porto Novo")
        self.assertTrue(r["precisa_escolher"])
        joined = " | ".join(texts(r))
        self.assertIn("Porto Novo Futebol Clube", joined)
        self.assertIn("Esporte Clube Porto Novo", joined)
        self.assertNotIn("Lista", joined)

    def test_characteristic_vs_appearance(self):
        r = analyze(SOL, "sol azul")
        self.assertEqual(texts(r), ["O Sol é azul?", "O Sol pode parecer azul em alguma situação?",
                                    "Existem estrelas azuis?"])

    def test_gender_agreement_from_source(self):
        r = analyze(TERRA, "terra plana")
        self.assertEqual(texts(r)[0], "A Terra é plana?")
        self.assertIn("Existem planetas planos?", texts(r))

    def test_explicit_claim_is_not_ambiguous(self):
        self.assertFalse(analyze(TERRA, "A Terra é plana")["precisa_escolher"])
        self.assertFalse(analyze([], "O Atlético Serrano tem 5 títulos nacionais")["precisa_escolher"])
        self.assertFalse(analyze([], "Vacina causa autismo?")["precisa_escolher"])

    def test_general_question_vs_specific_episode(self):
        r = analyze([], "tubarão atacou surfista")
        self.assertTrue(r["precisa_escolher"])
        self.assertEqual({o["diferenca"] for o in r["opcoes"]}, {"geral_especifico"})
        r2 = analyze([], "Tubarão atacou surfista em Vale Claro ontem")
        self.assertFalse(r2["precisa_escolher"])

    def test_not_this_with_details_combines_and_reinterprets(self):
        first = analyze(SOL, "sol azul")
        r = analyze(SOL, "sol azul", detalhes="vi uma foto em que o sol aparece azul no pôr do sol",
                    rejeitadas=texts(first), rodada=1)
        self.assertFalse(r["precisa_escolher"])
        self.assertEqual(r["entrada_original"], "sol azul")
        self.assertIn("sol azul", r["texto_pesquisa"])
        self.assertIn("vi uma foto", r["texto_pesquisa"])

    def test_rejected_options_are_not_repeated(self):
        first = analyze(MERCURIO, "Mercúrio")
        r = analyze(MERCURIO, "Mercúrio", detalhes="nenhuma dessas", rejeitadas=texts(first), rodada=1)
        self.assertFalse(set(texts(r)) & set(texts(first)))

    def test_round_limit_goes_direct(self):
        r = analyze(MERCURIO, "Mercúrio", detalhes="outro", rodada=2)
        self.assertFalse(r["precisa_escolher"])
        self.assertIn("limite", r["motivo"])

    def test_wikipedia_failure_is_reported_not_hidden(self):
        svc = LumeService(fetch=FakeWeb(fail={"wiki"}), cache=TTLCache(), clock=lambda: NOW)
        r = svc.disambiguate("Mercúrio")
        self.assertFalse(r["precisa_escolher"])
        self.assertIn("indisponível", r["aviso"])


class ModelDisambiguationTests(unittest.TestCase):
    """Tempo, local e categoria dependem do modelo opcional; a API é simulada."""

    def setUp(self):
        self.env = mock.patch.dict(os.environ, {"LUME_LLM": "openai", "OPENAI_API_KEY": "chave-de-teste"})
        self.env.start()

    def tearDown(self):
        self.env.stop()

    def _model(self, payload):
        text = json.dumps(payload)
        return mock.patch.object(netfetch, "post_json", lambda *a, **k: {
            "output": [{"type": "message", "content": [{"type": "output_text", "text": text}]}]})

    def test_time_ambiguity_and_invented_details_are_removed(self):
        ans = {"ambigua": True, "motivo": "tempo", "opcoes": [
            {"pergunta": "Quem venceu a eleição de Vale Claro mais recente?", "diferenca": "tempo"},
            {"pergunta": "Quem venceu todas as eleições de Vale Claro?", "diferenca": "tempo"},
            {"pergunta": "Quem venceu a eleição de Vale Claro em 2018 com apoio de Joana Prado?", "diferenca": "tempo"}]}
        with self._model(ans):
            r = analyze([], "Quem venceu a eleição de Vale Claro?")
        self.assertEqual(r["metodo"], "modelo")
        self.assertTrue(r["precisa_escolher"])
        self.assertEqual(len(r["opcoes"]), 2, "opção com ano e pessoa inventados deve ser descartada")
        self.assertFalse(any("Joana" in t or "2018" in t for t in texts(r)))

    def test_location_options_must_be_grounded_in_context(self):
        wiki = [{"title": "Salário mínimo no Brasil", "extract": "O salário mínimo no Brasil é definido por lei."},
                {"title": "Salário mínimo em Portugal", "extract": "Em Portugal, o salário mínimo é a retribuição mínima."}]
        ans = {"ambigua": True, "motivo": "local", "opcoes": [
            {"pergunta": "Qual é o salário mínimo no Brasil?", "diferenca": "local"},
            {"pergunta": "Qual é o salário mínimo em Portugal?", "diferenca": "local"},
            {"pergunta": "Qual é o salário mínimo na Argentina?", "diferenca": "local"}]}
        with self._model(ans):
            r = analyze(wiki, "Qual é o salário mínimo?")
        self.assertEqual(texts(r), ["Qual é o salário mínimo no Brasil?", "Qual é o salário mínimo em Portugal?"])

    def test_category_ambiguity(self):
        ans = {"ambigua": True, "motivo": "categoria", "opcoes": [
            {"pergunta": "Qual é o maior clube de Vale Claro em número de títulos?", "diferenca": "categoria"},
            {"pergunta": "Qual é o maior clube de Vale Claro em número de torcedores?", "diferenca": "categoria"}]}
        with self._model(ans):
            r = analyze([], "maior clube de Vale Claro")
        self.assertEqual(len(r["opcoes"]), 2)

    def test_model_saying_clear_goes_direct(self):
        with self._model({"ambigua": False, "motivo": "clara", "opcoes": []}):
            r = analyze(SOL, "sol azul")
        self.assertFalse(r["precisa_escolher"])

    def test_verdict_words_rejected(self):
        ans = {"ambigua": True, "motivo": "x", "opcoes": [
            {"pergunta": "É falso que o Sol é azul?", "diferenca": "outro"},
            {"pergunta": "É verdadeiro que o Sol é azul?", "diferenca": "outro"}]}
        with self._model(ans):
            r = analyze(SOL, "Sol azul?")
        self.assertFalse(r["precisa_escolher"])

    def test_model_failure_falls_back_to_rules(self):
        def boom(*a, **k):
            raise netfetch.FetchError("http", "HTTP 500")
        with mock.patch.object(netfetch, "post_json", boom):
            r = analyze(SOL, "sol azul")
        self.assertEqual(r["metodo"], "regras")
        self.assertTrue(r["precisa_escolher"])
        self.assertIn("Modelo indisponível", r["aviso"])


if __name__ == "__main__":
    unittest.main()
