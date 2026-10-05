import argparse
import gzip
import json
import math
import re
from collections import Counter
from pathlib import Path

import joblib


TOKEN_PATTERN = re.compile(r"(?u)\b\w\w+\b")


def softmax(values: list[float]) -> list[float]:
    largest = max(values)
    exponentials = [math.exp(value - largest) for value in values]
    total = sum(exponentials)
    return [value / total for value in exponentials]


def mobile_predict(payload: dict, text: str) -> list[float]:
    vectorizer = payload["vectorizer"]
    vocabulary = {term: index for index, term in enumerate(vectorizer["vocabulary"])}
    tokens = TOKEN_PATTERN.findall(text.lower())
    terms = tokens + [f"{left} {right}" for left, right in zip(tokens, tokens[1:])]
    counts = Counter(index for term in terms if (index := vocabulary.get(term)) is not None)

    sparse_values = {
        index: (1.0 + math.log(count)) * vectorizer["idf"][index]
        for index, count in counts.items()
    }
    norm = math.sqrt(sum(value * value for value in sparse_values.values()))
    if norm:
        sparse_values = {index: value / norm for index, value in sparse_values.items()}

    classifier = payload["classifier"]
    scores = []
    for weights, intercept in zip(classifier["coefficients"], classifier["intercepts"]):
        scores.append(intercept + sum(weights[index] * value for index, value in sparse_values.items()))
    return softmax(scores)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("text")
    parser.add_argument("--python-model", type=Path, default=Path("artifacts/liar2_tfidf_logistic.joblib"))
    parser.add_argument("--mobile-model", type=Path, default=Path("artifacts/liar2_mobile_model.json.gz"))
    args = parser.parse_args()

    python_model = joblib.load(args.python_model)
    with gzip.open(args.mobile_model, "rt", encoding="utf-8") as model_file:
        mobile_model = json.load(model_file)

    expected = python_model.predict_proba([args.text])[0]
    actual = mobile_predict(mobile_model, args.text)
    largest_difference = max(abs(left - right) for left, right in zip(expected, actual))
    print(f"Maior diferença entre Python e pacote Android: {largest_difference:.12f}")
    if largest_difference > 1e-9:
        raise SystemExit("A exportação não reproduziu a previsão original.")


if __name__ == "__main__":
    main()

