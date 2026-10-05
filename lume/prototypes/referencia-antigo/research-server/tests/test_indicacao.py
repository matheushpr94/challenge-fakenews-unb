"""Indicação provisória (tende a ser verdadeira / falsa / inconclusiva) com rede simulada.
Nomes fictícios; as regras testadas são gerais (valor, unidade, categoria, período, negação)."""
import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lume.service import LumeService, TTLCache  # noqa: E402
from tests.fakeweb import NOW, FakeWeb, html_page  # noqa: E402

PAGE = "https://serrano.example.org/historia"


def d(y, m, day):
    return datetime(y, m, day, 10, 0, tzinfo=timezone.utc)


def web_with(paragraphs, title="Atlético Serrano: história e títulos", published=None):
    return FakeWeb(bing_web=[{"title": title, "url": PAGE, "snippet": "Informações sobre o Atlético Serrano."}],
                   pages={PAGE: html_page(paragraphs, published=published or d(2026, 5, 2))})


def evaluate(web, text):
    return LumeService(fetch=web, cache=TTLCache(), clock=lambda: NOW).evaluate(text, "i1")


def label(r):
    return r["sintese"]["indicacao"]["rotulo"]


class IndicationTests(unittest.TestCase):
    def test_support(self):
        r = evaluate(web_with(["O Atlético Serrano conquistou cinco títulos nacionais."]),
                     "O Atlético Serrano tem 5 títulos nacionais")
        ind = r["sintese"]["indicacao"]
        self.assertEqual(ind["rotulo"], "tende_verdadeira")
        self.assertEqual(ind["texto"], "Tende a ser verdadeira")
        self.assertIn("única fonte", ind["motivo"])
        self.assertEqual(ind["aviso"], "Indicação baseada nas fontes consultadas; não é uma garantia de veracidade.")

    def test_contradiction(self):
        r = evaluate(web_with(["O Atlético Serrano conquistou oito títulos nacionais."]),
                     "O Atlético Serrano tem 5 títulos nacionais")
        self.assertEqual(label(r), "tende_falsa")
        self.assertIn("oito títulos", " ".join(e["texto"] for e in r["sintese"]["explicacao"]))

    def test_divergence_is_inconclusive(self):
        a, b = "https://a.example.com/x", "https://b.example.com/x"
        web = FakeWeb(bing_web=[{"title": "Dengue em Vale Claro em 2020", "url": a, "snippet": "casos"},
                                {"title": "Balanço da dengue de 2020 em Vale Claro", "url": b, "snippet": "casos"}],
                      pages={a: html_page(["Vale Claro registrou 300 casos de dengue em 2020."]),
                             b: html_page(["Vale Claro teve 450 casos de dengue em 2020."])})
        r = evaluate(web, "Em 2020, Vale Claro registrou 300 casos de dengue")
        self.assertEqual(label(r), "inconclusiva")
        self.assertIn("divergentes", r["sintese"]["indicacao"]["motivo"])

    def test_absence_of_evidence_is_inconclusive_not_false(self):
        r = evaluate(FakeWeb(), "O Atlético Serrano tem 5 títulos nacionais")
        self.assertEqual(label(r), "inconclusiva")
        self.assertIn("não indica falsidade", r["sintese"]["indicacao"]["motivo"])

    def test_different_period(self):
        r = evaluate(web_with(["A inflação de Pedra Azul foi de 7% em 2022."], title="Inflação de Pedra Azul"),
                     "A inflação de Pedra Azul foi de 5% em 2023")
        self.assertEqual(label(r), "inconclusiva")

    def test_different_category(self):
        r = evaluate(web_with(["O Atlético Serrano conquistou oito títulos nacionais."]),
                     "O Atlético Serrano tem 8 títulos estaduais")
        self.assertEqual(label(r), "inconclusiva")

    def test_category_missing_in_input_is_not_assumed(self):
        r = evaluate(web_with(["O Atlético Serrano conquistou oito títulos nacionais."]),
                     "O Atlético Serrano tem 8 títulos")
        self.assertEqual(label(r), "inconclusiva")
        self.assertTrue(any("categoria" in e["texto"] for e in r["sintese"]["explicacao"]))

    def test_only_related_content(self):
        r = evaluate(web_with(["O Atlético Serrano inaugurou um novo centro de treinamento."]),
                     "O Atlético Serrano tem 5 títulos nacionais")
        self.assertEqual(label(r), "inconclusiva")

    def test_number_without_explicit_entity_is_not_enough(self):
        r = evaluate(web_with(["O clube conquistou oito títulos nacionais ao longo da história."]),
                     "O Atlético Serrano tem 5 títulos nacionais")
        self.assertEqual(label(r), "inconclusiva")

    def test_many_title_only_results_do_not_justify_a_tendency(self):
        items = [{"title": "Atlético Serrano tem 5 títulos nacionais, diz torcida", "source": f"Portal {i}",
                  "date": d(2026, 9, i + 1)} for i in range(6)]
        r = evaluate(FakeWeb(google=items), "O Atlético Serrano tem 5 títulos nacionais")
        self.assertEqual(label(r), "inconclusiva")

    def test_newer_page_without_fact_period_does_not_win(self):
        a, b = "https://a.example.com/pop", "https://b.example.com/pop"
        web = FakeWeb(bing_web=[{"title": "Habitantes de Vale Claro", "url": a, "snippet": "habitantes"},
                                {"title": "Vale Claro: habitantes", "url": b, "snippet": "habitantes"}],
                      pages={a: html_page(["Vale Claro tem 120 mil habitantes."], published=d(2016, 1, 1)),
                             b: html_page(["Vale Claro tem 150 mil habitantes."], published=d(2026, 9, 1))})
        r = evaluate(web, "Vale Claro tem 150 mil habitantes")
        self.assertEqual(label(r), "inconclusiva")

    def test_negation_is_applied_to_the_compared_value(self):
        r = evaluate(web_with(["O Atlético Serrano conquistou oito títulos nacionais."]),
                     "O Atlético Serrano não tem 5 títulos nacionais")
        self.assertEqual(label(r), "tende_verdadeira")
        r2 = evaluate(web_with(["O Atlético Serrano conquistou cinco títulos nacionais."]),
                      "O Atlético Serrano não tem 5 títulos nacionais")
        self.assertEqual(label(r2), "tende_falsa")

    def test_rules_cannot_interpret_free_text(self):
        web = FakeWeb(google=[{"title": "Prefeito de Vale Claro assina decreto de emergência", "source": "Jornal A",
                               "date": d(2026, 9, 25)}])
        r = evaluate(web, "O prefeito de Vale Claro não assinou o decreto de emergência")
        self.assertEqual(label(r), "inconclusiva")
        self.assertIn("regras", r["sintese"]["indicacao"]["motivo"])

    def test_opinion_is_inconclusive(self):
        self.assertEqual(label(evaluate(FakeWeb(), "Vale Claro é a melhor cidade do estado")), "inconclusiva")

    def test_network_failure_is_technical_failure(self):
        r = evaluate(FakeWeb(fail={"google", "bing_news", "bing_web", "wiki"}), "O Atlético Serrano tem 5 títulos nacionais")
        self.assertEqual(label(r), "falha_tecnica")
        self.assertNotIn("evidência", r["sintese"]["indicacao"]["texto"])

    def test_question_without_claim_gets_answer_not_true_or_false(self):
        r = evaluate(web_with(["O Atlético Serrano conquistou oito títulos nacionais."]),
                     "Quantos títulos nacionais o Atlético Serrano tem?")
        self.assertEqual(label(r), "resposta")
        r2 = evaluate(FakeWeb(), "Quantos títulos nacionais o Atlético Serrano tem?")
        self.assertEqual(label(r2), "sem_resposta")

    def test_several_claims_are_evaluated_separately(self):
        web = web_with(["O Atlético Serrano conquistou oito títulos nacionais.",
                        "O estádio do Atlético Serrano tem 20 mil lugares, segundo o clube."])
        r = evaluate(web, "O Atlético Serrano tem 5 títulos nacionais. O estádio do Atlético Serrano tem 20 mil lugares.")
        self.assertEqual(r["modo"], "partes")
        self.assertNotIn("sintese", r, "a notícia inteira não recebe uma classificação única")
        self.assertEqual([label(p) for p in r["partes"]], ["tende_falsa", "tende_verdadeira"])

    def test_two_values_in_one_sentence_are_separate_details(self):
        r = evaluate(web_with(["O Atlético Serrano conquistou oito títulos nacionais e dois títulos estaduais."]),
                     "O Atlético Serrano tem 8 títulos nacionais e 3 títulos estaduais")
        self.assertEqual(label(r), "tende_verdadeira")
        self.assertEqual(len(r["detalhes_adicionais"]), 1)
        self.assertEqual(r["detalhes_adicionais"][0]["indicacao"]["rotulo"], "tende_falsa")


if __name__ == "__main__":
    unittest.main()
