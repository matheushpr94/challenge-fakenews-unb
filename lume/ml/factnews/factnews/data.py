"""Carregamento e preparo do FactNews v2.0.0 (Vargas et al., 2023; CC BY 4.0).

O arquivo `dataset/factnews_dataset.csv` tem 6.191 frases de 100 histórias (299 artigos: cada história
foi publicada por três veículos, uma delas só por dois). Manchetes e corpo são arquivos separados. A coluna `classe` usa a codificação original: 0 = factual, -1 = citação,
1 = enviesada (conferida pelas contagens: 4.242 / 1.391 / 558). Os modelos usam índices 0..2.
"""
from __future__ import annotations

from pathlib import Path

import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "data" / "raw"
PROCESSED_DIR = ROOT / "data" / "processed"

# codificação original (coluna `classe`) -> índice usado nos modelos
ORIGINAL_TO_INDEX = {0: 0, -1: 1, 1: 2}
CLASS_NAMES = ["factual", "citacao", "enviesada"]

OUTLET_BY_LETTER = {"e": "Estadão", "o": "O Globo", "f": "Folha"}
# nomes aparecem como Estadao / OGlobo / Oglobo / Folha; em alguns arquivos a data ocupa o lugar do nome
OUTLET_BY_FILE = {"estadao": "Estadão", "oglobo": "O Globo", "folha": "Folha"}


def _find(pattern: str) -> Path:
    found = sorted(RAW_DIR.glob(pattern))
    if not found:
        raise FileNotFoundError(
            f"Arquivo não encontrado ({pattern}) em {RAW_DIR}. Rode: python scripts/00_download_data.py"
        )
    return found[0]


def sentences_csv() -> Path:
    return _find("FactNews-v2.0.0/*/dataset/factnews_dataset.csv")


def annotators_csv() -> Path:
    return _find("FactNews-v2.0.0/*/annotators/factnews-agremments.csv")


def load_sentences() -> pd.DataFrame:
    """Uma linha por frase, com rótulo, história e veículo derivados dos identificadores."""
    df = pd.read_csv(sentences_csv(), encoding="utf-8-sig")
    df = df.rename(columns={"classe": "classe_original"})
    df.insert(0, "row_id", range(len(df)))
    df["label"] = df["classe_original"].map(ORIGINAL_TO_INDEX)
    df["label_name"] = df["label"].map(dict(enumerate(CLASS_NAMES)))
    df["story"] = df["id_article"].str[:-1]
    df["outlet_from_id"] = df["id_article"].str[-1].map(OUTLET_BY_LETTER)
    df["outlet_from_file"] = df["file"].str.split("_").str[1].str.lower().map(OUTLET_BY_FILE)
    # O nome no arquivo é mais confiável: na história c78 o artigo do Estadão tem o id `c78o` (colide com O Globo).
    df["outlet"] = df["outlet_from_file"].fillna(df["outlet_from_id"])
    df["article"] = df["file"].str.replace(r"_titulo$", "", regex=True)
    # cada artigo tem dois arquivos: o corpo e a manchete (`..._titulo`); as manchetes também são linhas
    df["is_title"] = df["file"].str.endswith("_titulo")
    df["text"] = df["sentences"].astype(str).str.strip()
    return df


def load_annotators() -> pd.DataFrame:
    """Rótulos dos dois anotadores e dos juízes (mesma ordem das frases; id começa em 1)."""
    return pd.read_csv(annotators_csv())
