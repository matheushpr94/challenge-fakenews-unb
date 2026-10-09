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

/**
 * Modelo de "mesmo fato" (parte do 6B do ml-pipeline, ciclo 2): regressão logística sobre três números por par
 * (afirmação, título da fonte): cosseno do MPNet ajustado (Ollama), sobreposição de palavras e a nota do BERTimbau
 * ajustado (servidor Python local). Só PROMOVE fontes que a regra deixou em dúvida; nunca rebaixa nem decide veracidade.
 * Pesos e corte estimados no treino/validação dos 1.650 pares v3 (lume/ml/integracao_modelo_matheus).
 */
object PairModel {
    private val MEDIAS = doubleArrayOf(0.5623891750147286, 0.27772204014006235, 0.5384060415562084)
    private val DESVIOS = doubleArrayOf(0.2583979207440838, 0.2149050612814223, 0.4562862134997896)
    private val PESOS = doubleArrayOf(0.8188883495449529, 0.6377980487241676, 0.8917558194280873)
    private const val INTERCEPTO = -0.3153206821967418
    /** Nota mínima para "provável mesmo fato" quando a regra não reconheceu a ação (erro de ~5% no desenvolvimento). */
    const val CORTE = 0.783151
    /**
     * Corte quando a regra reconheceu a ação e mesmo assim não confirmou (assunto com várias etapas). No desenvolvimento dos
     * 1.650 pares (treino+validação): 0,78 → 80% das promovidas certas (119 recuperadas); 0,86 → 86% (67); 0,90 → 87% (45).
     */
    const val CORTE_COM_ACAO = 0.86
    /** Abaixo desta nota, uma fonte que a regra confirmou ganha o aviso "confira": no desenvolvimento, a regra estava errada em 10 de 11. */
    const val AVISO_ABAIXO = 0.20
    /**
     * Nota a partir da qual o modelo CONFIRMA "mesmo fato" (não só "incerta"). Escolhida no desenvolvimento dos 1.650 pares:
     * regra sozinha 48,6% de recall e 7,6% de falso positivo; regra + modelo (0,90 sem ação, 0,93 com ação) 54,6% e 8,1%,
     * com a mesma precisão (~82%). Com ação reconhecida o corte é maior porque ali os erros são outra etapa do mesmo assunto.
     */
    const val CONFIRMA_SEM_ACAO = 0.90
    const val CONFIRMA_COM_ACAO = 0.93

    /** Jaccard das palavras com 3+ letras (sem acento, minúsculas), a mesma definição usada na medição. */
    fun sobreposicao(a: String, b: String): Double {
        fun words(s: String) = Regex("[a-z0-9]+").findAll(java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")).map { it.value }.filter { it.length >= 3 }.toSet()
        val x = words(a); val y = words(b)
        return if (x.isEmpty() || y.isEmpty()) 0.0 else x.intersect(y).size.toDouble() / x.union(y).size
    }

    fun nota(cosseno: Double, sobreposicao: Double, notaTexto: Double): Double {
        val v = doubleArrayOf(cosseno, sobreposicao, notaTexto)
        var z = INTERCEPTO
        for (i in v.indices) z += PESOS[i] * (v[i] - MEDIAS[i]) / DESVIOS[i]
        return 1.0 / (1.0 + Math.exp(-z))
    }
}

/** Nota 0–1 de "esta fonte fala do mesmo fato?" por título; null quando o servidor local não responde. */
fun interface PairScorer {
    suspend fun scores(claim: String, titles: List<String>): List<Double>?
}

/** BERTimbau ajustado servido por lume/ml/integracao_modelo_matheus/servidor_bert.py (emulador: `adb reverse tcp:8765 tcp:8765`). */
class HttpPairScorer(private val baseUrl: String = "http://127.0.0.1:8765") : PairScorer {
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private var unavailableUntil = 0L

    override suspend fun scores(claim: String, titles: List<String>): List<Double>? = withContext(Dispatchers.IO) {
        if (titles.isEmpty() || System.currentTimeMillis() < unavailableUntil) return@withContext null
        try {
            val body = JsonObject().apply {
                addProperty("claim", claim.take(500))
                add("titles", JsonArray().apply { titles.forEach { add(it.take(300)) } })
            }
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}/score")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
                val arr = JsonParser.parseString(r.body?.string() ?: "{}").asJsonObject.getAsJsonArray("scores")
                if (arr.size() != titles.size) return@withContext null
                arr.map { it.asDouble }
            }
        } catch (e: Exception) {
            runCatching { Log.w("LumeSemantic", "Modelo de mesmo fato indisponível: ${e.message}") }
            unavailableUntil = System.currentTimeMillis() + 60_000L
            null
        }
    }
}
