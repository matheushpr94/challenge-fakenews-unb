"""Detector de "texto fora do que o modelo conhece" (ideia 1).

Mede a distância de Mahalanobis entre o texto e as frases de treino no espaço dos embeddings do próprio modelo (média das
representações da última camada), depois de reduzir para poucas dimensões (PCA). Distância alta = estilo/gênero diferente do
corpus de treino (por exemplo, reportagem de revista ou enciclopédia). Não diz nada sobre o conteúdo ser verdadeiro.

Compartilhado por scripts/09_ood.py (experimento) e scripts/serve.py (uso no app).
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np

N_COMPONENTS = 64


def mean_pool(hidden: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """hidden: (n, tokens, dim); mask: (n, tokens) com 1 nos tokens reais. Média sobre os tokens reais."""
    m = mask[..., None].astype(hidden.dtype)
    return (hidden * m).sum(1) / np.maximum(m.sum(1), 1.0)


@dataclass
class OodStats:
    pca_mean: np.ndarray        # (dim,)
    components: np.ndarray      # (k, dim)
    z_mean: np.ndarray          # (k,)
    precision: np.ndarray       # (k, k)
    threshold: float = float("nan")   # limiar do escore de TEXTO (média das distâncias por frase / k)

    def sentence_distance(self, emb: np.ndarray) -> np.ndarray:
        """Distância de Mahalanobis ao quadrado dividida por k (≈ 1 para frases típicas do treino)."""
        z = (emb - self.pca_mean) @ self.components.T - self.z_mean
        return np.einsum("ij,jk,ik->i", z, self.precision, z) / z.shape[1]

    def text_score(self, emb: np.ndarray) -> float:
        return float(self.sentence_distance(emb).mean())

    def save(self, path: Path) -> None:
        np.savez_compressed(path, pca_mean=self.pca_mean, components=self.components, z_mean=self.z_mean,
                            precision=self.precision, threshold=np.array(self.threshold))

    @classmethod
    def load(cls, path: Path) -> "OodStats":
        z = np.load(path)
        return cls(z["pca_mean"], z["components"], z["z_mean"], z["precision"], float(z["threshold"]))


def fit_stats(train_emb: np.ndarray, n_components: int = N_COMPONENTS, seed: int = 0) -> OodStats:
    from sklearn.covariance import LedoitWolf
    from sklearn.decomposition import PCA
    pca = PCA(n_components=n_components, random_state=seed).fit(train_emb)
    z = pca.transform(train_emb)
    lw = LedoitWolf().fit(z)
    return OodStats(pca.mean_.astype(np.float64), pca.components_.astype(np.float64), z.mean(0), lw.precision_.astype(np.float64))


def auc(pos: np.ndarray, neg: np.ndarray) -> float:
    """P(escore de pos > escore de neg), com empates contando meio (AUC de Mann-Whitney)."""
    pos, neg = np.asarray(pos, float), np.asarray(neg, float)
    if len(pos) == 0 or len(neg) == 0:
        return float("nan")
    allv = np.concatenate([pos, neg])
    ranks = allv.argsort().argsort().astype(float)
    # média de ranks para empates
    _, inv, counts = np.unique(allv, return_inverse=True, return_counts=True)
    sums = np.zeros(len(counts)); np.add.at(sums, inv, ranks)
    ranks = (sums / counts)[inv] + 1
    return float((ranks[:len(pos)].sum() - len(pos) * (len(pos) + 1) / 2) / (len(pos) * len(neg)))
