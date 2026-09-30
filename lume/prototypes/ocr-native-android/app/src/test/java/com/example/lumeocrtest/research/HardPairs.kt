package com.example.lumeocrtest.research

import com.example.lumeocrtest.ocr.ArticleReader
import com.example.lumeocrtest.ocr.OcrFixtures
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Pares difíceis (notícia importada × fonte candidata) em `src/test/resources/pairs/pares-dificeis.json`.
 * Os pares reais usam a captura (saída real do ML Kit) e os textos que os buscadores devolveram em 29-30/09/2026;
 * os sintéticos usam nomes fictícios. Nenhuma regra do app conhece esses nomes.
 */
data class HardPair(
    val id: String, val tipo: String, val original: ArticleContext, val claim: String, val candidate: Candidate,
    val expected: String, val accepted: List<String>, val why: String,
)

object HardPairs {
    /** Momento da pesquisa usado nos pares reais (logo depois da coleta). */
    val REAL_NOW: Long = Dates.parse("2026-09-30T03:00:00Z")!!

    private val readings = HashMap<String, Pair<ArticleContext, String>>()

    /** Lê a captura como o app: OCR -> leitura por papéis -> afirmação sugerida. */
    fun fromCapture(name: String): Pair<ArticleContext, String> = readings.getOrPut(name) {
        val f = OcrFixtures.load(name)
        val reader = ArticleReader()
        val reading = reader.read(f.blocks, f.width, f.height)
        val choice = reader.claimFor(reading)
        ArticleContext(
            title = reading.title, subtitle = reading.subtitle, body = reading.body,
            source = reading.metadata.source, author = reading.metadata.author,
            publishedAtMs = reading.metadata.publishedAtMs, url = null,
        ) to (choice.claim ?: choice.alternatives.first())
    }

    fun load(): List<HardPair> {
        val stream = HardPairs::class.java.classLoader!!.getResourceAsStream("pairs/pares-dificeis.json")!!
        val arr = JsonParser.parseString(stream.bufferedReader(Charsets.UTF_8).readText()).asJsonArray
        return arr.map { it.asJsonObject }.map { o ->
            val orig = o.getAsJsonObject("original")
            val (article, claim) = if (orig.has("captura")) fromCapture(orig.get("captura").asString) else {
                val a = ArticleContext(
                    title = orig.str("titulo"), subtitle = orig.str("subtitulo"), body = orig.str("corpo").orEmpty(),
                    source = orig.str("veiculo"), author = null, publishedAtMs = orig.str("data")?.let { Dates.parse(it) }, url = null)
                a to a.title!!
            }
            val c = o.getAsJsonObject("candidata")
            val page = c.str("pagina").orEmpty()
            val snippet = c.str("trecho").orEmpty()
            val cand = Candidate(
                title = c.str("titulo")!!, url = c.str("url")!!, sourceName = c.str("veiculo")!!, origin = c.str("origem")!!,
                published = c.str("data")?.let { Dates.parse(it) }, snippet = snippet,
                contentKind = if (page.isNotBlank()) "completo" else if (snippet.isNotBlank()) "trecho" else "titulo",
                domain = c.str("dominio").orEmpty(), pageText = page,
            )
            HardPair(o.get("id").asString, o.get("tipo").asString, article, claim, cand, o.get("esperado").asString,
                o.getAsJsonArray("aceitos").map { it.asString }, o.get("justificativa").asString)
        }
    }

    /** Momento da pesquisa: pares reais usam a coleta; sintéticos, um dia depois da matéria. */
    fun nowFor(p: HardPair): Long = if (p.id.startsWith("s-")) (p.original.publishedAtMs ?: REAL_NOW) + Dates.DAY_MS else REAL_NOW

    private fun JsonObject.str(k: String): String? = get(k)?.takeIf { !it.isJsonNull }?.asString
}
