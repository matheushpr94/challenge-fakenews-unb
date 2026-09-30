package com.example.lumeocrtest

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lumeocrtest.ocr.ArticleReader
import com.example.lumeocrtest.ocr.ArticleReading
import com.example.lumeocrtest.ocr.ClaimChoice
import com.example.lumeocrtest.ocr.toOcrBlocks
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Capturas reais passando pelo ML Kit do aparelho e pelo mesmo leitor usado no app. */
@RunWith(AndroidJUnit4::class)
class OcrArticleRegressionTest {
    private fun read(asset: String): Pair<ArticleReading, ClaimChoice> {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open(asset).use(BitmapFactory::decodeStream)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val blocks = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).toOcrBlocks()
            val reader = ArticleReader()
            val reading = reader.read(blocks, bitmap.width, bitmap.height)
            return reading to reader.claimFor(reading)
        } finally { recognizer.close(); bitmap.recycle() }
    }

    @Test fun uolExplicitAuthorAndEstadaoMasthead() {
        // "Igor Siqueira • Do UOL, no Rio de Janeiro": autor individual e veículo, mesmo com o ponto separador perdido no OCR.
        val (uol, uolClaim) = read("uol-flamengo-stf.png")
        assertEquals("Igor Siqueira", uol.metadata.author)
        assertEquals("UOL", uol.metadata.source)
        assertTrue(uol.metadata.publishedAt.orEmpty().startsWith("29/09/2026"))
        assertEquals("Flamengo aciona STF e reforça ação das bets contra proibição do governo", uolClaim.claim)
        // Estadão: sem assinatura na captura; o nome vem do topo, confirmado pela trilha "Estadão / Esportes / Futebol".
        val (est, _) = read("estadao-flamengo-stf.png")
        assertEquals("Estadão", est.metadata.source)
        assertNull(est.metadata.author)
        assertTrue(est.title.orEmpty().startsWith("Flamengo é o primeiro clube"))
    }

    @Test fun bbcScreenshotKeepsArticleAndByline() {
        val (r, c) = read("bbc-fux-article.png")
        assertEquals("Pedro Martins", r.metadata.author)
        assertEquals("BBC News Brasil", r.metadata.source)
        assertTrue(r.metadata.publishedAt.orEmpty().contains("29 setembro 2026"))
        assertTrue(r.body.contains("eleiç"))
        assertFalse(r.body.contains("Piketty"))
        assertFalse(r.body.contains("GETTY"))
        assertFalse(r.body.contains("Padroeira do Bra pelo Vaticano"))
        assertEquals("Fux derruba decisão de Dino sobre posts de Nossa Senhora Aparecida", c.claim)
    }

    @Test fun senadoInstitutionalBylineAndPhotoCredit() {
        val (r, c) = read("agencia-senado-bets.png")
        assertEquals("MP proíbe bets e determina encerramento das operações em 30 dias", c.claim)
        assertEquals("Agência Senado", r.metadata.source)
        assertEquals("Agência Senado", r.metadata.institutionalByline)
        assertNull(r.metadata.author)
        assertTrue(r.metadata.publishedAt.orEmpty().startsWith("28/09/2026"))
        assertTrue(r.metadata.imageCredits.contains("Anna Tolipova"))
        assertFalse(r.body.contains("Proposições"))
        assertFalse(r.body.contains("Anna Tolipova"))
        assertEquals(com.example.lumeocrtest.ocr.BlockRole.LATERAL,
            r.decisions.first { it.text.startsWith("Proposições") }.role)
    }

    @Test fun mobileCapturesFromOtherOutlets() {
        val (ab, abClaim) = read("agenciabrasil-bets.png")
        assertEquals("Marcelo Brandão", ab.metadata.author)
        assertEquals("Agência Brasil", ab.metadata.source)
        assertEquals("Bets passam a ser proibidas no país e apostador receberá saldo", abClaim.claim)
        val (cam, _) = read("camara-bets-pl.png")
        assertTrue(cam.title.orEmpty().startsWith("Projeto de lei"))
        assertEquals("Agência Câmara", cam.metadata.institutionalByline)
        val (g1, g1Claim) = read("g1-inadimplencia.png")
        assertEquals("Alexandro Martello", g1.metadata.author)
        assertEquals("g1", g1.metadata.source)
        assertTrue(g1Claim.claim.orEmpty().startsWith("Em meio à corrida eleitoral"))
    }

    @Test fun partialCapturesDoNotInventTitle() {
        val (cut, cutClaim) = read("g1-inadimplencia-corpo.png")
        assertNull(cut.title); assertTrue(cutClaim.needsChoice)
        val (crop, cropClaim) = read("recorte-senado-sem-titulo.png")
        assertNull(crop.title); assertNull(crop.metadata.source); assertNull(crop.metadata.author)
        assertTrue(cropClaim.claim.orEmpty().startsWith("Começou a tramitar"))
    }
}
