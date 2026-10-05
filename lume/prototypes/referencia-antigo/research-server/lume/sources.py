"""Conectores de busca sem chave de API e extração de páginas.

Nenhuma dessas fontes é uma API oficial estável para este uso (ver README). Cada falha
vira SourceError, que o serviço registra separadamente de "sem resultados"."""
import html
import json
import os
import re
import urllib.parse
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from html.parser import HTMLParser

from . import netfetch

GOOGLE_NEWS = "https://news.google.com/rss/search?"
BING_NEWS = "https://www.bing.com/news/search?"
BING_WEB = "https://www.bing.com/search?"
WIKIPEDIA = "https://pt.wikipedia.org/w/api.php?"
# A política da Wikimedia pede um User-Agent descritivo com forma de contato (LUME_CONTACT).
WIKI_UA = "LumePrototipo/0.1 (prototipo educacional de pesquisa de fontes; {}) python-urllib".format(
    os.environ.get("LUME_CONTACT", "contato nao configurado"))


class SourceError(Exception):
    def __init__(self, kind, message):
        super().__init__(message)
        self.kind = kind


@dataclass
class Candidate:
    title: str
    url: str
    source_name: str
    origin: str
    query: str = ""
    published: datetime = None
    snippet: str = ""
    content_kind: str = "titulo"  # completo | trecho | titulo
    domain: str = ""
    link_note: str = ""
    page_text: str = ""
    page_excerpts: list = field(default_factory=list)
    page_author: str = ""
    alerts: list = field(default_factory=list)
    disambiguation: bool = False


def _get(fetch, url, **kw):
    try:
        return fetch(url, **kw)
    except netfetch.FetchError as e:
        raise SourceError(e.kind, str(e))


def domain_of(url):
    try:
        host = urllib.parse.urlsplit(url).hostname or ""
    except ValueError:
        return ""
    return host[4:] if host.startswith("www.") else host


def safe_link(url):
    """Aceita apenas http(s) com host; não resolve DNS (o link é só exibido)."""
    try:
        netfetch.check_url(url, resolve=False)
        return True
    except netfetch.FetchError:
        return False


def strip_html(s):
    s = re.sub(r"<[^>]+>", " ", s or "")
    return " ".join(html.unescape(s).split())


def _parse_date(s):
    if not s:
        return None
    try:
        d = parsedate_to_datetime(s.strip())
    except (TypeError, ValueError, IndexError):
        try:
            d = datetime.fromisoformat(s.strip().replace("Z", "+00:00"))
        except ValueError:
            return None
    return d if d.tzinfo else d.replace(tzinfo=timezone.utc)


def parse_rss(text):
    if "<!ENTITY" in text[:5000]:
        raise SourceError("conteudo_invalido", "RSS com entidades declaradas foi recusado")
    try:
        root = ET.fromstring(text.encode("utf-8") if isinstance(text, str) else text)
    except ET.ParseError as e:
        raise SourceError("conteudo_invalido", f"XML inválido: {e}")
    if root.tag != "rss" and root.find(".//channel") is None:
        raise SourceError("conteudo_invalido", "resposta não é um RSS")
    items = []
    for item in root.iter("item"):
        d = {"title": "", "link": "", "description": "", "pubDate": "", "source": "", "source_url": ""}
        for child in item:
            tag = child.tag.split("}")[-1].split(":")[-1].lower()
            if tag in ("title", "link", "description"):
                d[tag] = (child.text or "").strip()
            elif tag == "pubdate":
                d["pubDate"] = (child.text or "").strip()
            elif tag == "source":
                d["source"] = (child.text or "").strip()
                d["source_url"] = child.get("url", "")
        items.append(d)
    return items


def google_news(query, fetch, limit=15):
    params = {"q": query, "hl": "pt-BR", "gl": "BR", "ceid": "BR:pt-419"}
    resp = _get(fetch, GOOGLE_NEWS + urllib.parse.urlencode(params), accept="application/rss+xml,application/xml")
    out = []
    for it in parse_rss(resp.text())[:limit]:
        title, source = strip_html(it["title"]), it["source"]
        if source and title.endswith(" - " + source):
            title = title[: -len(source) - 3].strip()
        if not title or not safe_link(it["link"]):
            continue
        out.append(Candidate(
            title=title, url=it["link"], source_name=source or "(veículo não informado)",
            origin="google_news", query=query, published=_parse_date(it["pubDate"]),
            content_kind="titulo", domain=domain_of(it["source_url"]),
            link_note="link via Google Notícias (redireciona para o veículo)"))
    return out


def _bing_target(link):
    try:
        parts = urllib.parse.urlsplit(link)
    except ValueError:
        return ""
    if (parts.hostname or "").endswith("bing.com"):
        target = urllib.parse.parse_qs(parts.query).get("url", [""])[0]
        return target
    return link


def bing(query, fetch, kind="news", limit=12):
    base = BING_NEWS if kind == "news" else BING_WEB
    params = {"q": query, "format": "rss", "setlang": "pt-BR", "cc": "BR"}
    resp = _get(fetch, base + urllib.parse.urlencode(params), accept="application/rss+xml,application/xml")
    out = []
    for it in parse_rss(resp.text())[:limit]:
        url = _bing_target(it["link"])
        title = strip_html(it["title"])
        if not title or not safe_link(url):
            continue
        snippet = strip_html(it["description"])
        dom = domain_of(url)
        out.append(Candidate(
            title=title, url=url, source_name=it["source"] or dom, origin=f"bing_{kind}", query=query,
            # A data do RSS de busca web do Bing é do rastreamento, não da publicação.
            published=_parse_date(it["pubDate"]) if kind == "news" else None,
            snippet=snippet, content_kind="trecho" if snippet else "titulo", domain=dom))
    return out


def wikipedia(query, fetch, limit=3):
    params = {"action": "query", "format": "json", "generator": "search", "gsrsearch": query,
              "gsrlimit": limit, "prop": "extracts|pageprops|info", "exintro": 1, "explaintext": 1,
              "exsentences": 3, "exlimit": limit, "inprop": "url", "ppprop": "disambiguation", "utf8": 1}
    resp = _get(fetch, WIKIPEDIA + urllib.parse.urlencode(params), accept="application/json", user_agent=WIKI_UA)
    try:
        data = json.loads(resp.text())
    except (json.JSONDecodeError, ValueError) as e:
        raise SourceError("conteudo_invalido", f"JSON inválido: {e}")
    if not isinstance(data, dict):
        raise SourceError("conteudo_invalido", "JSON inesperado")
    if "error" in data:
        raise SourceError("api", str(data["error"].get("info", "erro da API")))
    pages = (data.get("query") or {}).get("pages") or {}
    out = []
    for page in sorted(pages.values(), key=lambda p: p.get("index", 99)):
        url, title = page.get("fullurl", ""), page.get("title", "")
        if not title or not safe_link(url):
            continue
        out.append(Candidate(
            title=title, url=url, source_name="Wikipédia", origin="wikipedia", query=query,
            snippet=" ".join((page.get("extract") or "").split())[:900], content_kind="resumo",
            domain="pt.wikipedia.org",
            disambiguation="disambiguation" in (page.get("pageprops") or {})))
    return out


# --------- páginas -----------------------------------------------------------------------

INJECTION_RE = re.compile(
    r"(ignore|ignora|desconsidere|disregard|forget)\W+(?:\w+\W+){0,5}(instru|instruct|prompt|regras|rules)|"
    r"system\s+prompt|you\s+are\s+(?:an?\s+)?(?:ai|assistant|language model|chatgpt)|"
    r"voc[eê]\s+(?:é|e)\s+(?:um|uma)\s+(?:ia|assistente|modelo)|as\s+an\s+ai|"
    r"</?\s*(?:system|assistant|user)\s*>|responda\s+(?:apenas|somente)\s+(?:com|que)", re.I)
CITATION_RE = re.compile(r"\[(?:nota\s+|note\s+|carece[^\]]*|\w\s*)?\d*\]")  # marcadores [2], [nota 1]
SKIP_TAGS = {"script", "style", "noscript", "nav", "footer", "header", "aside", "form", "svg",
             "button", "iframe", "template", "figure", "figcaption"}


class _PageParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.meta, self.paragraphs, self.jsonld, self.times = {}, [], [], []
        self.title, self._skip, self._in_p, self._in_title, self._buf = "", 0, 0, False, []
        self._in_jsonld = False

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "meta":
            key = (a.get("property") or a.get("name") or a.get("itemprop") or "").lower()
            if key and a.get("content"):
                self.meta.setdefault(key, a["content"])
        elif tag == "script" and (a.get("type") or "").lower() == "application/ld+json":
            self._in_jsonld, self._buf = True, []
        elif tag == "time" and a.get("datetime"):
            self.times.append(a["datetime"])
        if tag in SKIP_TAGS and not self._in_jsonld:
            self._skip += 1
        elif tag == "p":
            self._in_p, self._buf = self._in_p + 1, []
        elif tag == "title":
            self._in_title = True

    def handle_endtag(self, tag):
        if tag == "script" and self._in_jsonld:
            self._in_jsonld = False
            self.jsonld.append("".join(self._buf))
            self._buf = []
            return
        if tag in SKIP_TAGS and self._skip:
            self._skip -= 1
        elif tag == "p" and self._in_p:
            self._in_p -= 1
            text = " ".join(CITATION_RE.sub("", "".join(self._buf)).split())
            if len(text) >= 40:
                self.paragraphs.append(text)
            self._buf = []
        elif tag == "title":
            self._in_title = False

    def handle_data(self, data):
        if self._in_jsonld:
            self._buf.append(data)
        elif self._in_title:
            self.title += data
        elif self._in_p and not self._skip:
            self._buf.append(data)


def _jsonld_field(blobs, names):
    for blob in blobs:
        try:
            data = json.loads(blob)
        except (json.JSONDecodeError, ValueError):
            continue
        stack = [data]
        while stack:
            cur = stack.pop()
            if isinstance(cur, list):
                stack.extend(cur)
            elif isinstance(cur, dict):
                for n in names:
                    v = cur.get(n)
                    if isinstance(v, str) and v.strip():
                        return v.strip()
                    if isinstance(v, dict) and isinstance(v.get("name"), str):
                        return v["name"].strip()
                    if isinstance(v, list) and v and isinstance(v[0], dict) and isinstance(v[0].get("name"), str):
                        return v[0]["name"].strip()
                stack.extend(x for x in cur.values() if isinstance(x, (dict, list)))
    return ""


def extract_page(text):
    """Extrai metadados e parágrafos. Texto de página é dado, nunca instrução."""
    parser = _PageParser()
    try:
        parser.feed(text[:1_500_000])
        parser.close()
    except Exception as e:  # HTMLParser pode falhar em HTML muito quebrado
        raise SourceError("conteudo_invalido", f"HTML inválido: {e}")
    m = parser.meta
    published = (m.get("article:published_time") or m.get("og:published_time") or m.get("datepublished")
                 or _jsonld_field(parser.jsonld, ["datePublished"]) or (parser.times[0] if parser.times else ""))
    kept, dropped = [], 0
    for p in parser.paragraphs:
        if INJECTION_RE.search(p):
            dropped += 1
            continue
        kept.append(p)
    return {
        "title": " ".join((m.get("og:title") or parser.title or "").split()),
        "site_name": m.get("og:site_name", ""),
        "description": m.get("og:description") or m.get("description", ""),
        "published": _parse_date(published),
        "author": m.get("author") or _jsonld_field(parser.jsonld, ["author"]),
        "paragraphs": kept,
        "injection_dropped": dropped,
    }


def fetch_page(url, fetch):
    resp = _get(fetch, url, timeout=6.0, max_bytes=1_200_000, accept="text/html,application/xhtml+xml")
    ctype = (resp.content_type or "").lower()
    if ctype and "html" not in ctype:
        raise SourceError("conteudo_invalido", f"página não é HTML ({ctype.split(';')[0]})")
    return extract_page(resp.text())
