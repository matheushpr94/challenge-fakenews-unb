package app.lume.mvp

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object Api {
    @Volatile private var connection: HttpURLConnection? = null
    fun cancel() { connection?.disconnect(); connection = null }
    fun analyze(context: Context, mode: String, question: String, image: Bitmap?, text: String): JSONObject {
        check(!BuildConfig.DEMO_ONLY) { "Esta versão de demonstração não se conecta à IA." }
        val prefs = context.getSharedPreferences("lume", Context.MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", "http://127.0.0.1:8787")!!.trim().trimEnd('/')
        val token = prefs.getString("token", "")!!.trim()
        if (token.length < 32) error("Configure o endereço do servidor e o token do piloto no app Lume.")
        val url = URL("$endpoint/analyze")
        require(url.protocol == "https" || (BuildConfig.DEBUG && url.protocol == "http")) { "Use um servidor HTTPS." }
        val payload = JSONObject().put("mode", mode).put("text", text).put("question", question)
        if (image != null) {
            val bytes = ByteArrayOutputStream().use { buffer -> image.compress(Bitmap.CompressFormat.JPEG, 85, buffer); buffer.toByteArray() }
            payload.put("image", "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 12000; readTimeout = 75000
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        connection = conn
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val success = conn.responseCode in 200..299
            val stream = if (success) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = try { JSONObject(raw) } catch (_: Exception) { error("Resposta inválida do servidor.") }
            if (!success) error(response.optString("error", "Não foi possível concluir a análise."))
            return response
        } finally { conn.disconnect(); if (connection === conn) connection = null }
    }
}
