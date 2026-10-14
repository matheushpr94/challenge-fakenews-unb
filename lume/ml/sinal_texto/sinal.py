"""Pontuação do 'sinal do texto': lematiza (spaCy pt), calcula TF-IDF e a distância ao hiperplano do LinearSVC.
Sem scikit-learn em tempo de execução; paridade com o pipeline original verificada em teste_paridade.py."""
import json, math, re
from collections import Counter
from pathlib import Path

AQUI = Path(__file__).parent
TOKEN = re.compile(r"(?u)\b\w\w+\b")

class Sinal:
    def __init__(self, caminho=AQUI / "modelo_sinal.json", spacy_modelo="pt_core_news_sm"):
        d = json.loads(Path(caminho).read_text(encoding="utf-8"))
        self.termos = d["termos"]
        self.indice = {t: i for i, t in enumerate(self.termos)}
        self.idf, self.coef, self.intercepto = d["idf"], d["coef"], d["intercepto"]
        self.origem = d["origem"]
        self._nlp = None; self._spacy_modelo = spacy_modelo

    @property
    def nlp(self):
        if self._nlp is None:
            import spacy
            self._nlp = spacy.load(self._spacy_modelo, disable=["ner", "parser"])
        return self._nlp

    def lematizar(self, texto: str) -> str:
        """Texto cru -> lemas em minúsculas, sem pontuação, números e palavras funcionais (como a coluna Noticia)."""
        doc = self.nlp(re.sub(r"\s+", " ", texto).strip()[:20000])
        out = []
        for t in doc:
            if t.is_punct or t.is_space or t.like_num or t.like_url or t.is_stop: continue
            out.extend(w for w in t.lemma_.lower().split() if w)
        return " ".join(out)

    def ngramas(self, lematizado: str):
        toks = TOKEN.findall(lematizado.lower())
        return toks + [f"{a} {b}" for a, b in zip(toks, toks[1:])]

    def decisao(self, lematizado: str, top=0):
        """Distância assinada ao hiperplano (positivo = estilo de notícia comum; negativo = estilo de boato/checagem)."""
        cont = Counter(g for g in self.ngramas(lematizado) if g in self.indice)
        if not cont: return 0.0, [], 0
        pesos = {self.indice[g]: n * self.idf[self.indice[g]] for g, n in cont.items()}
        norma = math.sqrt(sum(v * v for v in pesos.values())) or 1.0
        contrib = {i: v / norma * self.coef[i] for i, v in pesos.items()}
        s = sum(contrib.values()) + self.intercepto
        termos = sorted(contrib.items(), key=lambda kv: kv[1])[:top] if top else []
        return s, [(self.termos[i], c) for i, c in termos], len(cont)

    def pontuar(self, texto: str, top=0):
        lem = self.lematizar(texto)
        s, termos, n = self.decisao(lem, top)
        return {"decisao": s, "termos": termos, "ngramas_conhecidos": n, "palavras_lematizadas": len(lem.split())}
