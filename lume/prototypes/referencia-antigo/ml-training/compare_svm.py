import argparse
import json
from pathlib import Path

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import accuracy_score, classification_report, f1_score
from sklearn.pipeline import FeatureUnion, Pipeline
from sklearn.svm import LinearSVC

from train import LABEL_NAMES, read_split


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compara Linear SVM no LIAR2.")
    parser.add_argument("--train", type=Path, required=True)
    parser.add_argument("--valid", type=Path, required=True)
    parser.add_argument("--test", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("artifacts"))
    return parser.parse_args()


def evaluate(labels: list[int], predictions) -> dict:
    return {
        "accuracy": accuracy_score(labels, predictions),
        "macro_f1": f1_score(labels, predictions, average="macro"),
        "classification_report": classification_report(
            labels,
            predictions,
            labels=sorted(LABEL_NAMES),
            target_names=[LABEL_NAMES[index] for index in sorted(LABEL_NAMES)],
            output_dict=True,
            zero_division=0,
        ),
    }


def main() -> None:
    args = parse_args()
    train_texts, train_labels = read_split(args.train)
    valid_texts, valid_labels = read_split(args.valid)
    test_texts, test_labels = read_split(args.test)

    features = FeatureUnion(
        [
            (
                "words",
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
                "characters",
                TfidfVectorizer(
                    analyzer="char_wb",
                    lowercase=True,
                    ngram_range=(3, 5),
                    min_df=3,
                    max_features=120_000,
                    sublinear_tf=True,
                ),
            ),
        ]
    )
    model = Pipeline(
        [
            ("features", features),
            ("classifier", LinearSVC(C=1.0, class_weight="balanced", random_state=42)),
        ]
    )

    print(f"Treinando Linear SVM com {len(train_texts)} afirmações...", flush=True)
    model.fit(train_texts, train_labels)
    valid_metrics = evaluate(valid_labels, model.predict(valid_texts))
    test_metrics = evaluate(test_labels, model.predict(test_texts))

    args.output.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, args.output / "liar2_word_char_svm.joblib", compress=3)
    (args.output / "svm_metrics.json").write_text(
        json.dumps({"valid": valid_metrics, "test": test_metrics}, indent=2),
        encoding="utf-8",
    )

    print(f"Validação — accuracy: {valid_metrics['accuracy']:.4f} | macro-F1: {valid_metrics['macro_f1']:.4f}")
    print(f"Teste     — accuracy: {test_metrics['accuracy']:.4f} | macro-F1: {test_metrics['macro_f1']:.4f}")


if __name__ == "__main__":
    main()
