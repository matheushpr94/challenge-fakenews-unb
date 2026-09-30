package com.example.lumeocrtest

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Ferramenta (não é teste de regressão): renderiza uma página pública num WebView com a largura do
 * aparelho e salva o que caberia na tela, como uma captura feita pelo usuário. Só roda quando recebe
 * `-e captureUrl <url> -e captureName <nome> [-e captureScroll <px>]`.
 */
@RunWith(AndroidJUnit4::class)
class WebCaptureTool {
    @Test fun capture() {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("captureUrl") ?: return // sem argumento: nada a capturar
        val name = args.getString("captureName") ?: "captura"
        val scroll = args.getString("captureScroll")?.toIntOrNull() ?: 0
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val metrics = context.resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val loaded = CountDownLatch(1)
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            WebView.enableSlowWholeDocumentDraw()
            web = WebView(context)
            web.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, u: String) { loaded.countDown() }
            }
            web.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            web.layout(0, 0, w, h)
            web.loadUrl(url)
        }
        loaded.await(40, TimeUnit.SECONDS)
        Thread.sleep(7000)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync {
            web.scrollTo(0, scroll)
            val canvas = Canvas(bitmap)
            canvas.translate(0f, -scroll.toFloat())
            web.draw(canvas)
        }
        val out = File(context.getExternalFilesDir(null), "captures").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        instrumentation.runOnMainSync { web.destroy() }
    }
}
