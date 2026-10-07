"""Divisão por HISTÓRIA, com teste lacrado.

A mesma história aparece em três veículos, com frases quase idênticas. Dividir frases ao acaso
colocaria irmãs gêmeas em treino e teste e inflaria as métricas. Aqui todas as frases de uma
história ficam juntas. O fold 0 é o teste e só pode ser lido com `require_test_permission`.
"""
from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.model_selection import StratifiedGroupKFold

from .data import PROCESSED_DIR, ROOT

SEED = 42
N_SPLITS = 5          # fold 0 = teste (~20%); folds 1..4 = validação cruzada no desenvolvimento
TEST_STORIES_FILE = ROOT / "splits" / "test_stories.txt"


def make_partitions(df: pd.DataFrame, seed: int = SEED, n_splits: int = N_SPLITS) -> pd.DataFrame:
    sgkf = StratifiedGroupKFold(n_splits=n_splits, shuffle=True, random_state=seed)
    fold = np.full(len(df), -1, dtype=int)
    for k, (_, idx) in enumerate(sgkf.split(df["text"], df["label"], df["story"])):
        fold[idx] = k
    out = df[["row_id", "story", "outlet"]].copy()
    out["fold"] = fold
    out["partition"] = np.where(fold == 0, "test", "dev")
    return out


def load_or_create(df: pd.DataFrame) -> pd.DataFrame:
    """Cria a divisão uma única vez (determinística) e registra as histórias de teste."""
    path = PROCESSED_DIR / "split.csv"
    if path.exists():
        part = pd.read_csv(path)
        assert len(part) == len(df) and (part["row_id"].to_numpy() == df["row_id"].to_numpy()).all()
        return part
    part = make_partitions(df)
    PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
    part.to_csv(path, index=False)
    TEST_STORIES_FILE.parent.mkdir(parents=True, exist_ok=True)
    stories = sorted(part.loc[part["partition"] == "test", "story"].unique(), key=lambda s: int(s[1:]))
    TEST_STORIES_FILE.write_text("\n".join(stories) + "\n", encoding="utf-8")
    return part


def dev_data(df: pd.DataFrame, part: pd.DataFrame) -> pd.DataFrame:
    """Só o desenvolvimento (sem teste), com a coluna `fold` (1..4) para validação cruzada."""
    merged = df.merge(part[["row_id", "fold", "partition"]], on="row_id")
    return merged[merged["partition"] == "dev"].reset_index(drop=True)


def require_test_permission(flag: bool, motivo: str = "teste lido") -> None:
    """O teste só é lido com --use-test e o uso fica registrado em reports/test_usage.log."""
    if not flag:
        raise SystemExit("O teste está lacrado. Rode com --use-test só quando o modelo estiver decidido.")
    log = ROOT / "reports" / "test_usage.log"
    log.parent.mkdir(parents=True, exist_ok=True)
    from datetime import datetime
    with log.open("a", encoding="utf-8") as f:
        f.write(f"{datetime.now().isoformat(timespec='seconds')} {motivo}\n")


def test_data(df: pd.DataFrame, part: pd.DataFrame, allowed: bool) -> pd.DataFrame:
    require_test_permission(allowed)
    merged = df.merge(part[["row_id", "fold", "partition"]], on="row_id")
    return merged[merged["partition"] == "test"].reset_index(drop=True)
