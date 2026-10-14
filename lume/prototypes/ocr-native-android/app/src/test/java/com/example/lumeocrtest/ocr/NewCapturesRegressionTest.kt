package com.example.lumeocrtest.ocr

import com.example.lumeocrtest.sameStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Capturas reais de 30/09 e 07/10/2026 (ML Kit, emulador API 37) que revelaram erros de leitura da autoria, da data e do veículo. */
class NewCapturesRegressionTest {
    private val reader = ArticleReader()
    private fun read(name: String): ArticleReading {
        val f = OcrFixtures.load(name)
        val blocks = f.blocks.map { b ->
            b.copy(text = OcrTextRepair.repairCase(b.text), lines = b.lines.map(OcrTextRepair::repairCase),
                lineDetails = b.lineDetails.map { it.copy(text = OcrTextRepair.repairCase(it.text)) })
        }
        return reader.read(blocks, f.width, f.height)
    }

    @Test fun cnnCollaboratorAndCityAreNotTheVehicle() {
        val r = read("cnn-lula-toque")
        assertEquals("CNN Brasil", r.metadata.source)            // antes: "a CNN Brasil, São Paulo"
        assertEquals("Pedro Penteado", r.metadata.author)          // antes: não identificado
        assertEquals("São Paulo", r.metadata.place)
        assertTrue(sameStamp(r.metadata.updated, r.metadata.publishedAt))
        assertTrue(r.title!!.startsWith("Lula diz que falou de coragem feminina"))
    }

    @Test fun labelledPublicationAndUpdateDatesAreSplit() {
        val r = read("ifsc-cloroquina")
        assertEquals("09 jun 2020 14:23", r.metadata.publishedAt)  // antes: a linha inteira ("IFSC VERIFICA Data de Publicação: ...")
        assertEquals("18 fev 2021 17:49", r.metadata.updated)
        assertTrue(r.metadata.source!!.contains("Verifica", ignoreCase = true))
        val c = reader.claimFor(r)
        assertFalse(c.needsChoice)
        assertTrue("título em forma de pergunta: sugere a frase que abre o texto", c.claim!!.startsWith("Um dos assuntos mais polêmicos"))
    }

    @Test fun editorialBylineKeepsTheVehicleSeparate() {
        val r = read("g1-lula-debate-globo")
        assertEquals("g1", r.metadata.source)                       // antes: "Redação g1"
        assertEquals("Redação g1", r.metadata.institutionalByline)
        assertNull(r.metadata.author)
        assertEquals("30/09/2026 13h14", r.metadata.publishedAt); assertEquals("há 28 minutos", r.metadata.updated)
    }

    @Test fun timeWithLetterOAndSpelledOutRelativeUpdate() {
        val r = read("g1-flavio-constituicao")
        assertEquals("07/10/2026 10h15", r.metadata.publishedAt)    // antes: "1Oh15"
        assertEquals("há um minuto", r.metadata.updated)            // antes: perdido
        assertTrue(r.title!!.startsWith("Flávio diz que fala sobre 'mudar a Constituição'"))
    }

    @Test fun otherCapturesKeepWorking() {
        val i = read("itatiaia-flamengo-stf")
        assertEquals("Itatiaia", i.metadata.source); assertEquals("Felippe Rocha e Wellington Campos", i.metadata.author)
        assertEquals("30/09/26 às 11:18", i.metadata.publishedAt); assertEquals("Flamengo entra com ação no STF contra o fim das bets", i.title)
        val p = read("boato-papa-francisco")
        assertEquals("Edgard Matsuki", p.metadata.author); assertEquals("24/02/2025", p.metadata.publishedAt)
        assertTrue(reader.claimFor(p).claim!!.startsWith("Alegação de que papa Francisco"))
    }
}
