"""Ideia 2 (passo 1): concordância dos rótulos do Claude com o gabarito humano do FactNews (frases de desenvolvimento, às cegas).

As 120 frases (40 por classe, sorteadas com semente fixa) foram rotuladas SEM ver o gabarito nem a saída do modelo. Critério fixado
antes de comparar: kappa >= 0,60 e, em "enviesada", precisão >= 0,50 e revocação >= 0,50. Só então os rótulos do Claude podem servir
como MEDIDA APROXIMADA (nunca como treino do classificador, nunca como substituto de anotadores humanos).
Entrada: data/rotulagem_amostra_gold.json (frases + gabarito; não versionado) e reports/rotulos_aproximados/concordancia_claude.json.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from sklearn.metrics import cohen_kappa_score, confusion_matrix, f1_score

ROOT = Path(__file__).resolve().parents[1]
CODE = {"F": 0, "C": 1, "E": 2}
NAMES = ["factual", "citacao", "enviesada"]


def main() -> None:
    gold = json.loads((ROOT / "data" / "rotulagem_amostra_gold.json").read_text(encoding="utf-8"))
    mine = json.loads((ROOT / "reports" / "rotulos_aproximados" / "concordancia_claude.json").read_text(encoding="utf-8"))
    y = np.array([g["gold"] for g in gold]); p = np.array([CODE[c] for c in mine["labels"]])
    assert len(y) == len(p) == 120 and [g["row_id"] for g in gold] == mine["row_ids"], "amostra e rótulos não conferem"
    cm = confusion_matrix(y, p, labels=[0, 1, 2]); kappa = cohen_kappa_score(y, p)
    prec = cm.diagonal() / np.maximum(cm.sum(0), 1); rec = cm.diagonal() / np.maximum(cm.sum(1), 1)
    f1 = f1_score(y, p, average=None, labels=[0, 1, 2]); acc = float((y == p).mean())
    ok = kappa >= 0.60 and prec[2] >= 0.50 and rec[2] >= 0.50
    out = ["# Concordância dos rótulos do Claude com o gabarito humano (120 frases de desenvolvimento, às cegas)\n",
           f"Acordo simples {acc:.2f}; kappa de Cohen **{kappa:.2f}**; F1 macro {f1.mean():.2f}.\n",
           "| Classe | precisão do Claude | revocação do Claude | F1 |", "|---|---:|---:|---:|"]
    out += [f"| {NAMES[i]} | {prec[i]:.2f} | {rec[i]:.2f} | {f1[i]:.2f} |" for i in range(3)]
    out += ["", "Matriz (linhas = gabarito humano; colunas = Claude): factual / citacao / enviesada", "```"] + [str(r.tolist()) for r in cm] + ["```", "",
            f"Critério (kappa >= 0,60 e enviesada com precisão e revocação >= 0,50): **{'ATENDIDO' if ok else 'NÃO atendido'}**. "
            "Com 40 frases por classe, cada taxa tem incerteza de cerca de ±0,15."]
    (ROOT / "reports" / "rotulos_aproximados" / "concordancia.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8"); print("\n".join(out))


if __name__ == "__main__":
    main()
