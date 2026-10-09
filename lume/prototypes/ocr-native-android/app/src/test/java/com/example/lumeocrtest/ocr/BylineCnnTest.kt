package com.example.lumeocrtest.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Linhas de autoria e data como na captura da CNN Brasil (sem "Por"; "Publicado" e "Atualizado" iguais). */
class BylineCnnTest {
    @Test fun twoAuthorsOrganisationAndPlacesWithoutBy() {
        val p = BylineParser.parse("Jonatas Martins e Murillo Ferrari, da CNN Brasil, Brasília e São Paulo")
        assertEquals("Jonatas Martins e Murillo Ferrari", p.author)
        assertEquals("CNN Brasil", p.organization)
        assertEquals("Brasília e São Paulo", p.place)
    }
    @Test fun placeLostALetterIsRepaired() {
        val p = BylineParser.parse("Jonatas Martins e Murillo Ferrari, da CNN Brasil, Braslia e São Paulo")
        assertEquals("Brasília e São Paulo", p.place)
        assertEquals("Fortaleza", BylineParser.fixPlace("Fortalza")); assertEquals("Marcelo", BylineParser.fixPlace("Marcelo"))
    }
    @Test fun oneAuthorAndOrganisation() {
        val p = BylineParser.parse("Ana Lima, da Agência Brasil")
        assertEquals("Ana Lima", p.author); assertEquals("Agência Brasil", p.organization)
    }
    @Test fun withByWordAndOrganisationTail() {
        val p = BylineParser.parse("Por Jonatas Martins e Murillo Ferrari, da CNN Brasil")
        assertEquals("Jonatas Martins e Murillo Ferrari", p.author); assertEquals("CNN Brasil", p.organization)
    }
    @Test fun publishedAndUpdatedStaySeparate() {
        val p = BylineParser.parse("06/10/26 às 16:18 | Atualizado 06/10/26 às 16:18")
        assertEquals("06/10/26 às 16:18", p.date); assertEquals("06/10/26 às 16:18", p.updated)
        val q = BylineParser.parse("06/10/26 às 16:18| Atualizado 06/10/26 às 17:40")
        assertEquals("06/10/26 às 16:18", q.date); assertEquals("06/10/26 às 17:40", q.updated)
    }
    @Test fun sameStampIsShownOnce() {
        assertTrue(com.example.lumeocrtest.sameStamp("06/10/26 às 16:18", "Atualizado 06/10/26 às 16:18"))
        assertTrue(!com.example.lumeocrtest.sameStamp("06/10/26 às 17:40", "06/10/26 às 16:18"))
    }
    @Test fun sentencesAndHeadlinesAreNotBylines() {
        for (s in listOf("Flávio Bolsonaro, do PL, diz que vai manter a decisão", "O ministro Alexandre de Moraes, do STF", "Moraes e Gilmar, da Corte", "Maria da Silva, de Brasília"))
            assertNull("não é autoria: $s", BylineParser.parse(s).author)
    }
}

/** Casos achados em capturas reais de 07/10/2026 (CNN com colaboração, IFSC Verifica, g1 com "Por Redação g1", hora com letra O). */
class BylineRealCapturesTest {
    @Test fun collaboratorWithOrganisationAndPlace() {
        val p = BylineParser.parse("Pedro Penteado, colaboração para a CNN Brasil, São Paulo")
        assertEquals("Pedro Penteado", p.author); assertEquals("CNN Brasil", p.organization); assertEquals("São Paulo", p.place)
    }
    @Test fun labelledPublicationAndUpdateDates() {
        val p = BylineParser.parse("IFSC VERIFICA Data de Publicação: 09 jun 2020 14:23 Data de Atualização: 18 fev 2021 17:49")
        assertEquals("09 jun 2020 14:23", p.date); assertEquals("18 fev 2021 17:49", p.updated)
        assertTrue(p.organization!!.contains("Verifica", ignoreCase = true))
    }
    @Test fun letterOForZeroAndSpelledOutRelativeTime() {
        val p = BylineParser.parse("07/10/2026 1Oh15 Atualizado há um minuto")
        assertEquals("07/10/2026 10h15", p.date); assertEquals("há um minuto", p.updated)
    }
}
