package com.example.lumeocrtest

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lumeocrtest.ocr.ArticleReader
import com.example.lumeocrtest.ocr.toOcrBlocks
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrGeneratedLayoutsTest {
    private fun image(width: Int, height: Int, draw: Canvas.(Paint) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = android.graphics.Typeface.DEFAULT }
        canvas.draw(paint)
        return bitmap
    }

    private fun Canvas.line(paint: Paint, text: String, x: Float, y: Float, size: Float) {
        paint.textSize = size
        drawText(text, x, y, paint)
    }

    private fun analyze(bitmap: Bitmap): Triple<String?, String?, String> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
            val blocks = result.toOcrBlocks()
            val reading = ArticleReader().read(blocks, bitmap.width, bitmap.height)
            return Triple(reading.title, reading.metadata.author, reading.body)
        } finally { recognizer.close(); bitmap.recycle() }
    }

    @Test fun desktopTwoColumnsDoNotMergeRecommendations() {
        val bitmap = image(1120, 1250) { p ->
            line(p, "Jornal Horizonte", 55f, 68f, 28f)
            line(p, "Câmara aprova plano", 55f, 160f, 49f)
            line(p, "de transporte escolar", 55f, 220f, 49f)
            line(p, "Por Ana Souza", 55f, 355f, 28f)
            line(p, "Fonte: Jornal Horizonte", 55f, 395f, 28f)
            line(p, "Publicado em 14/06/2026", 55f, 435f, 27f)
            line(p, "Os vereadores aprovaram o plano de", 55f, 570f, 28f)
            line(p, "transporte para alunos da rede pública.", 55f, 610f, 28f)
            line(p, "Mais lidas", 755f, 100f, 28f)
            line(p, "Time contrata novo", 755f, 155f, 29f)
            line(p, "atacante", 755f, 195f, 29f)
            line(p, "Autor: Carlos Silva", 755f, 250f, 26f)
        }
        val (title, author, body) = analyze(bitmap)
        assertTrue(title.orEmpty().contains("Câmara aprova plano"))
        assertTrue(author.orEmpty().contains("Ana Souza"))
        assertTrue(body.contains("vereadores"))
        assertFalse(body.contains("atacante"))
        assertFalse(body.contains("Carlos Silva"))
    }

    @Test fun mobileRecommendationIsNotPartOfArticle() {
        val bitmap = image(720, 1350) { p ->
            line(p, "Portal Cidade", 35f, 70f, 28f)
            line(p, "Hospital amplia atendimento", 35f, 175f, 43f)
            line(p, "infantil nesta semana", 35f, 230f, 43f)
            line(p, "Por Beatriz Lima", 35f, 330f, 27f)
            line(p, "Publicado em 16/06/2026", 35f, 370f, 27f)
            line(p, "A unidade abriu novos horários para", 35f, 540f, 29f)
            line(p, "as famílias da região central.", 35f, 582f, 29f)
            line(p, "Leia também", 35f, 740f, 28f)
            line(p, "Clube anuncia novo atacante", 35f, 800f, 32f)
            line(p, "para a próxima temporada", 35f, 840f, 32f)
        }
        val (title, author, body) = analyze(bitmap)
        assertTrue(title.orEmpty().contains("Hospital amplia atendimento"))
        assertTrue(author.orEmpty().contains("Beatriz Lima"))
        assertTrue(body.contains("famílias"))
        assertFalse(body.contains("atacante"))
    }
}
