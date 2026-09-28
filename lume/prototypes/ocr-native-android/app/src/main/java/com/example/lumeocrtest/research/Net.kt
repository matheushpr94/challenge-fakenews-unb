package com.example.lumeocrtest.research

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Falha técnica ao acessar uma URL. `kind` é um código curto para logs e para a tela. */
class FetchException(val kind: String, message: String) : Exception(message)

data class FetchOptions(
    val timeoutMs: Long = 8_000,
    val maxBytes: Int = 1_500_000,
    val accept: String = "*/*",
    val userAgent: String? = null,
)

class FetchResponse(val url: String, val status: Int, val contentType: String, val body: ByteArray, val truncated: Boolean = false) {
    fun text(): String {
        var enc = Regex("charset=([\\w\\-]+)", RegexOption.IGNORE_CASE).find(contentType)?.groupValues?.get(1)
        if (enc == null) {
            val head = String(body, 0, minOf(body.size, 4096), Charsets.ISO_8859_1)
            enc = (Regex("<meta[^>]+charset=[\"']?([\\w\\-]+)", RegexOption.IGNORE_CASE).find(head)
                ?: Regex("encoding=[\"']([\\w\\-]+)").find(head))?.groupValues?.get(1)
        }
        val cs = runCatching { Charset.forName(enc ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        return String(body, cs)
    }
}

/** Acesso à rede usado pela pesquisa. Nos testes é substituído por respostas simuladas. */
fun interface Fetcher {
    suspend fun fetch(url: String, options: FetchOptions): FetchResponse
}

const val DEFAULT_USER_AGENT = "Mozilla/5.0 (compatible; LumeApp/0.1; uso educacional)"

/** Valida esquema, porta e host; bloqueia endereços internos. Sem `resolve` não consulta DNS. */
object UrlGuard {
    private val BLOCKED_SUFFIXES = listOf(".localhost", ".local", ".internal", ".lan", ".home.arpa")

    fun check(url: String?, resolve: Boolean = true): URI {
        if (url.isNullOrBlank() || url.length > 2048) throw FetchException("url_invalida", "URL ausente ou longa demais")
        val uri = try { URI(url.trim()) } catch (e: Exception) { throw FetchException("url_invalida", "URL malformada") }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") throw FetchException("url_bloqueada", "esquema não permitido: ${scheme ?: "(vazio)"}")
        val host = (uri.host ?: "").lowercase().trim('[', ']')
        if (host.isEmpty()) throw FetchException("url_invalida", "URL sem host")
        if (uri.rawUserInfo != null) throw FetchException("url_bloqueada", "URL com credenciais não permitida")
        if (uri.port != -1 && uri.port != 80 && uri.port != 443) throw FetchException("url_bloqueada", "porta não permitida: ${uri.port}")
        if (host == "localhost" || BLOCKED_SUFFIXES.any { host.endsWith(it) }) throw FetchException("url_bloqueada", "host interno não permitido")
        if (isIpLiteral(host)) {
            checkAddress(InetAddress.getByName(host))
        } else if (resolve) {
            val addrs = try { InetAddress.getAllByName(host) } catch (e: UnknownHostException) {
                throw FetchException("dns", "não foi possível resolver $host")
            }
            addrs.forEach { checkAddress(it) }
        }
        return uri
    }

    private fun isIpLiteral(host: String) = Regex("^[0-9.]+$").matches(host) || host.contains(':')

    fun checkAddress(a: InetAddress) {
        if (!isPublic(a)) throw FetchException("url_bloqueada", "endereço interno ou reservado não permitido")
    }

    fun isPublic(a: InetAddress): Boolean {
        if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return false
        val b = a.address.map { it.toInt() and 0xff }
        if (a is Inet4Address) {
            if (b[0] == 0 || b[0] >= 240) return false
            if (b[0] == 100 && b[1] in 64..127) return false // CGNAT
            if (b[0] == 192 && b[1] == 0 && b[2] == 0) return false
            if (b[0] == 198 && b[1] in 18..19) return false
            return true
        }
        if (a is Inet6Address) {
            if ((b[0] and 0xfe) == 0xfc) return false // fc00::/7
            if (b.subList(0, 10).all { it == 0 } && b[10] == 0xff && b[11] == 0xff) { // IPv4 mapeado
                return isPublic(InetAddress.getByAddress(a.address.copyOfRange(12, 16)))
            }
        }
        return true
    }
}

/** Implementação real com OkHttp: prazo total, limite de bytes, redirecionamentos revalidados,
 * DNS que recusa IPs internos (evita "rebinding") e cancelamento junto com a corrotina. */
class OkHttpFetcher(private val base: OkHttpClient = defaultClient()) : Fetcher {
    companion object {
        private val SAFE_DNS = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addrs = Dns.SYSTEM.lookup(hostname).filter { UrlGuard.isPublic(it) }
                if (addrs.isEmpty()) throw UnknownHostException("$hostname só resolve para endereços internos")
                return addrs
            }
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false)
            .dns(SAFE_DNS)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun fetch(url: String, options: FetchOptions): FetchResponse {
        val deadline = System.currentTimeMillis() + options.timeoutMs
        var current = url
        repeat(5) {
            UrlGuard.check(current, resolve = false) // o DNS seguro valida os IPs na conexão
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) throw FetchException("tempo_esgotado", "tempo esgotado")
            val client = base.newBuilder().callTimeout(remaining, TimeUnit.MILLISECONDS).build()
            val request = Request.Builder().url(current)
                .header("User-Agent", options.userAgent ?: DEFAULT_USER_AGENT)
                .header("Accept", options.accept)
                .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.5")
                .build()
            val result = execute(client.newCall(request), options.maxBytes)
            if (result.status in listOf(301, 302, 303, 307, 308)) {
                val location = result.location ?: throw FetchException("http", "redirecionamento sem destino")
                current = current.toHttpUrlOrNull()?.resolve(location)?.toString()
                    ?: throw FetchException("url_invalida", "redirecionamento inválido")
                return@repeat
            }
            if (result.status !in 200..299) throw FetchException("http", "HTTP ${result.status}")
            return FetchResponse(current, result.status, result.contentType, result.body, result.truncated)
        }
        throw FetchException("redirecionamentos", "redirecionamentos demais")
    }

    private class Raw(val status: Int, val contentType: String, val body: ByteArray, val truncated: Boolean, val location: String?)

    private suspend fun execute(call: Call, maxBytes: Int): Raw = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(classify(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { r ->
                        val bytes = java.io.ByteArrayOutputStream()
                        var truncated = false
                        val input = if (r.code in 200..299) r.body?.byteStream() else null
                        if (input != null) {
                            val buf = ByteArray(65536)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                bytes.write(buf, 0, n)
                                if (bytes.size() >= maxBytes) { truncated = true; break }
                            }
                        }
                        val body = bytes.toByteArray().let { if (it.size > maxBytes) it.copyOf(maxBytes) else it }
                        if (cont.isActive) cont.resume(Raw(r.code, r.header("Content-Type") ?: "", body, truncated, r.header("Location")))
                    }
                } catch (e: IOException) {
                    if (cont.isActive) cont.resumeWithException(classify(e))
                }
            }
        })
    }

    private fun classify(e: IOException): FetchException = when {
        e is InterruptedIOException || e.message?.contains("timeout", true) == true -> FetchException("tempo_esgotado", "tempo esgotado")
        e is UnknownHostException -> FetchException("dns", e.message ?: "falha de DNS")
        e is javax.net.ssl.SSLException -> FetchException("tls", "falha TLS/certificado: ${e.message}")
        else -> FetchException("rede", "falha de rede: ${e.message}")
    }
}
