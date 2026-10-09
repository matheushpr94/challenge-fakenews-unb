package com.example.lumeocrtest.research

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/*
 * Fatos diferentes que compartilham pessoas, instituição e verbos ("manda retirar" × "manda apagar") não podem virar
 * "mesmo acontecimento" só por isso; e palavras achadas no corpo da página (antecedentes) não fazem a matéria relatar o fato.
 * Nomes fictícios.
 */
class SharedEntitiesTests {
    private val claim = "Tribunal Eleitoral manda retirar publicações de Ana Prado, Rui Costa e aliados contra Paulo Lima"

    private fun cmp(title: String, snippet: String = ""): EventComparison {
        val interp = interpret(claim, NOW, null)
        val o = originalEvent(interp.assunto, null, NOW)
        val c = Candidate(title = title, url = "https://a.example.com/" + title.hashCode(), sourceName = "Jornal A",
            origin = "google_news", published = NOW - 3_600_000L, snippet = snippet)
        return compareEvent(o, c, NOW, null)
    }

    @Test fun orderByAnAuthorityIsAnAction() {
        val o = originalEvent(interpret(claim, NOW, null).assunto, null, NOW)
        assertEquals("“manda retirar” é uma decisão", "decidir", o.frame.action)
    }

    @Test fun sameAuthorityAndPersonButOtherOrderIsNotTheSameEvent() {
        // Outra ordem (outro alvo, outro conteúdo a remover) do mesmo tribunal, citando uma das mesmas pessoas.
        val r = cmp("Tribunal Eleitoral manda rede social apagar montagem de revista com foto de Ana Prado")
        assertNotEquals(EventRelation.MESMO, r.kind)
        assertNotEquals(EventRelation.DIVERGENTE, r.kind)
    }

    @Test fun sameOrderWithSameWordsIsStillTheSameEvent() {
        val r = cmp("Tribunal Eleitoral manda retirar publicações de Ana Prado e aliados contra Paulo Lima")
        assertEquals(EventRelation.MESMO, r.kind)
    }

    @Test fun backgroundParagraphInThePageDoesNotPromoteTheSource() {
        // Afirmação sem ação reconhecida (triagem por palavras). A fonte trata de outro assunto; o corpo da página cita o
        // fato pesquisado só como antecedente.
        val q = "Prêmio Aurora 2026 para Lia Mendes, jurista de Vale Claro"
        val other = "https://b.example.com/reforma"
        val same = "https://c.example.com/premio"
        val web = FakeWeb(
            bingNews = listOf(
                News("Lia Mendes, jurista de Vale Claro, critica reforma do tribunal", "Jornal B", other, NOW - 3_600_000L,
                    domain = "b.example.com"),
                News("Lia Mendes, jurista de Vale Claro, recebe o Prêmio Aurora 2026", "Jornal C", same, NOW - 3_600_000L,
                    domain = "c.example.com"),
            ),
            pages = mapOf(
                other to htmlPage(listOf(
                    "A jurista Lia Mendes comentou nesta segunda a reforma do tribunal de Vale Claro e criticou o prazo das obras.",
                    "Segundo ela, a reforma do tribunal deveria ter sido discutida com os servidores antes de começar.",
                    "A obra no tribunal de Vale Claro deve durar dois anos e custar mais do que o previsto no edital.",
                    "Em outro momento da carreira, Lia Mendes foi escolhida para o Prêmio Aurora 2026, entregue a juristas de Vale Claro.")),
                same to htmlPage(listOf(
                    "A jurista Lia Mendes, de Vale Claro, recebeu o Prêmio Aurora 2026, anunciou o comitê nesta segunda.",
                    "O prêmio é entregue todos os anos a juristas com atuação em direitos humanos.")),
            ),
        )
        val r = runBlocking { ResearchService(fetcher = web, clock = { NOW }).search(q, "t", null) }
        val cards = r.resultados.values.flatten()
        val trace = r.diagnostico!!.report()
        assertNotEquals("antecedente no corpo não vira mesmo acontecimento\n$trace", RelationKind.MESMO_FATO,
            cards.firstOrNull { it.url == other }?.relacaoTipo)
        assertEquals("abertura da página que relata o fato confirma\n$trace", RelationKind.MESMO_FATO,
            cards.firstOrNull { it.url == same }?.relacaoTipo)
    }
}
