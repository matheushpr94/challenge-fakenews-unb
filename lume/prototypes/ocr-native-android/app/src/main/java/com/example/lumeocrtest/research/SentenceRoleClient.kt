package com.example.lumeocrtest.research

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Papéis de frase que o modelo FactNews distingue. Descrevem o PAPEL da frase na notícia; não dizem se é verdadeira. */
object SentenceRole {
    const val FACTUAL = "factual"
    const val CITACAO = "citacao"
    const val ENVIESADA = "enviesada"
    val ALL = setOf(FACTUAL, CITACAO, ENVIESADA)
}

/** Rótulo e confiança (0–1) de uma frase, mais as três probabilidades. */
data class RoleScore(val label: String, val confidence: Double, val probs: Map<String, Double>)

fun interface SentenceRoleClassifier {
    /** Um resultado por frase, na mesma ordem; null quando o modelo não está disponível. */
    suspend fun classify(sentences: List<String>): List<RoleScore>?
}

/** Valida e converte a resposta do servidor local (`POST /classify`). null se o formato não bater com o pedido. */
fun parseRoleResponse(json: String, expected: Int): List<RoleScore>? {
    val results: JsonArray = runCatching { JsonParser.parseString(json).asJsonObject.getAsJsonArray("results") }.getOrNull() ?: return null
    if (results.size() != expected) return null
    return results.map { el ->
        val o = el as? JsonObject ?: return null
        val label = o.get("label")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val conf = o.get("confidence")?.takeIf { it.isJsonPrimitive }?.asDouble ?: return null
        if (label !in SentenceRole.ALL || conf.isNaN() || conf < 0.0 || conf > 1.0) return null
        val probs = o.getAsJsonObject("probs")?.entrySet()?.associate { (k, v) -> k to v.asDouble } ?: return null
        RoleScore(label, conf, probs)
    }
}

/**
 * Cliente do rotulador de frases que roda no computador da demonstração (`ml/factnews/scripts/serve.py`; o emulador
 * alcança por `adb reverse tcp:8765 tcp:8765`). O texto só vai para esse endereço local configurado em local.properties.
 * Se o servidor não responder, devolve null por um tempo curto e o app segue sem a análise de linguagem.
 */
class LocalSentenceRoleClient(private val baseUrl: String) : SentenceRoleClassifier {
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    @Volatile private var unavailableUntil = 0L
    private companion object { const val CHUNK = 100 }

    override suspend fun classify(sentences: List<String>): List<RoleScore>? = withContext(Dispatchers.IO) {
        if (sentences.isEmpty() || System.currentTimeMillis() < unavailableUntil) return@withContext null
        try {
            // Blocos de até CHUNK frases por chamada: mantém cada resposta curta e abaixo do limite do servidor.
            sentences.chunked(CHUNK).flatMap { chunk ->
                val body = JsonObject().apply { add("sentences", JsonArray().also { a -> chunk.forEach { a.add(it) } }) }
                val req = Request.Builder().url("${baseUrl.trimEnd('/')}/classify")
                    .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    parseRoleResponse(response.body?.string().orEmpty(), chunk.size) ?: throw IllegalStateException("resposta fora do formato")
                }
            }
        } catch (e: Exception) {
            runCatching { Log.w("LumeRoles", "Análise de linguagem indisponível: ${e.message}") }
            unavailableUntil = System.currentTimeMillis() + 20_000L
            null
        }
    }
}
