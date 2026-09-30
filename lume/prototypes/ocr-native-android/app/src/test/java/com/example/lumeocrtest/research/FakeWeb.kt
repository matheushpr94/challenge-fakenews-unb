package com.example.lumeocrtest.research

import kotlinx.coroutines.delay
import java.net.URI

/*
 * Rede simulada: respostas fixas para Google Notícias, Bing, Wikipédia e páginas.
 * Os nomes (Atlético Serrano, Vale Claro, Pedra Azul...) são fictícios de propósito, para que os
 * testes exercitem categorias gerais e não conhecimento sobre casos reais.
 */

val NOW: Long = Dates.parseIso("2026-09-28T12:00:00Z")!!
fun d(y: Int, m: Int, day: Int): Long = Dates.parseIso("%04d-%02d-%02dT10:00:00Z".format(y, m, day))!!

data class News(val title: String, val source: String = "Jornal", val url: String = "", val date: Long? = null,
                val snippet: String = "", val domain: String = "jornal.example.com")
data class WebItem(val title: String, val url: String, val snippet: String = "")
data class WikiPage(val title: String, val extract: String, val disambiguation: Boolean = false)

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
private fun rfc(ms: Long?) = ms?.let { java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(it) } ?: ""

fun googleRss(items: List<News>) = "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel>" + items.joinToString("") {
    "<item><title>${esc(it.title)} - ${esc(it.source)}</title>" +
        "<link>https://news.google.com/rss/articles/${Math.abs((it.title + it.source).hashCode())}?oc=5</link>" +
        "<pubDate>${rfc(it.date)}</pubDate><description>x</description>" +
        "<source url=\"https://${it.domain}\">${esc(it.source)}</source></item>"
} + "</channel></rss>"

fun bingNewsRss(items: List<News>) = "<?xml version=\"1.0\"?><rss version=\"2.0\" xmlns:News=\"https://www.bing.com/news/search?q=x\"><channel>" +
    items.joinToString("") {
        val link = "http://www.bing.com/news/apiclick.aspx?ref=FexRss&url=" + java.net.URLEncoder.encode(it.url, "UTF-8")
        "<item><title>${esc(it.title)}</title><link>${esc(link)}</link><description>${esc(it.snippet)}</description>" +
            "<pubDate>${rfc(it.date)}</pubDate><News:Source>${esc(it.source)}</News:Source></item>"
    } + "</channel></rss>"

fun bingWebRss(items: List<WebItem>) = "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel>" + items.joinToString("") {
    "<item><title>${esc(it.title)}</title><link>${esc(it.url)}</link><description>${esc(it.snippet)}</description></item>"
} + "</channel></rss>"

fun wikiJson(pages: List<WikiPage>): String {
    if (pages.isEmpty()) return "{\"batchcomplete\":\"\"}"
    val body = pages.withIndex().joinToString(",") { (i, p) ->
        val props = if (p.disambiguation) ",\"pageprops\":{\"disambiguation\":\"\"}" else ""
        "\"${100 + i}\":{\"pageid\":${100 + i},\"title\":${com.google.gson.Gson().toJson(p.title)},\"index\":${i + 1}," +
            "\"extract\":${com.google.gson.Gson().toJson(p.extract)},\"fullurl\":\"https://pt.wikipedia.org/wiki/${p.title.replace(" ", "_")}\"$props}"
    }
    return "{\"batchcomplete\":\"\",\"query\":{\"pages\":{$body}}}"
}

fun htmlPage(paragraphs: List<String>, published: Long? = null, site: String? = null): String {
    val filler = "Este parágrafo descreve informações gerais da região, com dados de contexto que não tratam do " +
        "número pesquisado, para que a página tenha tamanho semelhante a uma matéria real."
    var meta = ""
    if (published != null) meta += "<meta property=\"article:published_time\" content=\"${Dates.iso(published)}\">"
    if (site != null) meta += "<meta property=\"og:site_name\" content=\"${esc(site)}\">"
    val body = (paragraphs + List(3) { filler }).joinToString("") { "<p>${esc(it)}</p>" }
    return "<html><head><title>t</title>$meta</head><body><nav><p>Menu inicial do site com links</p></nav>" +
        "<article>$body</article><script>var x = 1;</script></body></html>"
}

/** Fetcher simulado. `fail`: google, bing_news, bing_web, wiki, google_xml, wiki_json. Registra as URLs pedidas. */
class FakeWeb(
    var google: List<News> = emptyList(),
    var bingNews: List<News> = emptyList(),
    var bingWeb: List<WebItem> = emptyList(),
    var wiki: List<WikiPage> = emptyList(),
    var pages: Map<String, String> = emptyMap(),
    var fail: Set<String> = emptySet(),
    var delayMs: Long = 0,
) : Fetcher {
    val calls = mutableListOf<String>()

    private fun ok(url: String, body: String, ctype: String) = FetchResponse(url, 200, ctype, body.toByteArray(Charsets.UTF_8))

    override suspend fun fetch(url: String, options: FetchOptions): FetchResponse {
        synchronized(calls) { calls.add(url) }
        if (delayMs > 0) delay(delayMs)
        val host = URI(url).host ?: ""
        return when {
            host == "news.google.com" -> when {
                "google" in fail -> throw FetchException("tempo_esgotado", "tempo esgotado")
                "google_xml" in fail -> ok(url, "<rss><channel><item>quebrado", "application/xml")
                else -> ok(url, googleRss(google), "application/xml")
            }
            host == "www.bing.com" && "/news/" in url ->
                if ("bing_news" in fail) throw FetchException("http", "HTTP 503") else ok(url, bingNewsRss(bingNews), "application/xml")
            host == "www.bing.com" ->
                if ("bing_web" in fail) throw FetchException("http", "HTTP 503") else ok(url, bingWebRss(bingWeb), "text/xml")
            host == "pt.wikipedia.org" && "/w/api.php" in url -> when {
                "wiki" in fail -> throw FetchException("rede", "falha de rede")
                "wiki_json" in fail -> ok(url, "{nao e json", "application/json")
                else -> ok(url, wikiJson(wiki), "application/json")
            }
            url in pages && "pages" !in fail -> ok(url, pages.getValue(url), "text/html; charset=utf-8")
            else -> throw FetchException("http", "HTTP 404")
        }
    }

    fun count(fragment: String) = synchronized(calls) { calls.count { fragment in it } }
}
