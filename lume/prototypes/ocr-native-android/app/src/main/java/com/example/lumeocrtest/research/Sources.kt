package com.example.lumeocrtest.research

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

/*
 * Conectores de busca sem chave de API e leitura de páginas.
 * Nenhuma dessas fontes é uma API oficial estável para este uso (ver RESEARCH-NATIVE.md).
 * Cada falha vira SourceException, registrada separadamente de "sem resultados".
 */

const val GOOGLE_NEWS = "https://news.google.com/rss/search?"
const val BING_NEWS = "https://www.bing.com/news/search?"
const val BING_WEB = "https://www.bing.com/search?"
const val WIKIPEDIA = "https://pt.wikipedia.org/w/api.php?"

/** A política da Wikimedia pede User-Agent descritivo com forma de contato. */
var WIKI_USER_AGENT = "LumeApp/0.1 (prototipo educacional de pesquisa de fontes; contato nao configurado) okhttp"

class SourceException(val kind: String, message: String) : Exception(message)

/** Resultado de uma fonte. `contentKind`: completo (página lida), resumo (enciclopédia), trecho (buscador), titulo. */
class Candidate(
    var title: String,
    val url: String,
    var sourceName: String,
    val origin: String,
    val query: String = "",
    var published: Long? = null,
    var snippet: String = "",
    var contentKind: String = "titulo",
    val domain: String = "",
    val linkNote: String = "",
    var pageText: String = "",
    var pageExcerpts: List<String> = emptyList(),
    var pageAuthor: String = "",
    val alerts: MutableList<String> = mutableListOf(),
    val disambiguation: Boolean = false,
) {
    fun copy(): Candidate = Candidate(title, url, sourceName, origin, query, published, snippet, contentKind, domain,
        linkNote, pageText, pageExcerpts, pageAuthor, alerts.toMutableList(), disambiguation)
}

private suspend fun get(fetcher: Fetcher, url: String, options: FetchOptions): FetchResponse = try {
    fetcher.fetch(url, options)
} catch (e: FetchException) {
    throw SourceException(e.kind, e.message ?: e.kind)
}

fun domainOf(url: String): String {
    val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return ""
    return host.removePrefix("www.")
}

/** Aceita apenas http(s) com host; não resolve DNS (o link é só exibido). */
fun safeLink(url: String): Boolean = runCatching { UrlGuard.check(url, resolve = false) }.isSuccess

fun stripHtml(s: String?): String = Jsoup.parse(s ?: "").text().replace(Regex("\\s+"), " ").trim()

private fun params(vararg p: Pair<String, Any>): String =
    p.joinToString("&") { (k, v) -> URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v.toString(), "UTF-8") }

data class RssItem(val title: String, val link: String, val description: String, val pubDate: String,
                   val source: String, val sourceUrl: String)

/** XML estrito: conteúdo quebrado vira erro "conteudo_invalido", nunca "sem resultados". */
fun parseRss(text: String): List<RssItem> {
    val head = text.take(5000)
    if ("<!ENTITY" in head || "<!DOCTYPE" in head) throw SourceException("conteudo_invalido", "RSS com DOCTYPE/entidades foi recusado")
    val doc = try {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = false
        f.isExpandEntityReferences = false
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        f.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
    } catch (e: Exception) {
        throw SourceException("conteudo_invalido", "XML inválido: ${e.message}")
    }
    val root = doc.documentElement
    if (root.tagName.lowercase() != "rss" && doc.getElementsByTagName("channel").length == 0) {
        throw SourceException("conteudo_invalido", "resposta não é um RSS")
    }
    val items = doc.getElementsByTagName("item")
    val out = mutableListOf<RssItem>()
    for (i in 0 until items.length) {
        val item = items.item(i) as Element
        var title = ""; var link = ""; var desc = ""; var date = ""; var source = ""; var sourceUrl = ""
        val children = item.childNodes
        for (c in 0 until children.length) {
            val child = children.item(c) as? Element ?: continue
            when (child.tagName.substringAfterLast(':').lowercase()) {
                "title" -> title = child.textContent.trim()
                "link" -> link = child.textContent.trim()
                "description" -> desc = child.textContent.trim()
                "pubdate" -> date = child.textContent.trim()
                "source" -> { source = child.textContent.trim(); sourceUrl = child.getAttribute("url") }
            }
        }
        out.add(RssItem(title, link, desc, date, source, sourceUrl))
    }
    return out
}

suspend fun googleNews(query: String, fetcher: Fetcher, limit: Int = 15): List<Candidate> {
    val url = GOOGLE_NEWS + params("q" to query, "hl" to "pt-BR", "gl" to "BR", "ceid" to "BR:pt-419")
    val resp = get(fetcher, url, FetchOptions(accept = "application/rss+xml,application/xml"))
    return parseRss(resp.text()).take(limit).mapNotNull { it ->
        var title = stripHtml(it.title)
        if (it.source.isNotEmpty() && title.endsWith(" - " + it.source)) title = title.dropLast(it.source.length + 3).trim()
        if (title.isEmpty() || !safeLink(it.link)) return@mapNotNull null
        Candidate(title = title, url = it.link, sourceName = it.source.ifEmpty { "(veículo não informado)" },
            origin = "google_news", query = query, published = Dates.parse(it.pubDate), contentKind = "titulo",
            domain = domainOf(it.sourceUrl), linkNote = "link via Google Notícias (redireciona para o veículo)")
    }
}

private fun bingTarget(link: String): String {
    val uri = runCatching { URI(link) }.getOrNull() ?: return ""
    if (uri.host?.endsWith("bing.com") == true) {
        val q = uri.rawQuery ?: return ""
        return q.split("&").firstOrNull { it.startsWith("url=") }?.let { URLDecoder.decode(it.removePrefix("url="), "UTF-8") } ?: ""
    }
    return link
}

suspend fun bing(query: String, fetcher: Fetcher, kind: String = "news", limit: Int = 12): List<Candidate> {
    val base = if (kind == "news") BING_NEWS else BING_WEB
    val url = base + params("q" to query, "format" to "rss", "setlang" to "pt-BR", "cc" to "BR")
    val resp = get(fetcher, url, FetchOptions(accept = "application/rss+xml,application/xml"))
    return parseRss(resp.text()).take(limit).mapNotNull { it ->
        val target = bingTarget(it.link)
        val title = stripHtml(it.title)
        if (title.isEmpty() || !safeLink(target)) return@mapNotNull null
        val snippet = stripHtml(it.description)
        val dom = domainOf(target)
        Candidate(title = title, url = target, sourceName = it.source.ifEmpty { dom }, origin = "bing_$kind",
            query = query,
            // A data do RSS de busca web do Bing é do rastreamento, não da publicação.
            published = if (kind == "news") Dates.parse(it.pubDate) else null,
            snippet = snippet, contentKind = if (snippet.isNotEmpty()) "trecho" else "titulo", domain = dom)
    }
}

suspend fun wikipedia(query: String, fetcher: Fetcher, limit: Int = 3): List<Candidate> {
    val url = WIKIPEDIA + params("action" to "query", "format" to "json", "generator" to "search", "gsrsearch" to query,
        "gsrlimit" to limit, "prop" to "extracts|pageprops|info", "exintro" to 1, "explaintext" to 1,
        "exsentences" to 3, "exlimit" to limit, "inprop" to "url", "ppprop" to "disambiguation", "utf8" to 1)
    val resp = get(fetcher, url, FetchOptions(accept = "application/json", userAgent = WIKI_USER_AGENT))
    val data = try { JsonParser.parseString(resp.text()) } catch (e: Exception) {
        throw SourceException("conteudo_invalido", "JSON inválido: ${e.message}")
    }
    if (!data.isJsonObject) throw SourceException("conteudo_invalido", "JSON inesperado")
    val obj = data.asJsonObject
    obj.getAsJsonObject("error")?.let { throw SourceException("api", it.get("info")?.asString ?: "erro da API") }
    val pages = obj.getAsJsonObject("query")?.getAsJsonObject("pages") ?: return emptyList()
    return pages.entrySet().map { it.value.asJsonObject }
        .sortedBy { it.get("index")?.asInt ?: 99 }
        .mapNotNull { page ->
            val title = page.get("title")?.asString.orEmpty()
            val link = page.get("fullurl")?.asString.orEmpty()
            if (title.isEmpty() || !safeLink(link)) return@mapNotNull null
            Candidate(title = title, url = link, sourceName = "Wikipédia", origin = "wikipedia", query = query,
                snippet = (page.get("extract")?.asString ?: "").replace(Regex("\\s+"), " ").trim().take(900),
                contentKind = "resumo", domain = "pt.wikipedia.org",
                disambiguation = page.getAsJsonObject("pageprops")?.has("disambiguation") == true)
        }
}

// --------- páginas ------------------------------------------------------------------------------

private val NW = "[^\\p{L}\\p{N}_]"
val INJECTION_RE = Regex(
    "(ignore|ignora|desconsidere|disregard|forget)$NW+(?:$W+$NW+){0,5}(instru|instruct|prompt|regras|rules)|" +
        "system\\s+prompt|you\\s+are\\s+(?:an?\\s+)?(?:ai|assistant|language model|chatgpt)|" +
        "voc[eê]\\s+(?:é|e)\\s+(?:um|uma)\\s+(?:ia|assistente|modelo)|as\\s+an\\s+ai|" +
        "</?\\s*(?:system|assistant|user)\\s*>|responda\\s+(?:apenas|somente)\\s+(?:com|que)", RegexOption.IGNORE_CASE)
private val CITATION_RE = Regex("\\[(?:nota\\s+|note\\s+|carece[^\\]]*|$W\\s*)?\\d*\\]") // marcadores [2], [nota 1]
private val SKIP_TAGS = setOf("script", "style", "noscript", "nav", "footer", "header", "aside", "form", "svg",
    "button", "iframe", "template", "figure", "figcaption")

data class PageInfo(
    val title: String,
    val siteName: String,
    val description: String,
    val published: Long?,
    val author: String,
    val paragraphs: List<String>,
    val injectionDropped: Int,
)

private fun jsonldField(blobs: List<String>, names: List<String>): String {
    for (blob in blobs) {
        val data = runCatching { JsonParser.parseString(blob) }.getOrNull() ?: continue
        val stack = ArrayDeque<JsonElement>().apply { add(data) }
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (cur is JsonArray) cur.forEach { stack.add(it) }
            if (cur is JsonObject) {
                for (n in names) {
                    val v = cur.get(n) ?: continue
                    if (v.isJsonPrimitive && v.asString.isNotBlank()) return v.asString.trim()
                    if (v.isJsonObject && v.asJsonObject.get("name")?.isJsonPrimitive == true) return v.asJsonObject.get("name").asString.trim()
                    if (v.isJsonArray && v.asJsonArray.size() > 0 && v.asJsonArray[0].isJsonObject) {
                        v.asJsonArray[0].asJsonObject.get("name")?.takeIf { it.isJsonPrimitive }?.let { return it.asString.trim() }
                    }
                }
                cur.entrySet().forEach { (_, v) -> if (v.isJsonObject || v.isJsonArray) stack.add(v) }
            }
        }
    }
    return ""
}

/** Extrai metadados e parágrafos. Texto de página é dado, nunca instrução. */
fun extractPage(html: String): PageInfo {
    val doc = try { Jsoup.parse(html.take(1_500_000)) } catch (e: Exception) {
        throw SourceException("conteudo_invalido", "HTML inválido: ${e.message}")
    }
    val meta = HashMap<String, String>()
    for (m in doc.select("meta")) {
        val key = (m.attr("property").ifEmpty { m.attr("name") }.ifEmpty { m.attr("itemprop") }).lowercase()
        val content = m.attr("content")
        if (key.isNotEmpty() && content.isNotEmpty()) meta.putIfAbsent(key, content)
    }
    val jsonld = doc.select("script[type=application/ld+json]").map { it.data() }
    val times = doc.select("time[datetime]").map { it.attr("datetime") }
    val published = listOf(meta["article:published_time"], meta["og:published_time"], meta["datepublished"],
        jsonldField(jsonld, listOf("datePublished")), times.firstOrNull())
        .firstOrNull { !it.isNullOrBlank() }
    var dropped = 0
    val paragraphs = mutableListOf<String>()
    for (p in doc.select("p")) {
        if (p.parents().any { it.tagName().lowercase() in SKIP_TAGS }) continue
        val text = CITATION_RE.replace(p.text(), "").replace(Regex("\\s+"), " ").trim()
            .replace(Regex("\\s+([.,;:!?])"), "$1")
        if (text.length < 40) continue
        if (INJECTION_RE.containsMatchIn(text)) { dropped++; continue }
        paragraphs.add(text)
    }
    return PageInfo(
        title = (meta["og:title"] ?: doc.title()).replace(Regex("\\s+"), " ").trim(),
        siteName = meta["og:site_name"].orEmpty(),
        description = meta["og:description"] ?: meta["description"].orEmpty(),
        published = Dates.parse(published),
        author = meta["author"] ?: jsonldField(jsonld, listOf("author")),
        paragraphs = paragraphs,
        injectionDropped = dropped,
    )
}

suspend fun fetchPage(url: String, fetcher: Fetcher): PageInfo {
    val resp = get(fetcher, url, FetchOptions(timeoutMs = 6_000, maxBytes = 1_200_000, accept = "text/html,application/xhtml+xml"))
    val ctype = resp.contentType.lowercase()
    if (ctype.isNotEmpty() && "html" !in ctype) throw SourceException("conteudo_invalido", "página não é HTML (${ctype.substringBefore(';')})")
    return extractPage(resp.text())
}
