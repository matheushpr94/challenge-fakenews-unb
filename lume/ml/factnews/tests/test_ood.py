"""Módulo de distância (ideia 1, não adotada no app): matemática básica, para o experimento ser reproduzível."""
from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from factnews.ood import OodStats, auc, fit_stats, mean_pool  # noqa: E402


class OodTests(unittest.TestCase):
    def test_auc_known_values(self):
        self.assertEqual(1.0, auc([3, 4], [1, 2]))
        self.assertEqual(0.0, auc([1, 2], [3, 4]))
        self.assertEqual(0.5, auc([1, 1], [1, 1]))
        self.assertAlmostEqual(0.75, auc([2, 4], [1, 3]))

    def test_mean_pool_ignores_padding(self):
        h = np.array([[[1.0, 1.0], [3.0, 3.0], [99.0, 99.0]]]); m = np.array([[1, 1, 0]])
        np.testing.assert_allclose([[2.0, 2.0]], mean_pool(h, m))

    def test_typical_points_score_near_one_and_far_points_score_high(self):
        rng = np.random.default_rng(0)
        train = rng.normal(size=(2000, 96))
        st = fit_stats(train, n_components=16)
        typical = st.text_score(rng.normal(size=(300, 96)))
        far = st.text_score(rng.normal(loc=3.0, size=(300, 96)))
        self.assertLess(typical, 1.5)
        self.assertGreater(far, 3 * typical)

    def test_save_and_load_roundtrip(self):
        rng = np.random.default_rng(1)
        st = fit_stats(rng.normal(size=(500, 40)), n_components=8); st.threshold = 1.23
        with tempfile.TemporaryDirectory() as d:
            st.save(Path(d) / "s.npz"); back = OodStats.load(Path(d) / "s.npz")
        x = rng.normal(size=(5, 40))
        np.testing.assert_allclose(st.sentence_distance(x), back.sentence_distance(x))
        self.assertEqual(1.23, back.threshold)


if __name__ == "__main__":
    unittest.main()
