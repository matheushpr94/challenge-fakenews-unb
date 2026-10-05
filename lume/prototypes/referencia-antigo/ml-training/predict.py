import argparse
from pathlib import Path

import joblib


LABEL_NAMES = {
    0: "Pants on Fire",
    1: "False",
    2: "Barely True",
    3: "Half True",
    4: "Mostly True",
    5: "True",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Testa uma afirmação no modelo LIAR2.")
    parser.add_argument("text", help="Afirmação a classificar")
    parser.add_argument(
        "--model",
        type=Path,
        default=Path("artifacts/liar2_tfidf_logistic.joblib"),
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    model = joblib.load(args.model)
    probabilities = model.predict_proba([args.text])[0]
    ranking = sorted(enumerate(probabilities), key=lambda item: item[1], reverse=True)

    print("Resultado experimental; não representa verificação factual.")
    for label, probability in ranking:
        print(f"{LABEL_NAMES[label]:15} {probability:.2%}")


if __name__ == "__main__":
    main()

