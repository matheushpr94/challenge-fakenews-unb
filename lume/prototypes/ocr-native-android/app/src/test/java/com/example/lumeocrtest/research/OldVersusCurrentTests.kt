package com.example.lumeocrtest.research

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Notícia antiga x afirmação atual: publicações que compartilham nomes e números com a afirmação, mas são de outro
 * período, tratam de outro alcance ou citam o valor para negá-lo, servem de contexto e não decidem o resultado.
 * Rede simulada, nomes fictícios.
 */
class OldVersusCurrentTests {
    private val claim = "Torvana impõe sobretaxa de 40% sobre os vinhos de Valdória a partir de amanhã"
    private val post = ArticleContext(body = "$claim.", publishedAtMs = NOW)
    private val old = NOW - 70 * Dates.DAY_MS
    private val recent = NOW - Dates.DAY_MS
    private val oldCheck = "https://checagem.example.org/taxa-torvana"
    private val oldNews = "https://campo.example.com/cota-vinhos"
    private val newsA = "https://diario.example.com/sobretaxa-vinhos"
    private val newsB = "https://gazeta.example.com/torvana-vinhos"

    private fun run(web: FakeWeb, text: String = claim, ctx: ArticleContext? = post): ResearchResult =
        runBlocking { ResearchService(fetcher = web, clock = { NOW }).search(text, "t", ctx) }
    private fun label(r: ResearchResult) = r.sintese!!.indicacao!!.rotulo
    private fun explanation(r: ResearchResult) = r.sintese!!.explicacao.joinToString(" ") { it.texto }

    /** Checagem e notícia de meses atrás, com o mesmo país, o mesmo número e palavras parecidas. */
    private fun oldWeb() = FakeWeb(
        bingWeb = listOf(WebItem("Taxa de 40% imposta por Torvana não é para todos os produtos", oldCheck,
            "Torvana não taxou todas as importações de Valdória.")),
        google = listOf(News("Torvana impõe cota e sobretaxa de 40% sobre vinhos de Valdória", "Campo Notícias", oldNews, old,
            "Medida vale para o volume acima da cota.", "campo.example.com")),
        bingNews = listOf(News("Torvana impõe cota e sobretaxa de 40% sobre vinhos de Valdória", "Campo Notícias", oldNews, old,
            "Medida vale para o volume acima da cota.", "campo.example.com")),
        pages = mapOf(
            oldCheck to htmlPage(listOf("Torvana não estabeleceu uma taxa de 40% para importação de todos os produtos de Valdória, ao contrário do que afirmam publicações.",
                "A sobretaxa de 40% de Torvana vale para os vinhos de Valdória que excederem a cota anual."), published = old),
            oldNews to htmlPage(listOf("Torvana impõe sobretaxa de 40% sobre os vinhos de Valdória que passarem da cota, informou o governo."), published = old),
        ),
    )

    @Test fun oldCheckWithSameNumberDoesNotConfirmNewClaim() {
        val r = run(oldWeb())
        assertEquals("o número igual numa publicação antiga não é compatibilidade", "inconclusiva", label(r))
        assertEquals("insuficiente", r.sintese!!.situacao)
        assertTrue(r.resultados.getValue("responde").isEmpty())
        assertTrue(explanation(r), explanation(r).contains("não confirma nem desmente"))
        assertTrue("ausência de fontes não vira falsidade", explanation(r).contains("não indica falsidade"))
        assertTrue("a publicação antiga aparece como contexto, com a data", explanation(r).contains("contexto histórico"))
        assertNotEquals("dados_diferentes", label(r))
        // As fontes continuam visíveis para orientar a leitura.
        val shown = r.resultados.filterKeys { it in setOf("anterior", "contexto", "incerto") }.values.flatten()
        assertTrue(shown.any { it.url == oldNews || it.url == oldCheck })
    }

    @Test fun recentSourceAboutTheSameEventClarifiesTheDetail() {
        val web = oldWeb().apply {
            google = google + News("Torvana impõe sobretaxa de 40% sobre vinhos de Valdória a partir desta semana", "Diário", newsA, recent,
                "Nova sobretaxa de Torvana atinge os vinhos de Valdória.", "diario.example.com")
            bingNews = bingNews + News("Torvana impõe sobretaxa de 40% sobre vinhos de Valdória a partir desta semana", "Diário", newsA, recent,
                "Nova sobretaxa de Torvana atinge os vinhos de Valdória.", "diario.example.com")
            pages = pages + (newsA to htmlPage(listOf("Torvana impõe sobretaxa de 40% sobre os vinhos de Valdória a partir desta semana, segundo o governo."), published = recent))
        }
        val r = run(web)
        assertEquals("dados_compativeis", label(r))
        assertEquals(listOf(newsA), r.resultados.getValue("responde").map { it.url })
    }

    @Test fun recentSourceDoesNotWinJustForBeingRecent() {
        fun item(title: String, source: String, url: String) = News(title, source, url, recent, "Sobretaxa de Torvana sobre os vinhos de Valdória.", java.net.URI(url).host)
        val a = item("Torvana impõe sobretaxa de 40% sobre vinhos de Valdória", "Diário", newsA)
        val b = item("Torvana impõe sobretaxa sobre vinhos de Valdória e setor reage", "Gazeta", newsB)
        val web = FakeWeb(google = listOf(a, b), bingNews = listOf(a, b), pages = mapOf(
            newsA to htmlPage(listOf("Torvana impõe sobretaxa de 40% sobre os vinhos de Valdória, informou o governo."), published = recent),
            newsB to htmlPage(listOf("Torvana impõe sobretaxa de 25% sobre os vinhos de Valdória, segundo o setor."), published = recent)))
        val r = run(web)
        assertEquals("fontes recentes que divergem não confirmam a afirmação", "inconclusiva", label(r))
        assertEquals("divergentes", r.sintese!!.situacao)
    }

    @Test fun recentSourceWithAnotherNumberShowsDifferentData() {
        val a = News("Torvana impõe sobretaxa sobre vinhos de Valdória", "Gazeta", newsB, recent, "Sobretaxa de Torvana sobre os vinhos de Valdória.", "gazeta.example.com")
        val web = oldWeb().apply {
            google = google + a; bingNews = bingNews + a
            pages = pages + (newsB to htmlPage(listOf("Torvana impõe sobretaxa de 25% sobre os vinhos de Valdória, segundo o setor."), published = recent))
        }
        val r = run(web)
        assertEquals("a fonte atual decide; a antiga, com o número da afirmação, fica como contexto", "dados_diferentes", label(r))
    }

    @Test fun oldSourceStillAnswersAStableCount() {
        val page = "https://serrano.example.org/historia"
        val web = FakeWeb(bingWeb = listOf(WebItem("Atlético Serrano: história e títulos", page, "Conheça os títulos nacionais do Atlético Serrano.")),
            pages = mapOf(page to htmlPage(listOf("O Atlético Serrano conquistou cinco títulos nacionais."), published = NOW - 700 * Dates.DAY_MS)))
        val text = "O Atlético Serrano tem 5 títulos nacionais"
        val r = run(web, text, ArticleContext(title = text, publishedAtMs = NOW))
        assertEquals("contagem estável: a página antiga continua valendo", "dados_compativeis", label(r))
        assertEquals(page, r.resultados.getValue("responde").first().url)
    }

    @Test fun valueCitedOnlyToBeDeniedIsNotSupport() {
        val page = "https://serrano.example.org/historia"
        val web = FakeWeb(bingWeb = listOf(WebItem("Atlético Serrano: história e títulos", page, "Conheça os títulos nacionais do Atlético Serrano.")),
            pages = mapOf(page to htmlPage(listOf("O Atlético Serrano não tem 5 títulos nacionais, ao contrário do que circula nas redes."), published = d(2026, 5, 2))))
        val r = run(web, "O Atlético Serrano tem 5 títulos nacionais", null)
        assertNotEquals("dados_compativeis", label(r))
        assertTrue(explanation(r), explanation(r).contains("negação"))
        assertFalse(r.resultados.getValue("responde").any { it.url == page })
    }

    @Test fun typedClaimWithRelativeTimeTreatsOldPublicationsAsContext() {
        // Sem captura: "amanhã" é relativo ao momento da pesquisa.
        val r = run(oldWeb(), claim, null)
        assertEquals("inconclusiva", label(r))
        assertTrue(r.resultados.getValue("responde").isEmpty())
    }
}
