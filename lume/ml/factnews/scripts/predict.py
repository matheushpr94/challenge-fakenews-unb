"""Rotula frases com o modelo treinado (factual / citacao / enviesada).

Uso:
  python scripts/predict.py "A ministra disse que o projeto será votado." "O governo fracassou de novo."
  python scripts/predict.py --headline "Senado aprova projeto" --file frases.txt   # uma frase por linha
  python scripts/predict.py --json "frase"                                         # saída em JSON
Requer os pesos em models/factnews-v2 (gerados por: 04_context_ensemble.py --export --use-test --tag factnews-v2 ...).
Se a configuração usa contexto (ctx = headline), passe a manchete do artigo em --headline; sem ela o contexto fica vazio.
Texto em CAIXA ALTA é convertido para caixa normal antes de classificar (o treino quase não tem caixa alta).
Rotula o PAPEL da frase dentro da notícia; não diz se ela é verdadeira.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from factnews.text import normalize_case  # noqa: E402

MODEL_DIR = Path(__file__).resolve().parents[1] / "models" / "factnews-v2"


def load(model_dir: Path = MODEL_DIR):
    cfg_path = model_dir / "factnews_config.json"
    if not cfg_path.exists():
        raise SystemExit(f"Pesos não encontrados em {model_dir}. Veja o README (seção 'Exportar o modelo').")
    cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
    dev = torch.device("cuda" if torch.cuda.is_available() else "mps" if getattr(torch.backends, "mps", None) and torch.backends.mps.is_available() else "cpu")
    models = []
    for seed in cfg["seeds"]:
        d = model_dir / f"seed{seed}"
        models.append((AutoTokenizer.from_pretrained(d), AutoModelForSequenceClassification.from_pretrained(d).float().to(dev).eval()))
    return models, cfg, dev


def predict(sentences: list[str], headline: str = "", bundle=None, batch: int = 64) -> list[dict]:
    models, cfg, dev = bundle or load()
    w = np.array(cfg["decision_weights"], dtype=float)
    out = []
    headline = normalize_case(headline)
    for i in range(0, len(sentences), batch):
        originals = sentences[i:i + batch]
        chunk = [normalize_case(t) for t in originals]   # texto em CAIXA ALTA muda a resposta do modelo
        probs = []
        for tok, model in models:
            args = (chunk, [headline] * len(chunk)) if cfg["ctx"] == "headline" else (chunk,)
            kw = dict(truncation="longest_first" if cfg["ctx"] == "headline" else True, max_length=cfg["max_len"], padding=True, return_tensors="pt")
            enc = tok(*args, **kw).to(dev)
            with torch.no_grad():
                probs.append(torch.softmax(model(**enc).logits.float(), dim=-1).cpu().numpy())
        p = np.mean(probs, axis=0)
        for text, row in zip(originals, p):
            k = int((row * w).argmax())
            out.append({"frase": text, "rotulo": cfg["classes"][k], "confianca": round(float(row[k]), 3),
                        "probabilidades": {c: round(float(v), 3) for c, v in zip(cfg["classes"], row)}})
    return out


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("frases", nargs="*"); ap.add_argument("--file"); ap.add_argument("--headline", default="")
    ap.add_argument("--model-dir", default=str(MODEL_DIR)); ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    texts = list(a.frases)
    if a.file:
        texts += [l.strip() for l in Path(a.file).read_text(encoding="utf-8").splitlines() if l.strip()]
    if not texts:
        raise SystemExit("Passe frases como argumentos ou use --file.")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    results = predict(texts, a.headline, load(Path(a.model_dir)))
    if a.json:
        print(json.dumps(results, ensure_ascii=False, indent=2))
    else:
        for r in results:
            print(f'[{r["rotulo"]:9s} {r["confianca"]:.2f}] {r["frase"]}')
