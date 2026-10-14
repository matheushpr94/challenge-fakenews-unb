package com.example.lumeocrtest.ocr

import com.example.lumeocrtest.sameStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Capturas reais (ML Kit, emulador API 37, 07/10/2026) da matéria da CNN Brasil "RJ: 204 mil eleitores votaram "13"...".
 * `cnn-rj-votos-nulos`: sem sobreposição; o OCR leu "2044" na manchete. `cnn-rj-votos-nulos-aviso`: o aviso de notificações do site cobre
 * o começo da manchete e o OCR leu "5" no lugar de "13" (a leitura vem com confiança menor).
 */
class CnnCaptureRegressionTest {
    private val reader = ArticleReader()
    private val headline = "eleitores votaram \"13\" para governador e anularam seus votos"

    /** Como o app: o conversor do ML Kit aplica a correção de maiúsculas no meio da palavra antes da leitura por papéis. */
    private fun read(name: String): ArticleReading {
        val f = OcrFixtures.load(name)
        val blocks = f.blocks.map { b ->
            b.copy(text = OcrTextRepair.repairCase(b.text), lines = b.lines.map(OcrTextRepair::repairCase),
                lineDetails = b.lineDetails.map { it.copy(text = OcrTextRepair.repairCase(it.text)) })
        }
        return reader.read(blocks, f.width, f.height)
    }

    @Test fun cleanCaptureReadsVehicleAuthorsDateAndPlaceOnce() {
        val r = read("cnn-rj-votos-nulos")
        assertEquals("CNN Brasil", r.metadata.source)
        assertEquals("Jonatas Martins e Murillo Ferrari", r.metadata.author)
        assertEquals("06/10/26 às 16:18", r.metadata.publishedAt)
        assertTrue("publicado e atualizado iguais: aparece uma vez", sameStamp(r.metadata.updated, r.metadata.publishedAt))
        assertEquals("Brasília e São Paulo", r.metadata.place)
        assertTrue(r.subtitle!!.startsWith("O número 13, associado ao PT"))        // "asSociado" foi corrigido
        assertFalse(r.subtitle!!.contains("asSociado"))
    }

    @Test fun clampedExtraDigitIsFixedByTheEnlargedReread() {
        val r = read("cnn-rj-votos-nulos")
        assertTrue("o OCR leu 2044 (defeito real que motivou o teste)", r.title!!.contains("2044 mil"))
        val ok = OcrIntegrity.verify(r, rereads = listOf("RJ: 204 mil $headline", "RJ: 204 mil $headline"))
        assertTrue(ok.title!!.startsWith("RJ: 204 mil"))
        val choice = reader.claimFor(ok)
        assertTrue(choice.claim!!.startsWith("RJ: 204 mil"))
        assertTrue(choice.note!!.contains("Atenção") && choice.note!!.contains("2044"))
    }

    @Test fun withoutRereadTheNumberIsStillCheckedAgainstTheText() {
        val ok = OcrIntegrity.verify(read("cnn-rj-votos-nulos"))
        assertTrue("corrige ou, no mínimo, avisa", ok.title!!.contains("204 mil") || ok.integrityNotes.isNotEmpty())
    }

    @Test fun coveredHeadlineGetsALowConfidenceWarning() {
        val r = read("cnn-rj-votos-nulos-aviso")
        assertTrue("manchete lida com o aviso por cima: \"5\"", r.title!!.contains("\"5\""))
        assertNotNull(r.titleConfidence)
        val ok = OcrIntegrity.verify(r)
        assertTrue("a baixa confiança do OCR é sinalizada", ok.integrityNotes.any { it.contains("baixa confiança") || it.contains("não aparece") })
        val note = reader.claimFor(ok).note!!
        assertTrue(note.contains("Atenção"))
        assertEquals("CNN Brasil", r.metadata.source)
        assertEquals("Brasília e São Paulo", r.metadata.place)
        assertEquals("06/10/26 às 16:18", r.metadata.publishedAt)
        assertTrue(r.metadata.author!!.startsWith("Jonatas") && r.metadata.author!!.endsWith("Ferrari"))   // o OCR colou "Martinse": só confere as pontas
    }
}
