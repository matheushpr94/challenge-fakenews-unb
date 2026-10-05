"""Rede simulada: respostas fixas para Google Notícias, Bing, Wikipédia e páginas.

Os nomes (Atlético Serrano, Vale Claro, Pedra Azul...) são fictícios de propósito, para que os
testes exercitem categorias gerais e não conhecimento sobre casos reais."""
import json
import urllib.parse
from datetime import datetime, timezone
from email.utils import format_datetime
from xml.sax.saxutils import escape

from lume.netfetch import FetchError, Response

NOW = datetime(2026, 9, 28, 12, 0, tzinfo=timezone.utc)


def _date(d):
    return format_datetime(d) if d else ""


def google_rss(items):
    parts = []
    for it in items:
        parts.append(
            f"<item><title>{escape(it['title'])} - {escape(it['source'])}</title>"
            f"<link>https://news.google.com/rss/articles/{abs(hash(it['title']))}?oc=5</link>"
            f"<pubDate>{_date(it.get('date'))}</pubDate><description>x</description>"
            f"<source url=\"https://{it.get('domain', 'jornal.example.com')}\">{escape(it['source'])}</source></item>")
    return f"<?xml version=\"1.0\"?><rss version=\"2.0\"><channel>{''.join(parts)}</channel></rss>"


def bing_rss(items, news=True):
    ns = ' xmlns:News="https://www.bing.com/news/search?q=x"' if news else ""
    parts = []
    for it in items:
        link = it["url"]
        if news:
            link = "http://www.bing.com/news/apiclick.aspx?ref=FexRss&url=" + urllib.parse.quote(it["url"], safe="")
        src = f"<News:Source>{escape(it['source'])}</News:Source>" if news else ""
        parts.append(
            f"<item><title>{escape(it['title'])}</title><link>{escape(link)}</link>"
            f"<description>{escape(it.get('snippet', ''))}</description>"
            f"<pubDate>{_date(it.get('date'))}</pubDate>{src}</item>")
    return f"<?xml version=\"1.0\"?><rss version=\"2.0\"{ns}><channel>{''.join(parts)}</channel></rss>"


def wiki_json(pages):
    out = {}
    for i, p in enumerate(pages):
        out[str(100 + i)] = {"pageid": 100 + i, "title": p["title"], "index": i + 1, "extract": p["extract"],
                             "fullurl": "https://pt.wikipedia.org/wiki/" + p["title"].replace(" ", "_"),
                             **({"pageprops": {"disambiguation": ""}} if p.get("disambiguation") else {})}
    return json.dumps({"batchcomplete": "", "query": {"pages": out}} if out else {"batchcomplete": ""})


def html_page(paragraphs, published=None, site=None):
    filler = ("Este parágrafo descreve informações gerais da região, com dados de contexto que não "
              "tratam do número pesquisado, para que a página tenha tamanho semelhante a uma matéria real.")
    meta = ""
    if published:
        meta += f'<meta property="article:published_time" content="{published.isoformat()}">'
    if site:
        meta += f'<meta property="og:site_name" content="{escape(site)}">'
    body = "".join(f"<p>{escape(p)}</p>" for p in list(paragraphs) + [filler] * 3)
    return f"<html><head><title>t</title>{meta}</head><body><nav><p>Menu inicial do site com links</p></nav>" \
           f"<article>{body}</article><script>var x = 1;</script></body></html>"


class FakeWeb:
    """fetch(url, **kw) compatível com netfetch.fetch. Registra as URLs pedidas."""

    def __init__(self, google=None, bing_news=None, bing_web=None, wiki=None, pages=None, fail=()):
        self.google = google or []
        self.bing_news = bing_news or []
        self.bing_web = bing_web or []
        self.wiki = wiki or []
        self.pages = pages or {}
        self.fail = set(fail)  # {"google", "bing_news", "bing_web", "wiki", "pages"} ou conteúdo inválido
        self.calls = []

    def _resp(self, url, body, ctype):
        return Response(url=url, status=200, content_type=ctype, body=body.encode("utf-8"))

    def __call__(self, url, **kw):
        self.calls.append(url)
        host = urllib.parse.urlsplit(url).hostname or ""
        if host == "news.google.com":
            if "google" in self.fail:
                raise FetchError("tempo_esgotado", "tempo esgotado")
            if "google_xml" in self.fail:
                return self._resp(url, "<rss><channel><item>quebrado", "application/xml")
            return self._resp(url, google_rss(self.google), "application/xml")
        if host == "www.bing.com" and "/news/" in url:
            if "bing_news" in self.fail:
                raise FetchError("http", "HTTP 503")
            return self._resp(url, bing_rss(self.bing_news, news=True), "application/xml")
        if host == "www.bing.com":
            if "bing_web" in self.fail:
                raise FetchError("http", "HTTP 503")
            return self._resp(url, bing_rss(self.bing_web, news=False), "text/xml")
        if host == "pt.wikipedia.org" and "/w/api.php" in url:
            if "wiki" in self.fail:
                raise FetchError("rede", "falha de rede")
            if "wiki_json" in self.fail:
                return self._resp(url, "{nao e json", "application/json")
            return self._resp(url, wiki_json(self.wiki), "application/json")
        if url in self.pages and "pages" not in self.fail:
            return self._resp(url, self.pages[url], "text/html; charset=utf-8")
        raise FetchError("http", "HTTP 404")

    def count(self, fragment):
        return sum(1 for c in self.calls if fragment in c)
