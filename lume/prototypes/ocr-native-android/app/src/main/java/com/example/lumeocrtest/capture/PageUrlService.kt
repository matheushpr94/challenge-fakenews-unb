package com.example.lumeocrtest.capture

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Lê UMA vez, quando a pessoa toca em "Ler esta tela", o endereço da página aberta no navegador.
 * Não observa eventos, não guarda histórico e só enxerga os navegadores listados em `page_url_service.xml`.
 * O endereço completo vem do campo da barra de endereço, que o Chrome corta só na tela (o texto interno é inteiro).
 */
class PageUrlService : AccessibilityService() {
    companion object {
        @Volatile private var instance: PageUrlService? = null
        val enabled: Boolean get() = instance != null

        /** Campos da barra de endereço dos navegadores comuns. */
        private val URL_FIELDS = mapOf(
            "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
            "com.chrome.beta" to listOf("com.chrome.beta:id/url_bar"),
            "com.sec.android.app.sbrowser" to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text"),
            "org.mozilla.firefox" to listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view", "org.mozilla.firefox:id/url_bar_title"),
            "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
            "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
            "com.opera.browser" to listOf("com.opera.browser:id/url_field"),
        )

        /** Endereço da página no navegador em primeiro plano, ou null (serviço desligado, outro app, barra em edição). */
        fun readCurrentUrl(): String? = instance?.readUrl()
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private fun readUrl(): String? = try {
        val root = rootInActiveWindow
        val ids = root?.packageName?.toString()?.let { URL_FIELDS[it] }
        ids?.firstNotNullOfOrNull { id ->
            root.findAccessibilityNodeInfosByViewId(id).firstNotNullOfOrNull { node ->
                node.text?.toString()?.trim()?.let(::normalizeAddress)
            }
        }
    } catch (_: Exception) { null }
}

/** Aceita só algo com cara de endereço (sem espaços, com domínio); acrescenta https:// quando falta o esquema. */
fun normalizeAddress(raw: String): String? {
    val t = raw.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() } || '.' !in t) return null
    val withScheme = if (Regex("(?i)^https?://").containsMatchIn(t)) t else "https://$t"
    return withScheme.takeIf { Regex("(?i)^https?://[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}(?:[/?#:]\\S*)?$").matches(it) }
}

/** Endereço lido no toque do pop-up, entregue uma única vez à análise da captura que vem a seguir. */
object PageUrlHolder {
    private const val MAX_AGE_MS = 2 * 60_000L
    @Volatile private var url: String? = null
    @Volatile private var at = 0L

    fun remember(value: String?) { url = value; at = SystemClock.elapsedRealtime() }

    /** Endereço da captura em análise; consumido uma vez quando o contexto da matéria é montado. */
    @Volatile var forAnalysis: String? = null

    fun takeForAnalysis(): String? = forAnalysis.also { forAnalysis = null }

    fun take(): String? {
        val v = url
        url = null
        return v?.takeIf { SystemClock.elapsedRealtime() - at <= MAX_AGE_MS }
    }
}
