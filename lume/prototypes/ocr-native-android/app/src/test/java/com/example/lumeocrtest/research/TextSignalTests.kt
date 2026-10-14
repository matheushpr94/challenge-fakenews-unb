package com.example.lumeocrtest.research

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sinal do texto: leitura da resposta do servidor, guardas de entrada, agências de checagem e integração sem efeito colateral. */
class TextSignalTests {
    private val LONG = (1..80).joinToString(" ") { "palavra$it" }

    private val sample = """{"ok":true,"faixa":"desinformacao","regime":"normal","decisao":-1.2,"posicao":0.9,"palavras":120,
        "limites":{"boato":0.75,"noticia":0.4},"pesam_boato":[{"termo":"compartilhar","peso":-0.21},{"termo":"urgente","peso":-0.12}],
        "pesam_noticia":[],"ms":40}"""

    @Test fun parsesServerResponse() {
        val s = parseTextSignal(sample)!!
        assertEquals(SignalBand.DESINFORMACAO, s.faixa)
        assertEquals(0.9f, s.posicao, 1e-6f)
        assertEquals(0.75f, s.limiteBoato, 1e-6f)
        assertEquals(listOf("compartilhar", "urgente"), s.pesamBoato.map { it.termo })
    }

    @Test fun badResponsesBecomeNull() {
        assertNull(parseTextSignal("""{"ok":false,"motivo":"texto_curto"}"""))
        assertNull(parseTextSignal("não é json"))
        assertNull(parseTextSignal("""{"ok":true,"faixa":"outra","posicao":0.5}"""))
    }

    @Test fun shortOrTitleOnlyTextHasNoSignal() {
        assertNull(signalText(null, "MP proíbe rinhas e determina encerramento das arenas em 30 dias"))
        assertNull(signalText(ArticleContext(title = "Título", body = "curto"), "Título"))
        assertNull(signalText(null, "https://exemplo.com/noticia"))
    }

    @Test fun allCapsTextHasNoSignal() {
        assertNull(signalText(null, (1..80).joinToString(" ") { "PALAVRA$it" }))
    }

    @Test fun usesTheLongestOfArticleAndTypedText() {
        val ctx = ArticleContext(title = "Título da matéria", body = LONG)
        val t = signalText(ctx, "Título da matéria")!!
        assertTrue("usa o corpo da matéria lida", "palavra80" in t && t.startsWith("Título da matéria"))
        assertEquals(LONG, signalText(null, LONG))
    }

    @Test fun findsFactCheckAgenciesOncePerAgency() {
        fun card(url: String, title: String) = ResultCard(title, url, null, "X", null, null, "", "noticia", "", "", "", "", null,
            emptyList(), emptyList(), emptyList())
        val found = factChecksIn(listOf(
            card("https://www.aosfatos.org/noticias/x/", "Não é verdade"), card("https://aosfatos.org/outra", "Outra"),
            card("https://g1.globo.com/fato-ou-fake/noticia/y.ghtml", "É #FAKE"), card("https://g1.globo.com/politica/noticia/z.ghtml", "Notícia"),
        ))
        assertEquals(listOf("Aos Fatos", "Fato ou Fake (g1)"), found.map { it.agencia })
    }

    private fun web() = FakeWeb(google = listOf(
        News("MP proíbe rinhas e determina encerramento das arenas em 30 dias", "Jornal A", "https://a.example.com/1", d(2026, 9, 27), domain = "a.example.com"),
        News("Governo estuda proibir rinhas de galo", "Jornal B", "https://b.example.com/2", d(2026, 9, 27), domain = "b.example.com"),
    ))
    private val ctx = ArticleContext(title = "MP proíbe rinhas e determina encerramento das arenas em 30 dias", body = LONG, publishedAtMs = d(2026, 9, 28))
    private fun signature(e: Evaluation) = e.partes.flatMap { it.resultados.values.flatten() }.map { listOf(it.url, it.relacaoTipo, it.resumoRelacao, it.alertas) }

    @Test fun signalIsAttachedAndDoesNotChangeTheSources() {
        val scorer = TextSignalScorer { TextSignal(SignalBand.SEM_SINAL, 0.5f, 0.4f, 0.75f, 80, "normal") }
        val with = runBlocking { ResearchService(fetcher = web(), clock = { NOW }, textSignal = scorer).evaluate(ctx.title!!, "t", ctx) }
        val without = runBlocking { ResearchService(fetcher = web(), clock = { NOW }).evaluate(ctx.title!!, "t", ctx) }
        assertNotNull(with.sinalTexto)
        assertNull(without.sinalTexto)
        assertEquals(signature(without), signature(with))
        assertEquals(without.status, with.status)
    }

    @Test fun failingOrSlowScorerNeverBreaksOrDelaysTheSearch() {
        val boom = TextSignalScorer { throw IllegalStateException("servidor caiu") }
        val r1 = runBlocking { ResearchService(fetcher = web(), clock = { NOW }, textSignal = boom).evaluate(ctx.title!!, "t", ctx) }
        assertNull(r1.sinalTexto)
        assertTrue(r1.partes.isNotEmpty())
        val slow = TextSignalScorer { delay(30_000); TextSignal(SignalBand.NOTICIA, 0.1f, 0.4f, 0.75f, 80, "normal") }
        val t0 = System.currentTimeMillis()
        val r2 = runBlocking { ResearchService(fetcher = web(), clock = { NOW }, textSignal = slow).evaluate(ctx.title!!, "t", ctx) }
        val ms = System.currentTimeMillis() - t0
        assertNull("passou do prazo: sem sinal", r2.sinalTexto)
        assertTrue("o prazo do sinal é de 1,5 s; levou $ms ms", ms < 4_000)
    }
}
