package com.example.lumeocrtest.research

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit

/**
 * Similaridade de sentido (0–1) entre a afirmação e o título de cada fonte. Serve para ORDENAR candidatas
 * (quais páginas ler primeiro) e, na comparação estruturada, só para decidir se o assunto é equivalente quando
 * quem agiu e a ação já coincidem pelo texto. Não classifica relação, não decide veracidade e não produz texto.
 */
fun interface SemanticRanker {
    /** Uma similaridade por texto, na mesma ordem; null quando o modelo não está disponível. */
    suspend fun similarities(claim: String, texts: List<String>): List<Double>?
}

/**
 * Modelo multilíngue de paráfrase (paraphrase-multilingual, ~560 MB) servido pelo Ollama no computador da
 * demonstração (emulador com `adb reverse tcp:11434 tcp:11434`). Escolhido nos testes com pares reais em português:
 * não perdeu nenhum relato do mesmo acontecimento, mas aceita fatos parecidos de outra data, etapa ou lugar —
 * por isso nunca decide sozinho.
 */
class OllamaEmbeddingRanker(
    private val model: String = "paraphrase-multilingual",
    private val baseUrl: String = "http://127.0.0.1:11434",
) : SemanticRanker {
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
    private var unavailableUntil = 0L

    override suspend fun similarities(claim: String, texts: List<String>): List<Double>? = withContext(Dispatchers.IO) {
        if (texts.isEmpty() || System.currentTimeMillis() < unavailableUntil) return@withContext null
        try {
            val input = JsonArray().apply { add(claim.take(500)); texts.forEach { add(it.take(500)) } }
            val body = JsonObject().apply { addProperty("model", model); add("input", input) }
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/api/embed")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Ollama HTTP ${response.code}")
                val arr = JsonParser.parseString(response.body?.string() ?: "{}").asJsonObject.getAsJsonArray("embeddings")
                val vectors = arr.map { v -> v.asJsonArray.let { a -> DoubleArray(a.size()) { a[it].asDouble } } }
                if (vectors.size != texts.size + 1) return@withContext null
                val q = vectors[0]
                vectors.drop(1).map { cosine(q, it) }
            }
        } catch (e: Exception) {
            runCatching { Log.w("LumeSemantic", "Comparação de sentido indisponível: ${e.message}") }
            unavailableUntil = System.currentTimeMillis() + 60_000L
            null
        }
    }

    private fun cosine(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / Math.sqrt(na * nb)
    }
}
