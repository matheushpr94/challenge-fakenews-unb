package com.example.lumeocrtest

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Grava a saída real do ML Kit (blocos, linhas, posições e confiança) de cada captura em
 * `androidTest/assets`. Os arquivos gerados viram fixtures dos testes JVM em `src/test/resources/ocr`,
 * para que a extração seja reproduzida sem emulador. Não faz asserções sobre o conteúdo.
 */
@RunWith(AndroidJUnit4::class)
class OcrFixtureDumpTest {
    @Test fun dumpOcrOfAllCaptures() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val outDir = File(instrumentation.targetContext.getExternalFilesDir(null), "ocr-fixtures").apply { mkdirs() }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val names = assets.list("").orEmpty().filter { it.endsWith(".png") || it.endsWith(".jpg") }
        try {
            for (name in names) {
                val bitmap = assets.open(name).use(BitmapFactory::decodeStream)
                val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
                val blocks = JSONArray()
                result.textBlocks.forEach { b ->
                    val lines = JSONArray()
                    b.lines.forEach { l ->
                        lines.put(JSONObject().put("text", l.text).put("box", box(l.boundingBox))
                            .put("confidence", l.confidence.toDouble()))
                    }
                    blocks.put(JSONObject().put("text", b.text).put("box", box(b.boundingBox)).put("lines", lines))
                }
                val json = JSONObject().put("image", name).put("width", bitmap.width).put("height", bitmap.height)
                    .put("blocks", blocks)
                File(outDir, name.substringBeforeLast('.') + ".json").writeText(json.toString(1))
                Log.i("LumeOcrFixture", "$name: ${result.textBlocks.size} blocos")
                bitmap.recycle()
            }
        } finally { recognizer.close() }
        assertTrue(names.isNotEmpty())
    }

    private fun box(r: android.graphics.Rect?) = r?.let { JSONArray(listOf(it.left, it.top, it.right, it.bottom)) }
}
