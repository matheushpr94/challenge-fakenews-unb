"""Normalização de caixa alta antes de classificar."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from factnews.text import is_all_caps, normalize_case  # noqa: E402


class NormalizeCaseTests(unittest.TestCase):
    def test_all_caps_headline_becomes_sentence_case(self):
        self.assertEqual("A cruzada eleitoral pelas mulheres", normalize_case("A CRUZADA ELEITORAL PELAS MULHERES"))

    def test_keeps_accents_and_punctuation(self):
        self.assertEqual("As estratégias e as contradições: o que mudou?", normalize_case("AS ESTRATÉGIAS E AS CONTRADIÇÕES: O QUE MUDOU?"))

    def test_first_letter_after_a_quote_is_capitalized(self):
        self.assertEqual('"Não vamos aceitar", disse ele.', normalize_case('"NÃO VAMOS ACEITAR", DISSE ELE.'))

    def test_normal_text_is_untouched(self):
        for s in ("O Senado aprovou o projeto na terça-feira.", "O PT e o PL fecharam acordo no Senado.", "Lula e Moro se reuniram."):
            self.assertEqual(s, normalize_case(s))

    def test_short_acronyms_are_untouched(self):
        for s in ("STF", "PT", "ONU", "CPI"):
            self.assertFalse(is_all_caps(s))
            self.assertEqual(s, normalize_case(s))

    def test_idempotent(self):
        once = normalize_case("A CRUZADA ELEITORAL PELAS MULHERES")
        self.assertEqual(once, normalize_case(once))

    def test_empty_and_symbols(self):
        self.assertEqual("", normalize_case(""))
        self.assertEqual("123 - 456", normalize_case("123 - 456"))


if __name__ == "__main__":
    unittest.main()
