"""Baixa textos EXTERNOS ao FactNews para testar o detector de "texto fora do que o modelo conhece" (ideia 1) e medir o sinal em
estilos diferentes (ideia 2). Os textos ficam só em data/external/ (não versionado); o que vai para o Git é o manifesto
reports/externos_manifesto.json (endereço, gênero, nº de frases, SHA-256), nunca o texto.

Gêneros: enciclopedia (Wikipédia pt), revista_analise (reportagem longa e análise), noticia_controle (agência pública de notícias:
é NOTÍCIA, então o detector NÃO deve marcá-la como estranha).
Uso: python scripts/08_external_texts.py
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
import time
import urllib.parse
from html.parser import HTMLParser
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "external"
UA = {"User-Agent": "Mozilla/5.0 (compatible; LumeResearch/0.1; uso educacional)"}
SPLIT = re.compile(r"(?<=[.!?…])[\"”')\]]*\s+(?=[\"“'(\[]?[A-ZÀ-ÝÇ0-9])")

WIKI = ["Supremo Tribunal Federal", "Eleições gerais no Brasil em 2022", "Luiz Inácio Lula da Silva", "Jair Bolsonaro",
        "Congresso Nacional do Brasil", "Pandemia de COVID-19 no Brasil", "Operação Lava Jato", "Tribunal Superior Eleitoral",
        "Partido dos Trabalhadores", "Câmara dos Deputados do Brasil", "Senado Federal do Brasil", "Sufrágio feminino no Brasil",
        "Constituição brasileira de 1988", "Ministério Público Federal (Brasil)", "Polícia Federal do Brasil",
        "Imposto sobre Circulação de Mercadorias e Serviços", "Banco Central do Brasil", "Eleições municipais no Brasil em 2020",
        "Reforma da Previdência no Brasil", "Impeachment de Dilma Rousseff"]
# (feed RSS/Atom público, gênero, quantos, domínio exigido). Feeds em vez de busca: a busca ignora filtros de site e contamina o conjunto.
FEEDS = [("https://theconversation.com/br/articles.atom", "revista_analise", 8, "theconversation.com"),
         ("https://www.cartacapital.com.br/feed/", "revista_analise", 6, "cartacapital.com.br"),
         ("https://piaui.uol.com.br/feed/", "revista_analise", 6, "piaui.uol.com.br"),
         ("https://agenciabrasil.ebc.com.br/rss/ultimasnoticias/feed.xml", "noticia_controle", 14, "agenciabrasil.ebc.com.br"),
         ("https://agenciabrasil.ebc.com.br/rss/politica/feed.xml", "noticia_controle", 8, "agenciabrasil.ebc.com.br")]
EXTRA_URLS = [("https://piaui.uol.com.br/web/a-cruzada-eleitoral-pelas-mulheres/", "revista_analise")]


class Paras(HTMLParser):
    SKIP = {"script", "style", "nav", "footer", "aside", "header", "form", "noscript", "figure", "button"}

    def __init__(self):
        super().__init__(); self.depth = 0; self.in_p = False; self.cur = []; self.paras = []

    def handle_starttag(self, tag, attrs):
        if tag in self.SKIP: self.depth += 1
        if tag == "p" and not self.depth: self.in_p = True; self.cur = []

    def handle_endtag(self, tag):
        if tag in self.SKIP and self.depth: self.depth -= 1
        if tag == "p" and self.in_p:
            self.in_p = False; t = re.sub(r"\s+", " ", "".join(self.cur)).strip()
            if len(t) >= 40: self.paras.append(t)

    def handle_data(self, data):
        if self.in_p: self.cur.append(data)


def sentences(text: str) -> list[str]:
    out, seen = [], set()
    for chunk in re.split(r"\n+", text):
        for raw in SPLIT.split(chunk):
            s = re.sub(r"\s+", " ", raw).strip()
            if 20 <= len(s) <= 700 and len(s.split()) >= 4 and sum(c.isalpha() for c in s) >= 0.6 * len(s) and s.lower() not in seen:
                seen.add(s.lower()); out.append(s)
    return out


def fetch_html(url: str) -> str | None:
    try:
        r = requests.get(url, headers=UA, timeout=20)
        return r.text if r.ok and "html" in r.headers.get("content-type", "") else None
    except requests.RequestException:
        return None


def feed_links(url: str, domain: str) -> list[str]:
    try:
        r = requests.get(url, headers=UA, timeout=20)
    except requests.RequestException:
        return []
    links = re.findall(r"<link>\s*(?:<!\[CDATA\[)?\s*(https?://[^<\s\]]+)", r.text) + re.findall(r"<link[^>]+href=\"(https?://[^\"]+)\"", r.text)
    seen, out = set(), []
    for u in links:
        if domain in u and u not in seen and u.rstrip("/").count("/") >= 3:
            seen.add(u); out.append(u)
    return out


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    manifest, seen_urls = [], set()

    def add(genre: str, url: str, title: str, text: str) -> bool:
        sents = sentences(text)
        if len(sents) < 15 or url in seen_urls:
            return False
        seen_urls.add(url)
        h = hashlib.sha256("\n".join(sents).encode("utf-8")).hexdigest()
        d = OUT / genre; d.mkdir(exist_ok=True)
        (d / f"{h[:16]}.txt").write_text("\n".join(sents), encoding="utf-8")
        manifest.append({"genre": genre, "url": url, "title": title[:120], "sentences": len(sents), "sha256": h})
        return True

    for t in WIKI:
        time.sleep(1.0)
        try:
            r = requests.get("https://pt.wikipedia.org/w/api.php", params={"action": "query", "format": "json", "prop": "extracts", "explaintext": 1,
                             "exsectionformat": "plain", "redirects": 1, "titles": t}, headers=UA, timeout=20).json()
            page = next(iter(r["query"]["pages"].values()))
            add("enciclopedia", "https://pt.wikipedia.org/wiki/" + urllib.parse.quote(t.replace(" ", "_")), t, page.get("extract", ""))
        except Exception as e:  # noqa: BLE001
            print("wiki falhou:", t, type(e).__name__)
        time.sleep(0.2)

    targets = [(u, g, 1) for u, g in EXTRA_URLS]
    for feed, g, n, dom in FEEDS:
        links = feed_links(feed, dom)
        print(f"feed {dom}: {len(links)} links")
        targets += [(u, g, 0) for u in links[:n + 6]]   # margem: alguns links não rendem 15 frases
    quota = {}
    for u, g, _ in targets:
        html = fetch_html(u)
        if not html:
            print("sem HTML:", u[:90]); continue
        p = Paras(); p.feed(html)
        title = (re.search(r"<title[^>]*>(.*?)</title>", html, re.S) or [None, u])[1]
        ok = add(g, u, re.sub(r"\s+", " ", title).strip(), "\n".join(p.paras))
        print(("ok   " if ok else "pula "), g, u[:90])
        time.sleep(0.3)

    (ROOT / "reports").mkdir(exist_ok=True)
    (ROOT / "reports" / "externos_manifesto.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    for g in ("enciclopedia", "revista_analise", "noticia_controle"):
        rows = [m for m in manifest if m["genre"] == g]
        print(f"{g}: {len(rows)} textos, {sum(m['sentences'] for m in rows)} frases")


if __name__ == "__main__":
    main()
