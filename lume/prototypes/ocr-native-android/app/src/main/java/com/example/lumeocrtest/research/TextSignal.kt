package com.example.lumeocrtest.research

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/*
 * "Sinal do texto": o quanto o ESTILO da escrita lembra textos de boato/checagem ou de notícia comum, medido por um SVM
 * treinado no FakeRecogna (servidor local lume/ml/sinal_texto). Não verifica fatos, não decide a relação das fontes e nunca
 * aparece como número: só como faixa ("tende a…"), com a posição numa escala e as palavras que pesaram.
 */

object SignalBand {
    const val DESINFORMACAO = "desinformacao"
    const val SEM_SINAL = "sem_sinal"
    const val NOTICIA = "noticia"
}

data class SignalTerm(val termo: String, val peso: Double)

data class TextSignal(
    /** [SignalBand]. */
    val faixa: String,
    /** 0 = estilo de notícia comum, 1 = estilo de boato. */
    val posicao: Float,
    /** Posição dos cortes na mesma escala (zona de notícia: até [limiteNoticia]; de boato: a partir de [limiteBoato]). */
    val limiteNoticia: Float,
    val limiteBoato: Float,
    val palavras: Int,
    /** curto | normal: textos curtos usam cortes mais conservadores. */
    val regime: String,
    val pesamBoato: List<SignalTerm> = emptyList(),
    val pesamNoticia: List<SignalTerm> = emptyList(),
)

/** Pontua o estilo do texto; null quando o servidor local não está disponível ou o texto não serve. */
fun interface TextSignalScorer {
    /** Falso quando não há servidor configurado: a pesquisa nem tenta (e não lê a página só para isso). */
    val configured: Boolean get() = true

    suspend fun score(text: String): TextSignal?
}

/** Interpreta a resposta do servidor. Qualquer resposta inesperada vira null (o app segue sem o sinal). */
fun parseTextSignal(json: String): TextSignal? = runCatching {
    val o = JsonParser.parseString(json).asJsonObject
    if (!o.get("ok").asBoolean) return null
    val band = o.get("faixa").asString
    if (band !in setOf(SignalBand.DESINFORMACAO, SignalBand.SEM_SINAL, SignalBand.NOTICIA)) return null
    fun terms(k: String) = o.getAsJsonArray(k)?.map { it.asJsonObject }?.map { SignalTerm(it.get("termo").asString, it.get("peso").asDouble) }.orEmpty()
    val lim = o.getAsJsonObject("limites")
    TextSignal(
        faixa = band, posicao = o.get("posicao").asFloat.coerceIn(0f, 1f),
        limiteNoticia = lim.get("noticia").asFloat.coerceIn(0f, 1f), limiteBoato = lim.get("boato").asFloat.coerceIn(0f, 1f),
        palavras = o.get("palavras").asInt, regime = o.get("regime").asString,
        pesamBoato = terms("pesam_boato"), pesamNoticia = terms("pesam_noticia"),
    )
}.getOrNull()

/** Servidor local (lume/ml/sinal_texto/servidor_sinal.py). No emulador: `adb reverse tcp:8766 tcp:8766`. */
class HttpTextSignalScorer(
    private val baseUrlProvider: () -> String = { "http://127.0.0.1:8766" },
    private val tokenProvider: () -> String? = { null },
) : TextSignalScorer {
    constructor(baseUrl: String) : this({ baseUrl })

    override val configured: Boolean get() = baseUrlProvider().isNotBlank()
    private var lastUrl = ""
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS).build()
    private var unavailableUntil = 0L

    override suspend fun score(text: String): TextSignal? = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider()
        if (baseUrl != lastUrl) { lastUrl = baseUrl; unavailableUntil = 0L }
        if (baseUrl.isBlank() || System.currentTimeMillis() < unavailableUntil) return@withContext null
        try {
            val body = JsonObject().apply { addProperty("texto", text.take(MAX_SIGNAL_CHARS)) }
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/sinal")
                .apply { tokenProvider()?.let { header(ServerConfig.HEADER, it) } }
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
                parseTextSignal(r.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            runCatching { Log.w("LumeSignal", "Sinal do texto indisponível: ${e.message}") }
            unavailableUntil = System.currentTimeMillis() + 60_000L
            null
        }
    }
}

const val MIN_SIGNAL_WORDS = 60
const val MAX_SIGNAL_CHARS = 20_000

/**
 * Texto que vai para o sinal: o corpo da matéria lida (com o título) ou, se a entrada for longa, a própria entrada.
 * Título solto, frase curta e texto todo em maiúsculas (OCR de manchete) não têm estilo medível: devolve null.
 */
fun signalText(context: ArticleContext?, text: String): String? {
    val fromArticle = context?.let { listOfNotNull(it.title, it.body).filter { s -> s.isNotBlank() }.joinToString(". ") }.orEmpty()
    val best = listOf(fromArticle, text.trim()).maxByOrNull { it.split(Regex("\\s+")).size }.orEmpty()
    if (best.isBlank() || Regex("^https?://\\S+$").matches(best)) return null
    if (best.split(Regex("\\s+")).size < MIN_SIGNAL_WORDS) return null
    val letters = best.filter { it.isLetter() }
    if (letters.isNotEmpty() && letters.count { it.isUpperCase() }.toDouble() / letters.length > 0.7) return null
    return best.take(MAX_SIGNAL_CHARS)
}

/** Agência de checagem encontrada entre as fontes da pesquisa. */
data class FactCheckSource(val agencia: String, val titulo: String, val url: String, val veiculo: String)

private val FACT_CHECKERS = listOf(
    "aosfatos.org" to "Aos Fatos", "lupa.news" to "Agência Lupa", "lupa.uol.com.br" to "Agência Lupa",
    "projetocomprova.com.br" to "Projeto Comprova", "estadao.com.br/verifica" to "Estadão Verifica",
    "g1.globo.com/fato-ou-fake" to "Fato ou Fake (g1)", "boatos.org" to "Boatos.org", "e-farsas.com" to "E-Farsas",
    "checamos.afp.com" to "AFP Checamos", "factcheck.afp.com" to "AFP Fact Check", "reuters.com/fact-check" to "Reuters Fact Check",
    "bbc.com/portuguese/articles/c" to "BBC News Brasil Verifica", "uol.com.br/confere" to "UOL Confere",
)

/** Fontes que são de agências de checagem (pelo endereço). Uma por agência. */
fun factChecksIn(cards: List<ResultCard>): List<FactCheckSource> =
    cards.mapNotNull { c ->
        val url = c.url.lowercase()
        FACT_CHECKERS.firstOrNull { (key, _) -> key in url }?.let { (_, nome) -> FactCheckSource(nome, c.titulo, c.url, c.veiculo) }
    }.distinctBy { it.agencia }
