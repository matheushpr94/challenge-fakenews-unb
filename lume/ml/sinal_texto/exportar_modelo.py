#!/usr/bin/env python3
"""Exporta o pipeline do .pkl (TfidfVectorizer + LinearSVC) para JSON, sem executar código arbitrário do pickle.
Carrega com um unpickler que só aceita classes de scikit-learn/numpy/scipy/joblib. Uso: python exportar_modelo.py [caminho.pkl]"""
import hashlib, json, pickle, sys, warnings
from pathlib import Path
from joblib.numpy_pickle import NumpyUnpickler
warnings.simplefilter("ignore")
OK_PREFIXOS = ("sklearn.", "numpy", "scipy.", "joblib.", "collections", "copyreg", "_codecs")
OK_BUILTINS = {"set", "frozenset", "dict", "list", "tuple", "int", "float", "str", "bool", "slice", "range", "object", "bytes", "complex"}

class Restrito(NumpyUnpickler):
    def find_class(self, modulo, nome):
        if modulo == "builtins" and nome not in OK_BUILTINS or modulo != "builtins" and not modulo.startswith(OK_PREFIXOS):
            raise pickle.UnpicklingError(f"classe bloqueada: {modulo}.{nome}")
        return super().find_class(modulo, nome)

def carregar(caminho):
    with open(caminho, "rb") as f:
        return Restrito(f.name, f, None).load()

if __name__ == "__main__":
    pkl = Path(sys.argv[1] if len(sys.argv) > 1 else "/Users/aluno1/Downloads/modelo_fakerecogna_svm.pkl")
    pipe = carregar(pkl)
    vec, clf = pipe.named_steps["tfidf"], pipe.named_steps["clf"]
    assert type(clf).__name__ == "LinearSVC" and list(clf.classes_) == [0, 1]
    p = vec.get_params()
    assert p["analyzer"] == "word" and p["lowercase"] and p["ngram_range"] == (1, 2) and p["norm"] == "l2" \
        and not p["binary"] and not p["sublinear_tf"] and p["use_idf"] and p["stop_words"] is None and p["strip_accents"] is None
    termos = [None] * len(vec.vocabulary_)
    for t, i in vec.vocabulary_.items(): termos[i] = t
    out = {
        "origem": {"arquivo": pkl.name, "sha256": hashlib.sha256(pkl.read_bytes()).hexdigest(),
                   "pipeline": "TfidfVectorizer(1-2 gramas, 30000 termos, min_df=2) + LinearSVC(C=1)",
                   "classes": {"0": "falsa (checagem/boato)", "1": "verdadeira (notícia comum)"},
                   "treinado_em": "FakeRecogna (texto lematizado, coluna Noticia)"},
        "token_pattern": p["token_pattern"], "intercepto": float(clf.intercept_[0]),
        "termos": termos, "idf": [round(float(x), 7) for x in vec.idf_], "coef": [round(float(x), 7) for x in clf.coef_[0]],
    }
    destino = Path(__file__).parent / "modelo_sinal.json"
    destino.write_text(json.dumps(out, ensure_ascii=False), encoding="utf-8")
    print("ok:", destino, f"{destino.stat().st_size/1e6:.1f} MB | termos {len(termos)} | intercepto {out['intercepto']:.4f}")
