package com.example.lumeocrtest.ocr

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class EvidenceSource(val title: String, val url: String)
data class EvidenceResult(val summary: String, val sources: List<EvidenceSource>, val limited: Boolean)

class EvidenceClient(private val baseUrl: String = "http://10.0.2.2:8766") {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun analyze(claim: String, sources: List<Map<String, String>> = emptyList()): EvidenceResult = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "claim" to claim,
            "sources" to sources
        )
        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$baseUrl/analyze").post(body).build()
        
        try {
            http.newCall(request).execute().use { response ->
                val json = response.body?.string() ?: throw Exception("Resposta vazia")
                if (!response.isSuccessful) {
                    val errorMap = try { gson.fromJson(json, Map::class.java) } catch (e: Exception) { null }
                    val errorMessage = errorMap?.get("error")?.toString() ?: "Erro ${response.code}"
                    throw Exception(errorMessage)
                }
                gson.fromJson(json, EvidenceResult::class.java) ?: throw Exception("Resposta inválida")
            }
        } catch (e: ConnectException) {
            throw Exception("Não foi possível conectar ao servidor de evidências.")
        } catch (e: SocketTimeoutException) {
            throw Exception("O servidor demorou muito para responder.")
        }
    }
}
