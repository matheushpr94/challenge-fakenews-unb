"""Planilha MISTA de rotulagem (vários assuntos e gêneros) para medir o modelo fora do FactNews.

build  : busca textos em feeds públicos (notícias de agência pública por editoria + colunas, análises e reportagens), amostra frases
         por texto (destaques do modelo, as 3 mais incertas e sorteadas do resto) e grava:
           data/rotulagem_humana/misto_para_rotular.xlsx / .csv   id, frase, rotulo   <- só isto vai para quem rotula
           data/rotulagem_humana/misto_chave.csv                  id, texto, veiculo, assunto, genero, grupo, rotulo_modelo, confianca, peso
score  : python scripts/17_mixed_sheet.py --score ARQUIVO_PREENCHIDO.xlsx   (ou .csv)
         precisão dos destaques, revocação estimada (com os pesos de amostragem) e quebra por assunto e gênero, com IC 95% por bootstrap de textos.
Pré-requisito: servidor do modelo ligado (scripts/start-factnews-server.ps1). Os textos ficam só em data/ (não versionado).
"""
from __future__ import annotations

import argparse
import csv
import importlib.util
import json
import re
import sys
import urllib.request
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "rotulagem_humana"
spec = importlib.util.spec_from_file_location("ext", Path(__file__).with_name("08_external_texts.py"))
ext = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ext)
HL = 0.80
AB = "https://agenciabrasil.ebc.com.br/rss/{}/feed.xml"
# (feed, domínio, veículo, assunto fixo ou None, gênero, nº de textos)
FEEDS = [(AB.format(s), "agenciabrasil.ebc.com.br", "Agência Brasil", a, "noticia", n) for s, a, n in
         [("politica", "política", 6), ("economia", "economia", 6), ("esportes", "esportes", 6), ("cultura", "cultura", 6), ("saude", "saúde", 5),
          ("internacional", "mundo", 5), ("educacao", "educação", 4), ("justica", "justiça", 4), ("pesquisa-e-inovacao", "ciência e tecnologia", 4), ("geral", "cotidiano", 5)]] + [
    ("https://www.brasildefato.com.br/feed", "brasildefato.com.br", "Brasil de Fato (coluna)", None, "opiniao_analise", 6),
    ("https://www.cartacapital.com.br/feed/", "cartacapital.com.br", "CartaCapital", None, "opiniao_analise", 6),
    ("https://theconversation.com/br/articles.atom", "theconversation.com", "The Conversation Brasil", None, "opiniao_analise", 7),
    ("https://piaui.uol.com.br/feed/", "piaui.uol.com.br", "piauí", None, "reportagem", 6),
    ("https://revistaoeste.com/feed/", "revistaoeste.com", "Revista Oeste", None, "opiniao_analise", 5)]
TOPIC_WORDS = {"política": ("politica", "eleic"), "economia": ("economia", "mercado", "negocios"), "esportes": ("esporte", "futebol"), "cultura": ("cultura", "cinema", "musica", "arte"),
               "saúde": ("saude", "bem-estar", "medic"), "mundo": ("mundo", "internacional"), "ciência e tecnologia": ("ciencia", "tecnologia", "clima", "meio-ambiente")}


def topic_from_url(u: str) -> str:
    low = u.lower()
    for t, words in TOPIC_WORDS.items():
        if any(w in low for w in words):
            return t
    return "variado"


def classify(sents: list[str]) -> list[dict]:
    out = []
    for i in range(0, len(sents), 100):
        req = urllib.request.Request("http://127.0.0.1:8765/classify", data=json.dumps({"sentences": sents[i:i + 100]}, ensure_ascii=False).encode("utf-8"),
                                     headers={"Content-Type": "application/json"})
        out += json.load(urllib.request.urlopen(req, timeout=120))["results"]
    return out


def build(a) -> None:
    rng = np.random.default_rng(2026)
    texts, seen = [], set()
    for feed, dom, veic, assunto, genero, quota in FEEDS:
        got = 0
        for u in ext.feed_links(feed, dom):
            if got >= quota or u in seen:
                continue
            if any(x in u for x in ("/video", "/podcast", "/galeria", "/tag/", "/receitas/")):
                continue
            html = ext.fetch_html(u)
            if not html:
                continue
            p = ext.Paras(); p.feed(html); s = ext.sentences("\n".join(p.paras))
            if len(s) < 8:
                continue
            seen.add(u); got += 1
            texts.append({"url": u, "veiculo": veic, "assunto": assunto or topic_from_url(u), "genero": genero, "sents": s[:150]})
        print(f"{veic} ({assunto or 'por URL'}): {got}/{quota}", flush=True)
    per_text = max(6, a.total // max(1, len(texts)))
    rows = []
    for ti, t in enumerate(texts):
        res = classify(t["sents"]); lab = np.array([r["label"] for r in res]); conf = np.array([r["confidence"] for r in res])
        n = len(t["sents"]); hl = np.where((lab == "enviesada") & (conf >= HL))[0]; rest = np.array([i for i in range(n) if i not in set(hl.tolist())])
        n_h = min(len(hl), max(2, per_text // 3)); pick_h = rng.choice(hl, n_h, replace=False) if n_h else np.array([], int)
        unc = rest[np.argsort(conf[rest])[:min(3, len(rest))]]
        pool = np.array([i for i in rest if i not in set(unc.tolist())], int)
        n_r = max(0, min(len(pool), per_text - len(pick_h) - len(unc)))
        pick_r = rng.choice(pool, n_r, replace=False) if n_r else np.array([], int)
        for grp, idx, w in (("destaque", pick_h, len(hl) / max(1, len(pick_h))), ("incerta", unc, 1.0), ("aleatoria", pick_r, len(pool) / max(1, len(pick_r)))):
            for i in idx:
                rows.append({"texto": ti, "url": t["url"], "veiculo": t["veiculo"], "assunto": t["assunto"], "genero": t["genero"], "grupo": grp,
                             "frase": t["sents"][i], "rotulo_modelo": lab[i], "confianca": f"{conf[i]:.3f}", "peso": f"{w:.4f}"})
    rng.shuffle(rows)
    OUT.mkdir(parents=True, exist_ok=True)
    with (OUT / "misto_chave.csv").open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f); w.writerow(["id", "texto", "url", "veiculo", "assunto", "genero", "grupo", "rotulo_modelo", "confianca", "peso"])
        for n, r in enumerate(rows):
            w.writerow([n, r["texto"], r["url"], r["veiculo"], r["assunto"], r["genero"], r["grupo"], r["rotulo_modelo"], r["confianca"], r["peso"]])
    with (OUT / "misto_para_rotular.csv").open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f); w.writerow(["id", "frase", "rotulo"])
        for n, r in enumerate(rows):
            w.writerow([n, r["frase"], ""])
    from openpyxl import Workbook
    from openpyxl.styles import Alignment, Font, PatternFill
    from openpyxl.worksheet.datavalidation import DataValidation
    wb = Workbook(); ws = wb.active; ws.title = "Rotular"; ws.append(["id", "frase", "rotulo (F, C ou E)"])
    for n, r in enumerate(rows):
        ws.append([n, r["frase"], None])
    ws.column_dimensions["A"].width = 7; ws.column_dimensions["B"].width = 110; ws.column_dimensions["C"].width = 18
    for c in ws[1]:
        c.font = Font(bold=True, color="FFFFFF"); c.fill = PatternFill("solid", fgColor="2F4F3E")
    for row in ws.iter_rows(min_row=2):
        row[1].alignment = Alignment(wrap_text=True, vertical="top"); row[2].alignment = Alignment(horizontal="center", vertical="top")
    dv = DataValidation(type="list", formula1='"F,C,E"', allow_blank=True, showErrorMessage=True, errorTitle="Valor inválido", error="Use F, C ou E.")
    ws.add_data_validation(dv); dv.add(f"C2:C{len(rows) + 1}"); ws.freeze_panes = "A2"
    g = wb.create_sheet("Instruções")
    for line in ["Rotule frase a frase, SEM procurar o texto original e sem pensar no que um programa diria. As frases vêm de assuntos e veículos misturados, em ordem aleatória.", "",
                 "F = FATO: a frase relata uma informação ou acontecimento, sem juízo de valor do autor.",
                 "C = CITAÇÃO: a frase reproduz o que alguém disse ou escreveu (entre aspas ou em discurso indireto: 'disse que', 'segundo...').",
                 "E = ENVIESADA: linguagem carregada, avaliação ou opinião do próprio autor (adjetivos de juízo, ironia, ênfase, rótulos pejorativos ou elogiosos).", "",
                 "Dúvida? Escolha a opção mais próxima e não deixe em branco. Se a frase for lixo do site (menu, aviso, chamada para assinar), use F.",
                 "Use a lista suspensa da coluna C, salve e devolva o arquivo."]:
        g.append([line])
    g.column_dimensions["A"].width = 140
    wb.save(OUT / "misto_para_rotular.xlsx")
    c = {k: sum(1 for r in rows if r["grupo"] == k) for k in ("destaque", "incerta", "aleatoria")}
    print(f"{len(texts)} textos, {len(rows)} frases ({c}). Arquivo: {OUT / 'misto_para_rotular.xlsx'}")


def read_labels(path: Path) -> dict[str, str]:
    if path.suffix.lower() == ".xlsx":
        from openpyxl import load_workbook
        ws = load_workbook(path, data_only=True).worksheets[0]
        return {str(r[0]): (str(r[2]).strip().upper() if r[2] is not None else "") for r in ws.iter_rows(min_row=2, values_only=True) if r[0] is not None}
    return {r["id"]: r["rotulo"].strip().upper() for r in csv.DictReader(path.open(encoding="utf-8-sig"))}


def wilson(k: int, n: int) -> tuple[float, float]:
    if n == 0:
        return (float("nan"),) * 2
    z, p = 1.96, k / n
    c = (p + z * z / (2 * n)) / (1 + z * z / n); d = z * np.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / (1 + z * z / n)
    return c - d, c + d


def score(a) -> None:
    from sklearn.metrics import cohen_kappa_score
    labs = read_labels(Path(a.score)); key = list(csv.DictReader((OUT / "misto_chave.csv").open(encoding="utf-8")))
    rows = [dict(r, h=labs.get(r["id"], "")) for r in key]; bad = [r for r in rows if r["h"] not in ("F", "C", "E")]
    sys.stdout.reconfigure(encoding="utf-8")
    print(f"{len(rows) - len(bad)} de {len(rows)} frases com rótulo válido.")
    rows = [r for r in rows if r["h"] in ("F", "C", "E")]; rng = np.random.default_rng(42)

    def metrics(rs):
        hl = [r for r in rs if r["grupo"] == "destaque"]; non = [r for r in rs if r["grupo"] != "destaque"]
        tp = sum(float(r["peso"]) for r in hl if r["h"] == "E"); fn = sum(float(r["peso"]) for r in non if r["h"] == "E")
        return (sum(r["h"] == "E" for r in hl) / len(hl) if hl else float("nan")), (tp / (tp + fn) if tp + fn else float("nan")), len(hl)

    p, rec, n = metrics(rows); k = sum(r["h"] == "E" for r in rows if r["grupo"] == "destaque"); lo, hi = wilson(k, n)
    texts = sorted({r["texto"] for r in rows}); by_t = {t: [r for r in rows if r["texto"] == t] for t in texts}; bs = []
    for _ in range(1000):
        sel = [r for t in rng.choice(texts, len(texts)) for r in by_t[t]]; bs.append(metrics(sel)[1])
    print(f"\nGERAL: precisão dos destaques {p:.2f} (n={n}; IC 95% Wilson [{lo:.2f}; {hi:.2f}]); revocação estimada {rec:.2f} (IC 95% por bootstrap de textos [{np.nanpercentile(bs, 2.5):.2f}; {np.nanpercentile(bs, 97.5):.2f}])")
    pairs = [(r["h"], {"factual": "F", "citacao": "C", "enviesada": "E"}[r["rotulo_modelo"]]) for r in rows]
    print(f"Concordância humano × modelo (kappa, amostra enviesada para o difícil): {cohen_kappa_score([x for x, _ in pairs], [y for _, y in pairs]):.2f}")
    for campo in ("genero", "assunto", "veiculo"):
        print(f"\nPor {campo}: grupo | destaques | precisão | revocação estimada")
        for v in sorted({r[campo] for r in rows}):
            pp, rr, nn = metrics([r for r in rows if r[campo] == v])
            print(f"  {v:28s} {nn:4d}   {pp:.2f}   {rr:.2f}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--total", type=int, default=1000); ap.add_argument("--score")
    a = ap.parse_args()
    score(a) if a.score else build(a)
