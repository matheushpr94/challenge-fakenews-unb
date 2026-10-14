#!/usr/bin/env python3
"""Coleta textos de notícias reais e atuais (como o app: Bing Notícias RSS + página) para CALIBRAR as faixas do sinal do texto.
Respeita robots.txt, no máximo 1 requisição por segundo por domínio, User-Agent identificado, sem custo/chave.
Saída: JSONL fora do repositório (texto de terceiros: uso só local). Uso: python coletar_noticias.py saida.jsonl"""
import json, re, sys, time, threading, urllib.request, urllib.parse, urllib.robotparser
from concurrent.futures import ThreadPoolExecutor
from xml.etree import ElementTree as ET
from bs4 import BeautifulSoup
UA = "Mozilla/5.0 (compatible; LumeApp/0.1; uso educacional)"
QUERIES = ["Selic Copom decisão", "Lula sanciona lei", "STF julgamento decisão", "vacina SUS saúde", "Seleção brasileira convocação",
    "chuvas enchente defesa civil", "inflação IPCA IBGE", "TSE eleições decisão", "Petrobras lucro trimestre", "reforma tributária Câmara",
    "Enem inscrições MEC", "incêndio queimadas bombeiros", "dólar fecha bolsa", "greve servidores federais", "Copa do Brasil final",
    "polícia operação prisão", "Prêmio Nobel 2026", "Congresso aprova projeto Senado", "hospital leitos governo estadual", "economia PIB trimestre"]
_ult, _lock, _robots = {}, threading.Lock(), {}

def esperar(dominio):
    with _lock:
        agora = time.time(); t = max(0.0, _ult.get(dominio, 0) + 1.05 - agora); _ult[dominio] = agora + t
    if t: time.sleep(t)

def permitido(url):
    p = urllib.parse.urlparse(url); base = f"{p.scheme}://{p.netloc}"
    if base not in _robots:
        rp = urllib.robotparser.RobotFileParser(); rp.set_url(base + "/robots.txt")
        try: esperar(p.netloc); rp.read()
        except Exception: rp = None
        _robots[base] = rp
    rp = _robots[base]
    return True if rp is None else rp.can_fetch(UA, url)

def baixar(url, limite=1_500_000):
    esperar(urllib.parse.urlparse(url).netloc)
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "pt-BR,pt;q=0.9"})
    with urllib.request.urlopen(req, timeout=10) as r: return r.geturl(), r.read(limite)

def texto_da_pagina(html):
    s = BeautifulSoup(html, "html.parser")
    for t in s(["script", "style", "nav", "header", "footer", "aside", "form", "figure"]): t.decompose()
    ps = [re.sub(r"\s+", " ", p.get_text(" ")).strip() for p in s.find_all("p")]
    ps = [p for p in ps if len(p.split()) >= 8]
    return "\n".join(ps)

def links(q):
    url = "https://www.bing.com/news/search?q=" + urllib.parse.quote(q) + "&format=rss&setlang=pt-BR&cc=BR"
    _, corpo = baixar(url)
    out = []
    for it in ET.fromstring(corpo).iter("item"):
        l = it.findtext("link") or ""
        m = re.search(r"[?&]url=([^&]+)", l); out.append(urllib.parse.unquote(m.group(1)) if m else l)
    return out

def tarefa(url):
    try:
        if not permitido(url): return None
        final, html = baixar(url)
        t = texto_da_pagina(html.decode("utf-8", "ignore"))
        return {"url": final, "dominio": urllib.parse.urlparse(final).netloc, "palavras": len(t.split()), "texto": t} if len(t.split()) >= 60 else None
    except Exception: return None

if __name__ == "__main__":
    todos = []
    for q in QUERIES:
        try: todos += links(q)[:7]
        except Exception as e: print("falha na busca", q, e, file=sys.stderr)
    todos = list(dict.fromkeys(todos)); print("links:", len(todos), file=sys.stderr)
    with ThreadPoolExecutor(8) as ex: res = [r for r in ex.map(tarefa, todos) if r]
    vistos, saida = set(), []
    for r in res:
        if r["url"] not in vistos: vistos.add(r["url"]); saida.append(r)
    with open(sys.argv[1], "w", encoding="utf-8") as f:
        for r in saida: f.write(json.dumps(r, ensure_ascii=False) + "\n")
    import statistics
    print(f"textos: {len(saida)} | domínios: {len({r['dominio'] for r in saida})} | palavras (mediana): {statistics.median(r['palavras'] for r in saida)}")
