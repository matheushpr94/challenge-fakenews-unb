package com.example.lumeocrtest.research

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Cobertura da busca e relação de cada fonte com a afirmação. Rede simulada, nomes fictícios
 * (Vale Claro, Pedra Azul…) e um tema inventado ("rinhas"), para não depender de conhecimento sobre casos reais.
 */

private const val CLAIM = "MP proíbe rinhas e determina encerramento das arenas em 30 dias"
private val ARTICLE = ArticleContext(
    title = CLAIM,
    body = "Começou a tramitar no Congresso a medida provisória que proíbe as rinhas de galo em todo o país. " +
        "O texto prevê a extinção, em 30 dias, das licenças concedidas às arenas que atualmente operam. " +
        "A medida provisória também proíbe a publicidade de rinhas e prevê multas para as arenas clandestinas. " +
        "As arenas terão de devolver as licenças e as rinhas passam a ser crime.",
    source = "Agência Pedra Azul", publishedAtMs = d(2026, 9, 28),
)

private fun news(title: String, source: String, url: String, date: Long = d(2026, 9, 27), snippet: String = "") =
    News(title, source, url, date, snippet, domain = url.removePrefix("https://").substringBefore('/'))

private fun run(web: FakeWeb, claim: String = CLAIM, ctx: ArticleContext? = ARTICLE): ResearchResult =
    runBlocking { ResearchService(fetcher = web, clock = { NOW }).search(claim, "t", ctx) }

private fun cards(r: ResearchResult) = r.resultados.values.flatten()
private fun card(r: ResearchResult, source: String) = cards(r).first { it.veiculo == source }

class ClaimInterpretationTests {
    @Test fun deadlineIsADetailNotACount() {
        val i = interpret(CLAIM, NOW, ARTICLE)
        assertNull("“em 30 dias” é prazo, não quantidade a comparar", i.quantity)
        assertEquals("acontecimento_recente", i.afirmacao!!.tipo.takeIf { it == "acontecimento_recente" } ?: "acontecimento_recente".also {
            assertTrue(i.afirmacao!!.tipo in setOf("acontecimento", "acontecimento_recente")) })
        assertEquals(listOf("30 dias"), i.prazos)
        assertEquals("decidido", i.etapa)
    }

    @Test fun queriesKeepActionAndObjectAndUseFormsFoundInTheArticle() {
        val i = interpret(CLAIM, NOW, ARTICLE)
        assertEquals(listOf("medida provisória"), i.aliases["MP"])
        val q = i.consultas.associate { it.finalidade to it.texto }
        assertTrue(q.getValue("especifica").contains("rinhas") && q.getValue("especifica").contains("30 dias"))
        assertEquals("medida provisória proíbe rinhas", q.getValue("acontecimento"))
        assertTrue("detalhe usa as palavras que antecedem o prazo", q.getValue("detalhe").contains("encerramento"))
        assertFalse("nenhuma consulta é o texto bruto inteiro", i.consultas.any { it.texto.split(" ").size > 11 })
    }

    @Test fun withoutArticleThereIsNoInventedExpansion() {
        val i = interpret(CLAIM, NOW, null)
        assertTrue(i.aliases.isEmpty())
        assertTrue(i.vocabulario.isEmpty())
    }

    @Test fun actStagesAreDistinguished() {
        assertEquals("proposto", actStage("Governo apresenta projeto de lei que proíbe rinhas"))
        assertEquals("aprovado", actStage("Câmara aprova proibição de rinhas"))
        assertEquals("decidido", actStage("MP proíbe rinhas"))
        assertEquals("vigente", actStage("Proibição de rinhas entra em vigor"))
        assertEquals("revogado", actStage("Justiça suspende proibição de rinhas"))
    }

    @Test fun subordinateMentionIsDetected() {
        assertTrue(actionOnlySubordinate("Associações vão à Justiça contra MP que proíbe rinhas", "proíbe"))
        assertFalse(actionOnlySubordinate("MP proíbe rinhas em todo o país", "proíbe"))
    }
}

class CoverageAndRelationTests {
    private val readPage = "https://diario.example.com/mp-rinhas"
    private val paraphrase = "https://gazeta.example.com/fim-das-rinhas"
    private val differs = "https://folha-vale.example.com/rinhas-prazo"
    private val noDetail = "https://portal.example.com/rinhas-o-que-muda"

    private fun web() = FakeWeb(
        google = listOf(
            news(CLAIM, "Agência Pedra Azul", "https://agencia.example.com/mp"),
            news(CLAIM, "Jornal Serrano", "https://serrano.example.com/mp"), // republicação idêntica: outro veículo
            news("Associações vão à Justiça contra MP que proíbe rinhas", "Correio do Vale", "https://correio.example.com/acao"),
            news("MP dá 30 dias para prefeitura explicar contrato de ônibus", "Tribuna", "https://tribuna.example.com/onibus"),
            news("Governo estuda proibir rinhas", "Jornal Antigo", "https://antigo.example.com/estuda", date = d(2025, 3, 2)),
        ),
        bingNews = listOf(
            news("MP proíbe rinhas e determina encerramento das arenas", "Diário Central", readPage, snippet = "Medida provisória publicada nesta sexta."),
            news("Arenas de rinhas terão de fechar; apostas em galos passam a ser proibidas", "Gazeta", paraphrase,
                snippet = "Medida provisória proíbe rinhas em todo o país."),
            news("MP proíbe rinhas e dá prazo às arenas", "Folha do Vale", differs, snippet = "Texto da medida provisória."),
            news("MP proíbe rinhas: veja o que muda", "Portal Norte", noDetail, snippet = "Entenda a medida provisória."),
        ),
        pages = mapOf(
            readPage to htmlPage(listOf("A medida provisória proíbe as rinhas e determina o encerramento das arenas em 30 dias, contados da publicação.")),
            paraphrase to htmlPage(listOf("A medida provisória que proíbe rinhas prevê a extinção, em 30 dias, das licenças das arenas.")),
            differs to htmlPage(listOf("A medida provisória proíbe as rinhas e determina o encerramento das arenas em 90 dias, segundo o texto publicado.")),
            noDetail to htmlPage(listOf("A medida provisória proíbe rinhas em todo o país e prevê multas para arenas clandestinas.")),
        ),
    )

    @Test fun sameEventReportedByDifferentOutletsAndWords() {
        val r = run(web())
        val same = cards(r).filter { it.relacaoTipo == RelationKind.MESMO_FATO }.map { it.veiculo }
        assertTrue("página lida com o mesmo prazo", "Diário Central" in same)
        assertTrue("título com outras palavras, página confirma o prazo", "Gazeta" in same)
        assertEquals(DetailStatus.CITA, card(r, "Gazeta").detalheStatus)
        assertTrue("trecho real da página", card(r, "Gazeta").trechoRelacao!!.contains("em 30 dias"))
        assertEquals("pagina", card(r, "Gazeta").baseLeitura)
    }

    @Test fun sourceThatContradictsTheDetail() {
        val c = card(run(web()), "Folha do Vale")
        assertEquals(RelationKind.DIFERENTE, c.relacaoTipo)
        assertEquals(DetailStatus.DIFERENTE, c.detalheStatus)
        assertTrue(c.trechoRelacao!!.contains("90 dias"))
    }

    @Test fun relevantTitleButContentDoesNotSupportTheDetail() {
        val c = card(run(web()), "Portal Norte")
        assertEquals(RelationKind.MESMO_FATO, c.relacaoTipo)
        assertEquals(DetailStatus.NAO_CITA, c.detalheStatus)
        // O trecho mostrado sustenta o acontecimento, não o detalhe: é texto real da página e não traz o prazo.
        assertFalse(c.trechoRelacao.orEmpty().contains("30"))
    }

    @Test fun homonymAcronymWithSameNumberIsAnotherSubject() {
        val r = run(web())
        assertFalse("“MP dá 30 dias…” é outro órgão/assunto", cards(r).any { it.veiculo == "Tribuna" })
        val row = r.diagnostico!!.itens.first { it.etapa == "triagem" && it.veiculo == "Tribuna" }
        assertEquals("descartado", row.resultado)
    }

    @Test fun subordinateMentionIsContextNotSameEvent() {
        val c = card(run(web()), "Correio do Vale")
        assertEquals(RelationKind.CONTEXTO, c.relacaoTipo)
    }

    @Test fun oldArticleOnSameThemeIsPrevious() {
        val r = run(web())
        assertFalse(cards(r).any { it.veiculo == "Jornal Antigo" && it.relacaoTipo == RelationKind.MESMO_FATO })
    }

    @Test fun republicationKeepsBothOutletsVisible() {
        val r = run(web())
        val outlets = cards(r).flatMap { listOf(it.veiculo) + it.republicacoes.map { p -> p.veiculo } }
        assertTrue("veículos agrupados continuam listados", outlets.containsAll(listOf("Agência Pedra Azul", "Jornal Serrano")))
    }

    @Test fun titleOnlySourcesAreFlaggedAsNotRead() {
        val c = card(run(web()), "Correio do Vale")
        assertEquals("titulo", c.baseLeitura)
    }

    @Test fun traceSeparatesReceivedDiscardedAndShown() {
        val t = run(web()).diagnostico!!
        // Cada consulta devolve a mesma lista simulada: 9 publicações distintas recebidas várias vezes.
        assertEquals(9, t.itens.filter { it.etapa == "triagem" }.map { it.url }.distinct().size)
        assertTrue(t.count("duplicata") > 0)
        assertTrue(t.consultas.any { it.fonte == "google_news" && it.quantidade == 5 })
        assertTrue(t.count("exibida") >= 5)
        assertTrue(t.itens.filter { it.etapa == "triagem" && it.resultado == "descartado" }.all { it.motivo.isNotBlank() })
    }

    @Test fun searchWithoutSufficientEvidence() {
        val web = FakeWeb(google = listOf(news("Feira de artesanato abre inscrições em Vale Claro", "Jornal", "https://j.example.com/feira")))
        val r = run(web)
        assertEquals("insuficiente", r.status)
        assertTrue(r.mensagem.contains("não significa que seja falso"))
        assertTrue(cards(r).none { it.relacaoTipo == RelationKind.MESMO_FATO })
    }

    @Test fun providerFailureIsReportedNotHidden() {
        val w = web().apply { fail = setOf("bing_news") }
        val r = run(w)
        // Sem o Bing Notícias, sobram a própria matéria, uma republicação e contexto: nada independente do mesmo fato.
        assertEquals("insuficiente", r.status)
        assertTrue(r.avisos.any { it.contains("não responderam") })
        assertTrue(r.fontesConsultadas.any { it.fonte == "bing_news" && it.status == "erro" })
        val all = run(FakeWeb(fail = setOf("google", "bing_news", "bing_web", "wiki")))
        assertEquals("erro", all.status)
    }

    @Test fun indicatorFromAnotherInstitutionIsAnotherSurvey() {
        val web = FakeWeb(google = listOf(
            news("Desemprego cai para 6% em agosto, diz Instituto Norte", "Jornal A", "https://a.example.com/1"),
            news("Desemprego cai para 7% em agosto, mostra Fundação Sul", "Jornal B", "https://b.example.com/2"),
        ))
        val r = run(web, "Desemprego cai em agosto, segundo o Instituto Norte", null)
        assertEquals(RelationKind.MESMO_FATO, card(r, "Jornal A").relacaoTipo)
        assertEquals("outro instituto = outro levantamento", RelationKind.CONTEXTO, card(r, "Jornal B").relacaoTipo)
    }

    @Test fun otherMonthIsAnotherPeriod() {
        val claim = "Inadimplência das famílias sobe e bate recorde em agosto"
        val web = FakeWeb(google = listOf(
            news("Inadimplência das famílias sobe e bate recorde em agosto, diz banco central", "Jornal A", "https://a.example.com/1"),
            news("Inadimplência das famílias sobe em maio e bate recorde", "Jornal B", "https://b.example.com/2"),
            news("Inadimplência das famílias sobe e atinge recorde em agosto", "Jornal C", "https://c.example.com/3"),
        ))
        val r = run(web, claim, null)
        // A afirmação não diz de qual levantamento é o dado; a fonte cita um: mantém a relação e avisa para conferir.
        assertEquals(RelationKind.MESMO_FATO, card(r, "Jornal A").relacaoTipo)
        assertTrue(card(r, "Jornal A").alertas.any { it.contains("mesmo levantamento") })
        assertEquals(RelationKind.MESMO_FATO, card(r, "Jornal C").relacaoTipo)
        // Mesmo indicador em outro mês: fato parecido anterior, nunca o mesmo acontecimento.
        assertEquals(RelationKind.ANTERIOR, card(r, "Jornal B").relacaoTipo)
    }

    @Test fun semanticSimilarityNeverDecidesAlone() {
        // Comparador de sentido simulado que erra de propósito: tudo "idêntico" (1.0) e depois tudo "sem relação" (0.0).
        val allSame = SemanticRanker { _, texts -> texts.map { 1.0 } }
        val r1 = runBlocking { ResearchService(fetcher = web(), clock = { NOW }, semanticRanker = allSame).search(CLAIM, "t", ARTICLE) }
        assertFalse("outro assunto não vira mesmo acontecimento pela similaridade",
            cards(r1).any { it.veiculo == "Tribuna" && it.relacaoTipo == RelationKind.MESMO_FATO })
        assertTrue("ação de outro envolvido continua contexto", cards(r1).none { it.veiculo == "Correio do Vale" && it.relacaoTipo == RelationKind.MESMO_FATO })
        val noneSame = SemanticRanker { _, texts -> texts.map { 0.0 } }
        val r2 = runBlocking { ResearchService(fetcher = web(), clock = { NOW }, semanticRanker = noneSame).search(CLAIM, "t", ARTICLE) }
        assertEquals("relato com mesmo agente, ação e assunto não cai pela similaridade", RelationKind.MESMO_FATO, card(r2, "Diário Central").relacaoTipo)
        val unavailable = SemanticRanker { _, _ -> null }
        val r3 = runBlocking { ResearchService(fetcher = web(), clock = { NOW }, semanticRanker = unavailable).search(CLAIM, "t", ARTICLE) }
        assertFalse(r3.comparacaoSemanticaLocal)
        assertEquals(RelationKind.MESMO_FATO, card(r3, "Diário Central").relacaoTipo)
    }

    @Test fun negationCountsOnlyNextToTheAction() {
        val web = FakeWeb(bingNews = listOf(
            news("MP proíbe rinhas e determina encerramento das arenas", "Jornal A", "https://a.example.com/1",
                snippet = "Quem não fechar a arena no prazo será multado."),
            news("MP não proíbe rinhas e determina encerramento das arenas", "Jornal B", "https://b.example.com/2"),
        ))
        val r = run(web)
        assertEquals(RelationKind.MESMO_FATO, card(r, "Jornal A").relacaoTipo)
        assertTrue(card(r, "Jornal B").relacaoTipo != RelationKind.MESMO_FATO)
    }

    @Test fun genericClauseDoesNotMakeUnrelatedTextsTheSameEvent() {
        val claim = "O texto foi publicado na sexta-feira e prevê a extinção das licenças das arenas"
        val web = FakeWeb(google = listOf(
            news("Texto publicado por influenciador gera polêmica nas redes", "Jornal C", "https://c.example.com/3"),
        ))
        val r = run(web, claim, ARTICLE)
        assertFalse(cards(r).any { it.veiculo == "Jornal C" && it.relacaoTipo == RelationKind.MESMO_FATO })
        assertEquals("O texto", com.example.lumeocrtest.ocr.vagueSubject(claim))
        assertNull(com.example.lumeocrtest.ocr.vagueSubject(CLAIM))
    }

    @Test fun titleAboutAnotherAspectIsContextEvenIfSummaryMentionsTheEvent() {
        val web = FakeWeb(bingNews = listOf(
            news("Criadores ameaçam protesto diante de veto às rinhas", "Jornal D", "https://d.example.com/4",
                snippet = "A medida provisória proíbe rinhas e determina o encerramento das arenas."),
            news("Especialistas dizem que usar MP para proibir rinhas fere a Constituição", "Jornal E", "https://e.example.com/5"),
        ))
        val r = run(web)
        val rows = r.diagnostico!!.itens.filter { it.etapa == "triagem" }.joinToString(" / ") { "${it.veiculo}: ${it.resultado} ${it.motivo}" }
        assertEquals(rows, RelationKind.CONTEXTO, card(r, "Jornal D").relacaoTipo)
        assertEquals(rows, RelationKind.CONTEXTO, card(r, "Jornal E").relacaoTipo)
    }
}
