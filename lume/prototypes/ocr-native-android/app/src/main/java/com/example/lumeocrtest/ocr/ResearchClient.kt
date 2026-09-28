package com.example.lumeocrtest.ocr

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Calls the copied research backend; cancellation closes the active HTTP call. */
class ResearchClient(private val baseUrl: String = "http://10.0.2.2:8772") {
    private val http = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(70, TimeUnit.SECONDS).build()

    suspend fun interpret(text: String, id: String, details: String = "", rejected: List<String> = emptyList(), round: Int = 0): JsonObject {
        val payload = JsonObject().apply {
            addProperty("texto", text); addProperty("id_consulta", id)
            addProperty("detalhes", details); addProperty("rodada", round)
            add("rejeitadas", com.google.gson.Gson().toJsonTree(rejected))
        }
        return post("/api/interpretar", payload)
    }

    suspend fun search(text: String, id: String): JsonObject = post("/api/search", JsonObject().apply {
        addProperty("texto", text); addProperty("id_consulta", id)
    }).also { require(it.string("id_consulta") == id) { "Resposta de outra pesquisa." } }

    private suspend fun post(path: String, payload: JsonObject): JsonObject = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(baseUrl.trimEnd('/') + path)
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(IOException(
                    "Não foi possível consultar o backend. Confira o endereço e se research-server está em execução.", e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val obj = decodeResearchResponse(it.body?.string().orEmpty())
                        if (!it.isSuccessful) throw IOException(obj.string("mensagem").ifBlank { "Falha do servidor: HTTP ${it.code}" })
                        obj
                    }
                    if (!continuation.isCancelled) continuation.resume(result)
                } catch (e: Exception) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }
            }
        })
    }
}

internal fun decodeResearchResponse(body: String): JsonObject {
    val parsed = JsonParser.parseString(body)
    require(parsed.isJsonObject) { "Resposta inválida do backend." }
    return parsed.asJsonObject
}
fun JsonObject.string(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
fun JsonObject.objects(key: String): List<JsonObject> = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    ?.filter { it.isJsonObject }?.map { it.asJsonObject }.orEmpty()
fun JsonObject.objectOrEmpty(key: String): JsonObject = get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
fun JsonObject.strings(key: String): List<String> = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    ?.filter { it.isJsonPrimitive }?.map { it.asString }.orEmpty()
