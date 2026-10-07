"""Garantias do pipeline: rótulos, histórias, divisão sem vazamento e teste lacrado.

Rode na pasta lume/ml/factnews:  python -m unittest discover -s tests -v
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from factnews.data import CLASS_NAMES, load_sentences  # noqa: E402
from factnews.splits import dev_data, load_or_create, make_partitions, require_test_permission, test_data  # noqa: E402


class DataTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.df = load_sentences()

    def test_label_counts_match_the_published_table(self):
        counts = self.df["label_name"].value_counts().to_dict()
        self.assertEqual({"factual": 4242, "citacao": 1391, "enviesada": 558}, {c: counts[c] for c in CLASS_NAMES})
        self.assertEqual(6191, len(self.df))

    def test_stories_and_outlets(self):
        self.assertEqual(100, self.df["story"].nunique())
        self.assertTrue((self.df.groupby("story")["outlet"].nunique() == 3).all())
        self.assertFalse(self.df["label"].isna().any())

    def test_headlines_are_marked(self):
        self.assertEqual(656, int(self.df["is_title"].sum()))


class SplitTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.df = load_sentences()
        cls.part = load_or_create(cls.df)

    def test_no_story_in_two_partitions(self):
        sets = self.part.groupby("partition")["story"].apply(set).to_dict()
        self.assertEqual(set(), sets["dev"] & sets["test"])

    def test_dev_folds_do_not_share_stories(self):
        dev = dev_data(self.df, self.part)
        per_story_folds = dev.groupby("story")["fold"].nunique()
        self.assertTrue((per_story_folds == 1).all())
        self.assertEqual([1, 2, 3, 4], sorted(dev["fold"].unique()))

    def test_test_is_about_a_fifth_and_has_every_class(self):
        test = self.df.merge(self.part[["row_id", "partition"]], on="row_id")
        test = test[test["partition"] == "test"]
        self.assertTrue(0.15 <= len(test) / len(self.df) <= 0.25)
        self.assertEqual(3, test["label"].nunique())

    def test_split_is_deterministic(self):
        again = make_partitions(self.df)
        self.assertTrue((again["fold"].to_numpy() == self.part["fold"].to_numpy()).all())

    def test_test_set_is_sealed(self):
        with self.assertRaises(SystemExit):
            require_test_permission(False)
        with self.assertRaises(SystemExit):
            test_data(self.df, self.part, allowed=False)


if __name__ == "__main__":
    unittest.main()
