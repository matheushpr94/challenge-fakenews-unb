"""Baseline honesto: Dummy -> TF-IDF + Regressão Logística, medido SÓ no desenvolvimento.

Compara dois jeitos de validar, para mostrar quanto a divisão ao acaso infla a métrica:
  - ao acaso: StratifiedKFold sobre frases (gêmeas entre veículos podem cair dos dois lados);
  - por história: as frases de uma história ficam juntas (o jeito correto).
O teste (fold 0) não é lido aqui.
Saída: reports/baseline_dev.json e reports/baseline_dev.md
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from sklearn.dummy import DummyClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, f1_score
from sklearn.model_selection import StratifiedKFold
from sklearn.pipeline import FeatureUnion, Pipeline

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from factnews.data import CLASS_NAMES, load_sentences  # noqa: E402
from factnews.splits import dev_data, load_or_create  # noqa: E402

REPORTS = Path(__file__).resolve().parents[1] / "reports"
SEED = 42


def tfidf_lr() -> Pipeline:
    feats = FeatureUnion([
        ("palavras", TfidfVectorizer(ngram_range=(1, 2), min_df=2, sublinear_tf=True, lowercase=True)),
        ("caracteres", TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 5), min_df=3, sublinear_tf=True, lowercase=False)),
    ])
    return Pipeline([("tfidf", feats), ("lr", LogisticRegression(max_iter=3000, C=3.0, class_weight="balanced"))])


def evaluate(make_model, X: np.ndarray, y: np.ndarray, folds) -> dict:
    pred = np.full(len(y), -1)
    for tr, va in folds:
        m = make_model()
        m.fit(X[tr], y[tr])
        pred[va] = m.predict(X[va])
    rep = classification_report(y, pred, target_names=CLASS_NAMES, output_dict=True, zero_division=0)
    per_fold = [f1_score(y[va], pred[va], average="macro") for _, va in folds]
    return {"macro_f1": rep["macro avg"]["f1-score"], "macro_f1_por_fold": per_fold,
            "f1": {c: rep[c]["f1-score"] for c in CLASS_NAMES},
            "precisao": {c: rep[c]["precision"] for c in CLASS_NAMES},
            "revocacao": {c: rep[c]["recall"] for c in CLASS_NAMES}}


def main() -> None:
    df = load_sentences()
    part = load_or_create(df)
    dev = dev_data(df, part)
    X, y = dev["text"].to_numpy(), dev["label"].to_numpy()
    print(f"Desenvolvimento: {len(dev)} frases, {dev['story'].nunique()} histórias (teste lacrado: {int((part['partition']=='test').sum())} frases)")

    # CV por história: os folds 1..4 definidos em splits.py
    by_story = [(np.where(dev["fold"] != k)[0], np.where(dev["fold"] == k)[0]) for k in sorted(dev["fold"].unique())]
    k = len(by_story)
    at_random = list(StratifiedKFold(n_splits=k, shuffle=True, random_state=SEED).split(X, y))

    res = {
        "dev_frases": int(len(dev)), "dev_historias": int(dev["story"].nunique()), "folds": k,
        "dummy_mais_frequente": evaluate(lambda: DummyClassifier(strategy="most_frequent"), X, y, by_story),
        "tfidf_lr_por_historia": evaluate(tfidf_lr, X, y, by_story),
        "tfidf_lr_ao_acaso": evaluate(tfidf_lr, X, y, at_random),
    }
    REPORTS.mkdir(parents=True, exist_ok=True)
    (REPORTS / "baseline_dev.json").write_text(json.dumps(res, ensure_ascii=False, indent=2), encoding="utf-8")

    linhas = ["# Baseline no desenvolvimento (teste lacrado)", "",
              f"{res['dev_frases']} frases, {res['dev_historias']} histórias, {k} folds.", "",
              "| Modelo / validação | F1 macro | F1 factual | F1 citação | F1 enviesada |", "|---|---:|---:|---:|---:|"]
    for nome, chave in (("Dummy (classe mais frequente)", "dummy_mais_frequente"),
                        ("TF-IDF + LR, por história (correto)", "tfidf_lr_por_historia"),
                        ("TF-IDF + LR, ao acaso (inflado)", "tfidf_lr_ao_acaso")):
        r = res[chave]
        linhas.append(f"| {nome} | {r['macro_f1']:.3f} | {r['f1']['factual']:.3f} | {r['f1']['citacao']:.3f} | {r['f1']['enviesada']:.3f} |")
    r = res["tfidf_lr_por_historia"]
    linhas += ["", "F1 macro por fold (por história): " + ", ".join(f"{v:.3f}" for v in r["macro_f1_por_fold"]),
               "", "Revocação por classe (por história): " + ", ".join(f"{c} {r['revocacao'][c]:.3f}" for c in CLASS_NAMES),
               "Precisão por classe (por história): " + ", ".join(f"{c} {r['precisao'][c]:.3f}" for c in CLASS_NAMES)]
    (REPORTS / "baseline_dev.md").write_text("\n".join(linhas), encoding="utf-8")
    print("\n".join(linhas))


if __name__ == "__main__":
    main()
