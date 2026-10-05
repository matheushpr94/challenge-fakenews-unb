import argparse
import csv
import gzip
import json
from pathlib import Path

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, f1_score
from sklearn.pipeline import Pipeline


LABEL_NAMES = {
    0: "Pants on Fire",
    1: "False",
    2: "Barely True",
    3: "Half True",
    4: "Mostly True",
    5: "True",
}


def read_split(path: Path) -> tuple[list[str], list[int]]:
    texts: list[str] = []
    labels: list[int] = []

    with path.open(encoding="utf-8-sig", newline="") as csv_file:
        reader = csv.DictReader(csv_file)
        required = {"statement", "label"}
        missing = required.difference(reader.fieldnames or [])
        if missing:
            raise ValueError(f"{path} não contém as colunas: {sorted(missing)}")

        for row in reader:
            statement = row["statement"].strip()
            if statement:
                texts.append(statement)
                labels.append(int(row["label"]))

    return texts, labels


def metrics(y_true: list[int], y_pred: list[int]) -> dict:
    return {
        "accuracy": accuracy_score(y_true, y_pred),
        "macro_f1": f1_score(y_true, y_pred, average="macro"),
        "classification_report": classification_report(
            y_true,
            y_pred,
            labels=sorted(LABEL_NAMES),
            target_names=[LABEL_NAMES[index] for index in sorted(LABEL_NAMES)],
            output_dict=True,
            zero_division=0,
        ),
    }


def export_mobile_model(model: Pipeline, output_path: Path) -> None:
    vectorizer = model.named_steps["tfidf"]
    classifier = model.named_steps["classifier"]
    vocabulary_by_index = [""] * len(vectorizer.vocabulary_)
    for term, index in vectorizer.vocabulary_.items():
        vocabulary_by_index[index] = term

    payload = {
        "format_version": 1,
        "model_type": "tfidf_logistic_regression",
        "labels": LABEL_NAMES,
        "vectorizer": {
            "lowercase": True,
            "ngram_range": [1, 2],
            "sublinear_tf": True,
            "norm": "l2",
            "token_pattern": r"(?u)\b\w\w+\b",
            "vocabulary": vocabulary_by_index,
            "idf": vectorizer.idf_.tolist(),
        },
        "classifier": {
            "classes": classifier.classes_.tolist(),
            "coefficients": classifier.coef_.tolist(),
            "intercepts": classifier.intercept_.tolist(),
        },
    }
    with gzip.open(output_path, "wt", encoding="utf-8") as output_file:
        json.dump(payload, output_file, ensure_ascii=False, separators=(",", ":"))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Treina um baseline de veracidade no LIAR2.")
    parser.add_argument("--train", type=Path, required=True)
    parser.add_argument("--valid", type=Path, required=True)
    parser.add_argument("--test", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("artifacts"))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    train_texts, train_labels = read_split(args.train)
    valid_texts, valid_labels = read_split(args.valid)
    test_texts, test_labels = read_split(args.test)

    model = Pipeline(
        [
            (
                "tfidf",
                TfidfVectorizer(
                    lowercase=True,
                    ngram_range=(1, 2),
                    min_df=2,
                    max_df=0.98,
                    max_features=120_000,
                    sublinear_tf=True,
                ),
            ),
            (
                "classifier",
                LogisticRegression(
                    max_iter=1_500,
                    C=2.0,
                    class_weight="balanced",
                    solver="lbfgs",
                    random_state=42,
                ),
            ),
        ]
    )

    print(f"Treinando com {len(train_texts)} afirmações...")
    model.fit(train_texts, train_labels)

    valid_metrics = metrics(valid_labels, model.predict(valid_texts))
    test_metrics = metrics(test_labels, model.predict(test_texts))

    args.output.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, args.output / "liar2_tfidf_logistic.joblib")
    export_mobile_model(model, args.output / "liar2_mobile_model.json.gz")
    report = {
        "labels": LABEL_NAMES,
        "train_examples": len(train_texts),
        "valid_examples": len(valid_texts),
        "test_examples": len(test_texts),
        "valid": valid_metrics,
        "test": test_metrics,
    }
    (args.output / "metrics.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    print(f"Validação — accuracy: {valid_metrics['accuracy']:.4f} | macro-F1: {valid_metrics['macro_f1']:.4f}")
    print(f"Teste     — accuracy: {test_metrics['accuracy']:.4f} | macro-F1: {test_metrics['macro_f1']:.4f}")
    print(f"Modelo salvo em: {(args.output / 'liar2_tfidf_logistic.joblib').resolve()}")
    print(f"Pacote Android:  {(args.output / 'liar2_mobile_model.json.gz').resolve()}")


if __name__ == "__main__":
    main()
