package com.example.lumeocrtest.research

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Post com chamada ("URGENTE -"), material citado ("Imagens mostram como foi…"), erro de leitura ("foio") e atribuição
 * ("…, diz Jornal X"): as consultas preservam os envolvidos e o fato, e a comparação reconhece outras redações do mesmo
 * fato sem aceitar só nomes em comum. Nomes fictícios.
 */
class PostClaimRetrievalTests {
    private val raw = "URGENTE - Imagens mostram como foio jantar 'de gala' organizado por Lestan para Ardeo, diz O Correio"
    private val post = ArticleContext(body = raw, publishedAtMs = NOW)
    private val event = originalEvent(interpret(raw, NOW, post).assunto, post, NOW)

    private fun cand(title: String, date: Long? = NOW - Dates.DAY_MS, snippet: String = "") =
        Candidate(title, "https://jornal.example.com/" + title.hashCode(), "Jornal", "google_news", published = date, snippet = snippet)
    private fun kind(title: String, date: Long? = NOW - Dates.DAY_MS) = compareEvent(event, cand(title, date), NOW).kind

    // ---- consultas ---------------------------------------------------------------------------

    @Test fun queriesKeepParticipantsAndFactWithoutMarkerOrOcrNoise() {
        val i = interpret(raw, NOW, post)
        val queries = i.consultas.map { it.texto }
        assertTrue("os dois envolvidos e o fato numa consulta curta: $queries", queries.any { norm(it) == "lestan ardeo jantar" })
        assertTrue("as duas primeiras consultas vão aos dois buscadores e trazem os envolvidos: $queries",
            queries.take(2).all { "Lestan" in it && "Ardeo" in it && "jantar" in it })
        for (bad in listOf("urgente", "foio", "imagens", "mostram")) assertFalse("“$bad” em $queries", queries.any { bad in norm(it).split(" ") })
        assertEquals("imagens", i.materialCitado)
        assertFalse(i.assunto.startsWith("URGENTE"))
    }

    @Test fun cleanupOnlyTouchesMarkersAndFusedWords() {
        assertEquals("Torvana aprova regra para os vinhos", cleanInput("BOMBA: Torvana aprova regra parao s vinhos".replace("parao s", "paraos")))
        assertEquals("STF: ministros decidem sobre a regra", cleanInput("STF: ministros decidem sobre a regra"))
        assertEquals("O clube quer comprar a sede", cleanInput("O clube quer comprar a sede"))
        assertEquals("Vídeo mostra Lestan", cleanInput("Vídeo mostra Lestan"))
        assertEquals("Mensagens revelam troca de informações entre Lestan e Ardeo", cleanInput("Mensagens revelam troca de informações entre Lestan e Ardeo"))
    }

    // ---- comparação do acontecimento ---------------------------------------------------------

    @Test fun claimFrameIsTheFactNotTheAttribution() {
        assertEquals("organizar", event.frame.action)
        assertEquals(setOf("lestan"), event.agentNames)
        assertEquals(setOf("correio"), event.attribution)
        assertTrue("jantar" in event.frame.eventNoun)
    }

    @Test fun otherWordingsOfTheSameEventAreRecognized() {
        for (t in listOf(
            "Lestan organizou jantar para Ardeo em hotel de luxo em Valdória",
            "Jantar de Lestan para Ardeo teve vinhos raros e chef premiado",
            "Ardeo participou de jantar organizado por Lestan, mostram imagens",
            "Veja fotos do jantar 'de gala' de Lestan com Ardeo",
        )) assertEquals(t, EventRelation.MESMO, kind(t))
    }

    @Test fun samePeopleInAnotherEventAreNotTheSameEvent() {
        for (t in listOf(
            "'Estamos juntos': as mensagens de Lestan sobre Ardeo e outros ministros",
            "Lestan e Ardeo são citados em relatório sobre contratos do banco",
            "Ardeo decide manter prisão de Lestan",
        )) assertNotEquals(t, EventRelation.MESMO, kind(t))
    }

    @Test fun sameSubjectWithOnlyOneParticipantIsNotEnough() {
        assertNotEquals(EventRelation.MESMO, kind("Jantar de Lestan reúne empresários em Valdória"))
        assertNotEquals(EventRelation.MESMO, kind("Ardeo vai a jantar de formatura em Torvana"))
    }

    @Test fun followUpAboutTheEventIsContextNotTheEventItself() {
        val k = kind("Conselho investiga jantar de Lestan para Ardeo")
        assertTrue(k, k == EventRelation.CONTEXTO || k == EventRelation.INCERTA)
    }

    @Test fun denialIsSameEventWithOppositeSense() {
        assertEquals(EventRelation.DIVERGENTE, kind("Ardeo nega ter participado de jantar organizado por Lestan"))
    }

    @Test fun olderReportOfASimilarEventIsPreviousContext() {
        val cmp = compareEvent(event, cand("Lestan organizou jantar para Ardeo em hotel de luxo", NOW - 200 * Dates.DAY_MS), NOW)
        assertEquals(EventRelation.CONTEXTO, cmp.kind)
        assertTrue(cmp.anterior)
    }

    @Test fun currentReportThatDatesThePastFactIsStillTheSameEvent() {
        // Publicada na mesma época do post, a fonte diz quando o jantar ocorreu; o post não diz.
        val c = cand("Lestan organizou jantar para Ardeo em hotel de luxo", NOW - Dates.DAY_MS,
            "Lestan organizou jantar para Ardeo em dezembro de 2023, mostram mensagens.")
        val cmp = compareEvent(event, c, NOW)
        assertEquals(EventRelation.MESMO, cmp.kind)
        assertFalse(cmp.anterior)
        assertTrue(cmp.motivos.any { "mesma época" in it })
    }

    // ---- fluxo completo ----------------------------------------------------------------------

    @Test fun searchFindsSameEventAndDoesNotTreatItAsProofOfTheImages() {
        val same = News("Lestan organizou jantar para Ardeo em hotel de luxo em Valdória", "Gazeta", "https://gazeta.example.com/jantar", NOW - Dates.DAY_MS,
            "O banqueiro Lestan organizou um jantar para o ministro Ardeo.", "gazeta.example.com")
        val other = News("As mensagens de Lestan sobre Ardeo e outros ministros", "Diário", "https://diario.example.com/mensagens", NOW - Dates.DAY_MS,
            "Mensagens citam ministros.", "diario.example.com")
        val web = FakeWeb(google = listOf(same, other), bingNews = listOf(same, other))
        val r = runBlocking { ResearchService(fetcher = web, clock = { NOW }, fetchPages = false).search(raw, "t", post) }
        val cards = r.resultados
        assertTrue(cards.getValue("direto").any { it.url == same.url && it.relacaoTipo == RelationKind.MESMO_FATO })
        assertFalse("mesmas pessoas, outro fato", cards.getValue("direto").any { it.url == other.url })
        assertTrue(r.observacoes.any { "não avalia se esse material é autêntico" in it })
        assertTrue(r.observacoes.any { "atribui a informação a “O Correio”" in it })
        // A consulta que chegou aos buscadores tem os envolvidos e o fato.
        assertTrue(web.calls.any { "Lestan" in it && "Ardeo" in it && "jantar" in it })
        assertFalse(web.calls.any { "URGENTE" in it || "foio" in it })
    }
}
