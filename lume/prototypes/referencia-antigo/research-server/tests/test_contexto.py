"""Seleção de contexto por entidade + propriedade + período (rede simulada, entidades fictícias)."""
import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lume.context import complete_sentences  # noqa: E402
from lume.interpret import interpret  # noqa: E402
from lume.service import LumeService, TTLCache  # noqa: E402
from tests.fakeweb import NOW, FakeWeb, html_page  # noqa: E402

PAGE = "https://ibe.example.gov.br/vale-claro"
CLAIM = "Vale Claro tem 150 mil habitantes"


def run(wiki=None, paragraphs=None, google=None):
    web = FakeWeb(wiki=wiki or [], google=google or [],
                  bing_web=[{"title": "Vale Claro: habitantes em 2025", "url": PAGE, "snippet": "habitantes"}]
                  if paragraphs else [],
                  pages={PAGE: html_page(paragraphs, published=datetime(2025, 11, 3, tzinfo=timezone.utc))}
                  if paragraphs else {})
    return LumeService(fetch=web, cache=TTLCache(), clock=lambda: NOW).search(CLAIM, "c1"), web


ANSWER = "Vale Claro tem 150 mil habitantes, segundo o censo de 2025."


class ContextTests(unittest.TestCase):
    def test_right_entity_wrong_property_is_not_context(self):
        r, _ = run(wiki=[{"title": "Vale Claro", "extract": (
            "Vale Claro é um município do estado de Serra Alta. O município é dividido em quatro distritos "
            "administrativos, definidos pela lei orgânica municipal.")}], paragraphs=[ANSWER])
        self.assertEqual(r["contexto"], [])

    def test_useful_definition_and_period_context(self):
        useful = ("O número de habitantes de Vale Claro é estimado pelo instituto estadual, com data de referência "
                  "em 1º de julho de cada ano.")
        r, _ = run(paragraphs=[ANSWER, useful])
        self.assertEqual(r["sintese"]["indicacao"]["rotulo"], "tende_verdadeira")
        self.assertEqual(len(r["contexto"]), 1)
        c = r["contexto"][0]
        self.assertEqual(c["texto"], useful)
        self.assertEqual(c["url"], PAGE)
        self.assertEqual(c["motivo"], "definição ou método")

    def test_main_answer_is_not_repeated_as_context(self):
        r, _ = run(paragraphs=[ANSWER])
        self.assertFalse(any("150 mil" in c["texto"] for c in r["contexto"]))

    def test_generic_summary_is_not_context(self):
        r, _ = run(wiki=[{"title": "Vale Claro", "extract": (
            "Vale Claro é uma cidade conhecida pelas festas juninas e pela culinária regional. "
            "A cidade foi fundada por tropeiros e hoje recebe muitos turistas.")}], paragraphs=[ANSWER])
        self.assertEqual(r["contexto"], [])

    def test_interrupted_excerpt_is_not_used(self):
        cut = "O número de habitantes de Vale Claro é estimado pelo instituto estadual com base no"
        r, _ = run(wiki=[{"title": "Vale Claro", "extract": cut}], paragraphs=[ANSWER])
        self.assertEqual(r["contexto"], [])
        self.assertEqual(complete_sentences(cut + " registro..."), [])
        self.assertEqual(complete_sentences("de habitantes de Vale Claro é estimado pelo instituto estadual anualmente."), [])
        self.assertEqual(complete_sentences("Ele mede o número de habitantes de Vale Claro com data de referência anual."), [])
        self.assertEqual(complete_sentences("Além disso, os habitantes de Vale Claro são contados pelo censo estadual."), [])

    def test_sentence_about_another_entity_is_not_context(self):
        other = ("O município de Rio Fundo tem mais moradores que Vale Claro, segundo a contagem de habitantes "
                 "feita pelo instituto estadual.")
        r, _ = run(paragraphs=[ANSWER, other])
        self.assertEqual(r["contexto"], [])

    def test_method_word_in_claim_does_not_count_as_property(self):
        url = "https://pedraazul.example.gov.br/historia"
        web = FakeWeb(bing_web=[{"title": "Inflação oficial de Pedra Azul em 2023", "url": url, "snippet": "Índice oficial."}],
                      pages={url: html_page([
                          "Pedra Azul foi oficialmente reconhecida como município em 1890, segundo registros históricos.",
                          "A inflação de Pedra Azul é calculada pelo instituto municipal com base em uma cesta de 300 itens."])})
        r = LumeService(fetch=web, cache=TTLCache(), clock=lambda: NOW).search(
            "A inflação oficial de Pedra Azul em 2023 foi de 5%", "c2")
        self.assertEqual([c["texto"] for c in r["contexto"]],
                         ["A inflação de Pedra Azul é calculada pelo instituto municipal com base em uma cesta de 300 itens."])

    def test_ordinal_word_is_not_a_method_cue(self):
        r, _ = run(paragraphs=[ANSWER, "Vale Claro ocupa o segundo lugar entre as cidades do estado em número de habitantes."])
        self.assertEqual(r["contexto"], [])

    def test_loose_year_is_not_period_context(self):
        url = "https://serrano.example.org/historia"
        web = FakeWeb(bing_web=[{"title": "Atlético Serrano: títulos nacionais", "url": url, "snippet": "títulos"}],
                      pages={url: html_page([
                          "O Atlético Serrano conquistou oito títulos nacionais ao longo da história.",
                          "O Atlético Serrano venceu títulos nacionais em 1990 e 2004 com o mesmo treinador.",
                          "Fundado em 1920, o Atlético Serrano é um dos clubes nacionais mais populares da região.",
                          "Os títulos nacionais do Atlético Serrano são contabilizados pela federação, que reconhece "
                          "apenas campeonatos organizados por ela."])})
        r = LumeService(fetch=web, cache=TTLCache(), clock=lambda: NOW).search(
            "O Atlético Serrano tem 5 títulos nacionais", "c3")
        self.assertEqual([c["texto"][:40] for c in r["contexto"]], ["Os títulos nacionais do Atlético Serrano"])

    def test_no_adequate_context(self):
        r, _ = run()
        self.assertEqual(r["contexto"], [])

    def test_context_query_targets_the_property(self):
        i = interpret(CLAIM, NOW)
        self.assertIn("habitantes", i.consulta_contexto_propriedade)
        _, web = run(paragraphs=[ANSWER])
        self.assertTrue(any("habitantes" in c and "wikipedia" in c for c in web.calls))

    def test_unread_content_note_is_not_a_general_observation(self):
        google = [{"title": "Vale Claro chega a 150 mil habitantes", "source": "Jornal A",
                   "date": datetime(2026, 9, 1, tzinfo=timezone.utc)}]
        r, _ = run(google=google)
        self.assertFalse(any("título" in o for o in r["achados"]["observacoes"]))


if __name__ == "__main__":
    unittest.main()
