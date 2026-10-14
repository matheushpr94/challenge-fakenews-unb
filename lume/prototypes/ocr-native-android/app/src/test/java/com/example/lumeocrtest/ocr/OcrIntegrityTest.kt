package com.example.lumeocrtest.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OcrIntegrityTest {
    private val body = "Mais de 204 mil eleitores apertaram \"13\" nas urnas eletrônicas ao escolher o governador no Rio de Janeiro no último domingo (4)."

    // ---- maiúscula no meio da palavra ----
    @Test fun repairsMidWordCapitals() {
        assertEquals("O número 13, associado ao PT, do governador", OcrTextRepair.repairCase("O número 13, asSociado ao PT, do governador"))
        assertEquals("a letra extra do OCR vira minúscula, mas não é removida sem dicionário", "goovernador", OcrTextRepair.repairCase("goOvernador"))
    }
    @Test fun repairsCapitalRightAfterFirstLetter() {
        assertEquals("cargo de governador do Rio", OcrTextRepair.repairCase("cargo de gOvernador do Rio"))
    }
    @Test fun keepsBrandsAcronymsHandlesAndLinks() {
        val keep = "O iPhone e o iFood do STF e da CNN, veja @joaoSilva e www.siteNovo.com ou https://xY.com/aBc #temaAtual"
        assertEquals(keep, OcrTextRepair.repairCase(keep))
        assertEquals("Flamengo", OcrTextRepair.repairCase("Flamengo"))
    }

    // ---- número da manchete ----
    @Test fun fixesExtraDigitUsingArticleText() {
        val f = NumberCheck.check("RJ: 2044 mil eleitores votaram \"13\" para governador", body)!!
        assertEquals("RJ: 204 mil eleitores votaram \"13\" para governador", f.corrected)
        assertTrue(f.note, f.note.contains("2044") && f.note.contains("204"))
    }
    @Test fun warnsWhenNumberIsNotInTheText() {
        val f = NumberCheck.check("RJ: 204 mil eleitores votaram \"5\" para governador", body)!!
        assertNull("não há candidato à distância 1: só avisa", f.corrected)
        assertTrue(f.note, f.note.contains("“5”"))
    }
    @Test fun noFalseAlarms() {
        assertNull(NumberCheck.check("RJ: 204 mil eleitores votaram \"13\" para governador", body))
        assertNull("ano não é conferido", NumberCheck.check("Eleições 2026 têm 204 mil nulos", body))
        assertNull("sem contexto suficiente não há o que comparar", NumberCheck.check("RJ: 2044 mil eleitores", "204 mil"))
        assertNull("ordinal", NumberCheck.check("O 2º turno teve 204 mil votos nulos no Rio", body))
        assertNull("decimal com vírgula", NumberCheck.check("Nulos chegam a 30,25% no Rio", "$body Representa 30,25% de todos os votos nulos."))
    }
    @Test fun neverRewritesLegitimateDifferentNumber() {
        val f = NumberCheck.check("RJ: 205 mil eleitores votaram em branco", body)!!
        assertNull("204 e 205 podem ser números diferentes de verdade: só avisa", f.corrected)
        assertTrue(f.note, f.note.contains("“204”"))
    }
    @Test fun ordinalDoesNotCountAsNumber() {
        assertNull(NumberCheck.check("Saiba o que vale no 1° turno das eleições para governador", body))
    }
    @Test fun doesNotCorrectWhenUnitDiffers() {
        val f = NumberCheck.check("RJ: 2044 pessoas votaram em branco", body)
        assertNull(f?.corrected)
    }

    // ---- voto entre leituras ----
    @Test fun voteAdoptsMajorityOfEnlargedReads() {
        val v = NumberVote.resolve("RJ: 2044 mil eleitores votaram \"13\"", listOf("RJ: 204 mil eleitores votaram \"13\"", "RJ: 204 mil eleitores votaram 13"))
        assertEquals("RJ: 204 mil eleitores votaram \"13\"", v.title)
        assertNotNull(v.note)
    }
    @Test fun voteFlagsDisagreementWithoutMajority() {
        val v = NumberVote.resolve("RJ: 2044 mil", listOf("RJ: 204 mil", "RJ: 2014 mil"))
        assertEquals("RJ: 2044 mil", v.title)
        assertTrue(v.note!!.contains("confira"))
    }
    @Test fun voteIgnoresReadsWithDifferentNumberCount() {
        val v = NumberVote.resolve("RJ: 204 mil eleitores", listOf("RJ: 204 mil eleitores 06/10/26 16:18", "204 mil eleitores"))
        assertEquals("RJ: 204 mil eleitores", v.title)
        assertNull(v.note)
    }
    @Test fun voteKeepsTitleWhenAllAgree() {
        assertNull(NumberVote.resolve("RJ: 204 mil", listOf("RJ: 204 mil", "RJ: 204 mil")).note)
    }

    // ---- verify() ponta a ponta e capturas reais ----
    private fun reading(title: String, sub: String? = null, conf: Float? = null) =
        ArticleReading(title, null, sub, ArticleMetadata(), body, emptyList(), titleConfidence = conf)

    @Test fun verifyCorrectsTitleAndExposesNoteInClaim() {
        val r = OcrIntegrity.verify(reading("RJ: 2044 mil eleitores votaram \"13\" para governador"))
        assertEquals("RJ: 204 mil eleitores votaram \"13\" para governador", r.title)
        val c = ArticleReader().claimFor(r)
        assertEquals(r.title, c.claim)
        assertTrue(c.note!!.contains("Atenção"))
    }
    @Test fun verifyWarnsOnLowTitleConfidence() {
        val r = OcrIntegrity.verify(reading("Governo anuncia pacote de investimentos em obras", conf = 0.4f))
        assertTrue(r.integrityNotes.single().contains("baixa confiança"))
    }
    @Test fun realCapturesProduceNoFalseAlarms() {
        val dir = File(javaClass.classLoader!!.getResource("ocr")!!.toURI())
        val names = dir.listFiles { f -> f.name.endsWith(".json") }!!.map { it.nameWithoutExtension }.sorted()
        val bad = mutableListOf<String>()
        for (n in names) {
            if (n.startsWith("cnn-rj-votos-nulos")) continue   // erros reais de leitura de propósito: têm teste próprio (CnnCaptureRegressionTest)
            val f = runCatching { OcrFixtures.load(n) }.getOrNull() ?: continue
            val r0 = ArticleReader().read(f.blocks, f.width, f.height)
            val r = OcrIntegrity.verify(r0)
            println("FIXTURE $n título=${r0.title?.take(50)} conf=${r0.titleConfidence}")
            if (r.integrityNotes.isNotEmpty() || r.title != r0.title) bad += "$n: ${r.integrityNotes}"
        }
        assertTrue("avisos em capturas reais corretas: $bad", bad.isEmpty())
    }
}
