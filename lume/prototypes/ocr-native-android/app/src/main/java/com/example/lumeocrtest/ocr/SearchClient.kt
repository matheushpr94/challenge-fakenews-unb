package com.example.lumeocrtest.ocr

import android.text.Html
import android.util.Xml
import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import org.xmlpull.v1.XmlPullParser

class SearchClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun searchWikipedia(queryAnalyzer: AnalyzedQuery): List<ContextResult> = withContext(Dispatchers.IO) {
        val entitiesToSearch = queryAnalyzer.mainEntities.take(3)
        if (entitiesToSearch.isEmpty()) return@withContext emptyList()

        val allResults = mutableListOf<ContextResult>()
        var successfulQueries = 0
        var lastError: Exception? = null

        for (entity in entitiesToSearch) {
            currentCoroutineContext().ensureActive()
            if (entity.isBlank() || entity.length < 3) continue
            try {
                Log.d("LumeSearch", "Wikipedia query for entity: $entity")
                val url = "https://pt.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encode(entity)}&format=json&srlimit=3"
                val body = get(url)
                val root = JsonParser.parseString(body).asJsonObject
                successfulQueries++
                val items = root.getAsJsonObject("query")?.getAsJsonArray("search") ?: continue
                
                for (entry in items) {
                    val item = entry.asJsonObject
                    val title = item.get("title")?.asString?.trim().orEmpty()
                    if (title.isBlank()) continue
                    
                    // Rejeita páginas de desambiguação
                    if (title.contains("desambiguação", ignoreCase = true)) continue
                    
                    val snippet = Html.fromHtml(item.get("snippet")?.asString.orEmpty(), Html.FROM_HTML_MODE_LEGACY).toString().trim()
                    if (snippet.contains("desambiguação", ignoreCase = true)) continue
                    
                    // Valida se o título realmente refere-se à entidade pretendida (evitando correspondência parcial enganosa)
                    val entityParts = entity.split(" ").filter { it.length > 2 }
                    val titleLower = title.lowercase()
                    val entityLower = entity.lowercase()
                    
                    val isMatch = if (entityParts.size >= 2) {
                        entityParts.all { titleLower.contains(it.lowercase()) } || titleLower.contains(entityLower)
                    } else {
                        titleLower == entityLower || titleLower.startsWith("$entityLower ") || titleLower.contains(" ($entityLower)")
                    }
                    
                    if (!isMatch) continue

                    val pageUrl = "https://pt.wikipedia.org/wiki/${encode(title.replace(' ', '_'))}"
                    if (allResults.any { it.url == pageUrl }) continue
                    
                    allResults.add(ContextResult(
                        title = title, 
                        snippet = snippet, 
                        url = pageUrl, 
                        relationText = "Contexto enciclopédico sobre $entity."
                    ))
                    break // Pega o melhor candidato para esta entidade
                }
                if (allResults.size >= 3) break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.e("LumeSearch", "Wikipedia search error for entity $entity", e)
            }
        }
        currentCoroutineContext().ensureActive()
        if (successfulQueries == 0) throw IllegalStateException("Não foi possível consultar a Wikipédia.", lastError)
        return@withContext allResults.take(3)
    }

    suspend fun searchGoogleNews(queryAnalyzer: AnalyzedQuery): List<NewsResult> = withContext(Dispatchers.IO) {
        if (queryAnalyzer.searchQueries.isEmpty()) return@withContext emptyList()
        val allResults = mutableListOf<NewsResult>()
        val scorer = RelevanceScorer()
        
        var successfulQueries = 0
        var lastError: Exception? = null
        for (search in queryAnalyzer.searchQueries) {
            currentCoroutineContext().ensureActive()
            if (search.isBlank()) continue
            try {
                Log.d("LumeSearch", "News query: $search")
                val url = "https://news.google.com/rss/search?q=${encode(search)}&hl=pt-BR&gl=BR&ceid=BR:pt-419"
                val xml = get(url)
                val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
                
                var inItem = false
                var currentTag = ""
                var title = StringBuilder()
                var link = StringBuilder()
                var source = StringBuilder()
                var date = StringBuilder()
                var desc = StringBuilder()
                var seenItems = 0
                
                while (parser.eventType != XmlPullParser.END_DOCUMENT && seenItems < 30) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name
                            if (currentTag == "item") {
                                inItem = true
                                title = StringBuilder(); link = StringBuilder()
                                source = StringBuilder(); date = StringBuilder()
                                desc = StringBuilder()
                            }
                        }
                        XmlPullParser.TEXT -> if (inItem) when (currentTag) {
                            "title" -> title.append(parser.text)
                            "link" -> link.append(parser.text)
                            "source" -> source.append(parser.text)
                            "pubDate" -> date.append(parser.text)
                            "description" -> desc.append(parser.text)
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "item") {
                                seenItems++
                                val titleText = title.toString().trim()
                                val linkText = link.toString().trim()
                                val descText = Html.fromHtml(desc.toString(), Html.FROM_HTML_MODE_LEGACY).toString().trim()
                                
                                if (titleText.isNotEmpty() && linkText.startsWith("https://") && allResults.none { it.url == linkText || it.title == titleText }) {
                                    val scoreResult = scorer.score(queryAnalyzer, titleText, descText, isWiki = false)
                                    if (scoreResult.isRelevant) {
                                        allResults.add(NewsResult(titleText, source.toString().trim(), date.toString().trim(), linkText, scoreResult.relationText))
                                    }
                                }
                                inItem = false
                            }
                            currentTag = ""
                        }
                    }
                    parser.next()
                }
                
                successfulQueries++
                if (allResults.size >= 5) break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.e("LumeSearch", "News search error for query $search", e)
            }
        }
        
        currentCoroutineContext().ensureActive()
        if (successfulQueries == 0) throw IllegalStateException("Não foi possível consultar a fonte de busca.", lastError)
        return@withContext allResults.take(8)
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "LumeOCRTest/0.1 (Android)").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string() ?: error("Resposta vazia")
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
}
