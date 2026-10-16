package com.example.lumeocrtest.research

import android.content.Context
import android.content.SharedPreferences
import com.example.lumeocrtest.BuildConfig

/**
 * Servidor dos modelos (Ollama, BERTimbau e sinal do texto) que o celular usa SEM cabo: o gateway do Mac
 * (`lume/ml/gateway`) exposto por um túnel HTTPS. Endereço e chave ficam só neste aparelho (fora do backup).
 * Sem endereço salvo, vale o servidor "de fábrica" da compilação (BuildConfig.SERVER_URL e SERVER_TOKEN, vindos do
 * local.properties); se ele também estiver vazio, o app usa os endereços locais da compilação (emulador com `adb reverse`).
 */
object ServerConfig {
    const val HEADER = "X-Lume-Token"
    private const val FILE = "lume_servidor"
    private const val KEY_URL = "endereco"
    private const val KEY_TOKEN = "chave"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var remoteUrl: String = ""
    @Volatile private var remoteToken: String = ""

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        val salvoUrl = p.getString(KEY_URL, "").orEmpty()
        if (salvoUrl.isNotBlank()) {
            remoteUrl = salvoUrl
            remoteToken = p.getString(KEY_TOKEN, "").orEmpty()
        } else {
            aplicarFabrica()
        }
    }

    /** Endereço e chave gravados na compilação (vazios quando o APK foi gerado sem `lume.servidor.*`). */
    private fun aplicarFabrica() {
        remoteUrl = normalizeBaseUrl(BuildConfig.SERVER_URL).orEmpty()
        remoteToken = if (remoteUrl.isBlank()) "" else BuildConfig.SERVER_TOKEN.trim()
    }

    /** Há um servidor de fábrica e nada foi salvo por cima dele. */
    val isFactory: Boolean get() = remoteUrl.isNotBlank() && prefs?.getString(KEY_URL, "").isNullOrBlank()

    val address: String get() = remoteUrl
    val token: String get() = remoteToken
    val isRemote: Boolean get() = remoteUrl.isNotBlank()

    /** Salva (ou limpa, com campos vazios: volta ao servidor de fábrica, se houver). Devolve o endereço normalizado ou null se for inválido. */
    fun save(rawUrl: String, rawToken: String): String? {
        val url = if (rawUrl.isBlank()) "" else normalizeBaseUrl(rawUrl) ?: return null
        val tk = rawToken.trim()
        prefs?.edit()?.putString(KEY_URL, url)?.putString(KEY_TOKEN, tk)?.apply()
        if (url.isBlank()) aplicarFabrica() else { remoteUrl = url; remoteToken = tk }
        return remoteUrl
    }

    fun ollamaUrl(): String = remoteUrl.ifBlank { BuildConfig.OLLAMA_URL }
    fun pairUrl(): String = remoteUrl.ifBlank { BuildConfig.PAIR_MODEL_URL }
    fun signalUrl(): String = remoteUrl.ifBlank { BuildConfig.TEXT_SIGNAL_URL }
    /** Chave só vai quando há servidor remoto configurado. */
    fun tokenOrNull(): String? = if (remoteUrl.isNotBlank() && remoteToken.isNotBlank()) remoteToken else null
}

/**
 * Endereço do gateway: sem esquema vira https; só aceita http para o computador do emulador (10.0.2.2) e 127.0.0.1,
 * que são os únicos permitidos em texto claro pela configuração de rede do app. Remove espaços e a barra final.
 */
fun normalizeBaseUrl(raw: String): String? {
    var t = raw.trim().trimEnd('/')
    if (t.isEmpty() || t.any { it.isWhitespace() }) return null
    if (!Regex("(?i)^https?://").containsMatchIn(t)) t = "https://$t"
    val m = Regex("(?i)^(https?)://([a-z0-9][a-z0-9.-]*[a-z0-9])(:\\d{1,5})?(/.*)?$").matchEntire(t) ?: return null
    val scheme = m.groupValues[1].lowercase()
    val host = m.groupValues[2].lowercase()
    if (scheme == "http" && host != "10.0.2.2" && host != "127.0.0.1") return null
    if (!host.contains('.')) return null
    return "$scheme://$host${m.groupValues[3]}${m.groupValues[4].trimEnd('/')}"
}

/** Pergunta ao gateway se os três serviços do Mac estão no ar. Devolve uma frase pronta para a tela. */
suspend fun testServer(baseUrl: String, token: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val client = okhttp3.OkHttpClient.Builder().connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS).build()
        val req = okhttp3.Request.Builder().url("${baseUrl.trimEnd('/')}/saude")
            .apply { if (token.isNotBlank()) header(ServerConfig.HEADER, token.trim()) }.build()
        client.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            when {
                r.code == 401 -> "A chave não confere. Copie de novo a chave que o script do Mac mostrou."
                !r.isSuccessful -> "O servidor respondeu com erro (${r.code}). Confira o endereço."
                else -> {
                    val o = com.google.gson.JsonParser.parseString(body).asJsonObject
                    val s = o.getAsJsonObject("servicos")
                    val names = mapOf("sinal" to "sinal do texto", "bertimbau" to "BERTimbau", "ollama" to "Ollama")
                    val down = names.filter { (k, _) -> s?.get(k)?.asBoolean != true }.values
                    if (down.isEmpty()) "Conectado: os três serviços do Mac estão no ar."
                    else "Conectado ao gateway, mas fora do ar no Mac: ${down.joinToString(", ")}."
                }
            }
        }
    } catch (e: Exception) {
        "Sem resposta do servidor. Veja se o script do Mac está rodando e se o endereço está certo."
    }
}
