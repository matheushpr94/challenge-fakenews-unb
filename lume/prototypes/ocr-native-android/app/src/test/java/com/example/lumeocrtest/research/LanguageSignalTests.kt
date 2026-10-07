package com.example.lumeocrtest.research

import kotlinx.coroutines.runBlocking
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Sinal de linguagem do texto: regras de decisão, divisão em frases, leitura da resposta do servidor local e o
 * fluxo no ResearchService com um classificador simulado (sem servidor, sem rede). Textos e nomes fictícios.
 */

private fun sig(label: String, conf: Double, text: String = "Frase de teste com palavras suficientes para valer.") = SentenceSignal(text, label, conf)
private fun facts(n: Int, conf: Double = 0.97) = List(n) { sig(SentenceRole.FACTUAL, conf, "Fato número $it relatado sem adjetivos pelo veículo.") }
private fun biased(n: Int, conf: Double = 0.9) = List(n) { sig(SentenceRole.ENVIESADA, conf, "Trecho carregado número $it com juízo de valor evidente.") }

/** Classificador simulado: "vergonhosa" = enviesada, "disse" = citação, resto = fato (todos com a confiança dada). */
private class FakeRoles(private val confidence: Double = 0.95, private val fail: Boolean = false) : SentenceRoleClassifier {
    val received = mutableListOf<List<String>>()
    override suspend fun classify(sentences: List<String>): List<RoleScore>? {
        received.add(sentences)
        if (fail) return null
        return sentences.map { s ->
            val label = when { "vergonhosa" in s -> SentenceRole.ENVIESADA; "disse" in s -> SentenceRole.CITACAO; else -> SentenceRole.FACTUAL }
            RoleScore(label, confidence, SentenceRole.ALL.associateWith { if (it == label) confidence else (1 - confidence) / 2 })
        }
    }
}

class LanguageRulesTests {
    @Test fun twoHighlightsInEnoughTextIsSignal() {
        val r = LanguageRules.assess(null, facts(8) + biased(2), 0, "captura", null)
        assertEquals(LanguageState.SINAL_DE_VIES, r.estado)
        assertEquals(2, r.totalDestaques)
        assertEquals(10, r.frasesAnalisadas)
    }

    @Test fun noHighlightsIsNoSignalAndSaysItIsNotProofOfNeutrality() {
        val r = LanguageRules.assess(null, facts(12), 0, "captura", null)
        assertEquals(LanguageState.SEM_SINAL, r.estado)
        assertTrue(r.motivos.any { "não prova" in it })
    }

    @Test fun singleHighlightIsInconclusive() {
        val r = LanguageRules.assess(null, facts(10) + biased(1), 0, "captura", null)
        assertEquals(LanguageState.INCONCLUSIVO, r.estado)
        assertEquals(1, r.destaques.size)
    }

    @Test fun highlightThresholdIsRespected() {
        // 0,79 não é destaque; 0,80 é.
        val below = LanguageRules.assess(null, facts(10) + biased(3, 0.79), 0, "captura", null)
        assertEquals(0, below.totalDestaques)
        assertEquals(LanguageState.SEM_SINAL, below.estado)
        val at = LanguageRules.assess(null, facts(10) + biased(2, 0.80), 0, "captura", null)
        assertEquals(LanguageState.SINAL_DE_VIES, at.estado)
    }

    @Test fun shortTextIsInconclusiveEvenWithBiasedSentences() {
        val r = LanguageRules.assess(null, facts(3) + biased(3, 0.99), 0, "captura", null)
        assertEquals(LanguageState.INCONCLUSIVO, r.estado)
        assertTrue(r.motivos.any { "${LanguageRules.MIN_BODY} ou mais" in it })
    }

    @Test fun manyDoubtfulSentencesIsInconclusive() {
        val unsure = List(5) { sig(SentenceRole.FACTUAL, 0.55) }
        val r = LanguageRules.assess(null, facts(10) + unsure, 0, "pagina", "Jornal Vale Claro")
        assertEquals(LanguageState.INCONCLUSIVO, r.estado)
        assertTrue(r.motivos.any { "confiança foi baixa" in it })
    }

    @Test fun titleAloneNeverConcludes() {
        val title = sig(SentenceRole.ENVIESADA, 0.99, "Título carregado e enfático sobre o caso")
        val r = LanguageRules.assess(title, emptyList(), 0, "nenhuma", null)
        assertEquals("só o título: no máximo inconclusivo", LanguageState.INCONCLUSIVO, r.estado)
        assertTrue(r.indicioNoTitulo)
        assertTrue(r.motivos.any { "indício fraco" in it })
    }

    @Test fun cleanTitleWithoutBodyIsNoText() {
        val r = LanguageRules.assess(sig(SentenceRole.FACTUAL, 0.99, "Título neutro sobre a votação"), emptyList(), 0, "nenhuma", null)
        assertEquals(LanguageState.SEM_TEXTO, r.estado)
        assertFalse(r.indicioNoTitulo)
    }

    @Test fun biasedTitleWithCleanBodyIsInconclusive() {
        val title = sig(SentenceRole.ENVIESADA, 0.85, "Título carregado e enfático sobre o caso")
        val r = LanguageRules.assess(title, facts(12), 0, "captura", null)
        assertEquals(LanguageState.INCONCLUSIVO, r.estado)
        assertTrue(r.motivos.any { "Nenhuma frase do corpo" in it })
    }

    @Test fun biasedTitleAndBiasedBodyIsSignal() {
        val title = sig(SentenceRole.ENVIESADA, 0.85, "Título carregado e enfático sobre o caso")
        assertEquals(LanguageState.SINAL_DE_VIES, LanguageRules.assess(title, facts(10) + biased(3), 0, "captura", null).estado)
    }

    @Test fun titleHintNeedsModerateConfidence() {
        assertFalse(LanguageRules.assess(sig(SentenceRole.ENVIESADA, 0.69), facts(12), 0, "captura", null).indicioNoTitulo)
        assertTrue(LanguageRules.assess(sig(SentenceRole.ENVIESADA, 0.70), facts(12), 0, "captura", null).indicioNoTitulo)
        assertFalse("título factual não é indício", LanguageRules.assess(sig(SentenceRole.FACTUAL, 0.99), facts(12), 0, "captura", null).indicioNoTitulo)
    }

    @Test fun shownHighlightsAreCappedButCounted() {
        val r = LanguageRules.assess(null, facts(5) + biased(12), 0, "captura", null)
        assertEquals(LanguageRules.MAX_SHOWN, r.destaques.size)
        assertEquals(12, r.totalDestaques)
    }

    @Test fun fewHighlightsInALongTextAreNotEnough() {
        // 3 destaques em 60 frases = 5%: abaixo dos 10% exigidos, mesmo com 2 ou mais frases.
        val r = LanguageRules.assess(null, facts(57) + biased(3), 0, "captura", null)
        assertEquals(LanguageState.INCONCLUSIVO, r.estado)
        assertTrue(r.resumo.contains("pouco para o tamanho"))
        assertEquals(3, r.totalDestaques)
    }

    @Test fun shareThresholdIsTenPercent() {
        assertEquals(LanguageState.SINAL_DE_VIES, LanguageRules.assess(null, facts(18) + biased(2), 0, "captura", null).estado)      // 2 de 20 = 10%
        assertEquals(LanguageState.INCONCLUSIVO, LanguageRules.assess(null, facts(19) + biased(2), 0, "captura", null).estado)       // 2 de 21 < 10%
    }

    @Test fun veryLongTextCarriesACaveatAndCountsUnreadSentences() {
        val short = LanguageRules.assess(null, facts(40) + biased(10), 0, "pagina", "Jornal", 0)
        assertEquals(LanguageState.SINAL_DE_VIES, short.estado)
        assertFalse("até 70 frases: dentro da calibração", short.alemDaCalibracao)
        val long = LanguageRules.assess(null, facts(150) + biased(40), 5, "pagina", "Jornal", 30)
        assertEquals(LanguageState.SINAL_DE_VIES, long.estado)
        assertTrue(long.alemDaCalibracao)
        assertTrue("o usuário não vê nota técnica", long.motivos.none { "calibra" in it || "mais longo" in it })
        assertEquals(30, long.frasesNaoLidas)
        assertEquals(190, long.frasesAnalisadas)
    }

    @Test fun displayedPrecisionIsTheMeasuredOneNotTheRawConfidence() {
        assertEquals(0.78, LanguageRules.estimatedPrecision(0.98), 1e-9)
        assertEquals(0.65, LanguageRules.estimatedPrecision(0.92), 1e-9)
        assertEquals(0.55, LanguageRules.estimatedPrecision(0.80), 1e-9)
        assertTrue("nunca promete mais de 80%", (0..100).all { LanguageRules.estimatedPrecision(it / 100.0) <= 0.80 })
        assertTrue("sobe com a confiança", LanguageRules.estimatedPrecision(0.97) >= LanguageRules.estimatedPrecision(0.85))
    }

    @Test fun neverProducesAVeracityVerdict() {
        val states = setOf(LanguageState.SINAL_DE_VIES, LanguageState.SEM_SINAL, LanguageState.INCONCLUSIVO, LanguageState.SEM_TEXTO, LanguageState.INDISPONIVEL)
        val all = listOf(
            LanguageRules.assess(null, facts(12), 0, "captura", null), LanguageRules.assess(null, biased(12), 0, "captura", null),
            LanguageRules.assess(sig(SentenceRole.ENVIESADA, 0.9), emptyList(), 0, "nenhuma", null), LanguageRules.unavailable(),
        )
        all.forEach { a ->
            assertTrue(a.estado in states)
            val text = (listOf(a.resumo) + a.motivos).joinToString(" ").lowercase()
            assertFalse("sem veredito de falsidade: $text", Regex("\\bfals[ao]\\b|\\bfake\\b|\\bmentira").containsMatchIn(text) && "não diz" !in text)
        }
    }
}

class SentenceSplittingTests {
    @Test fun splitsParagraphsIntoSentencesAndDropsFragments() {
        val text = "O Senado aprovou o projeto na noite de terça-feira. A votação terminou com 45 votos a favor.\n" +
            "Leia mais\nPublicidade\n" +
            "\"Não vamos aceitar essa proposta\", disse a ministra a jornalistas. A reunião está marcada para as 10h de sexta."
        val (sentences, ignored) = LanguageRules.sentencesOf(text)
        assertEquals(4, sentences.size)
        assertTrue(sentences.none { it == "Leia mais" || it == "Publicidade" })
        assertEquals(2, ignored)
    }

    @Test fun dropsDuplicatesAndLinkOrNumberLines() {
        val s = "O prefeito anunciou a obra durante a cerimônia no centro."
        val (sentences, ignored) = LanguageRules.sentencesOf("$s $s\n12345 67890 11111 22222 33333 44444")
        assertEquals(listOf(s), sentences)
        assertEquals(2, ignored)
    }
}

class RoleResponseParsingTests {
    private fun json(vararg rows: String) = """{"results":[${rows.joinToString(",")}]}"""
    private fun row(label: String, c: Double) = """{"label":"$label","confidence":$c,"probs":{"factual":0.1,"citacao":0.1,"enviesada":0.8}}"""

    @Test fun parsesAValidResponse() {
        val r = parseRoleResponse(json(row("enviesada", 0.8), row("factual", 0.9)), 2)!!
        assertEquals(listOf("enviesada", "factual"), r.map { it.label })
        assertEquals(0.8, r[0].confidence, 1e-9)
    }

    @Test fun rejectsWrongCountUnknownLabelBadConfidenceAndGarbage() {
        assertNull(parseRoleResponse(json(row("enviesada", 0.8)), 2))
        assertNull(parseRoleResponse(json(row("falso", 0.8)), 1))
        assertNull(parseRoleResponse(json(row("factual", 1.5)), 1))
        assertNull(parseRoleResponse("não é json", 1))
        assertNull(parseRoleResponse("""{"erro":"x"}""", 1))
        assertNull(parseRoleResponse("""{"results":[{"label":"factual"}]}""", 1))
    }
}

class LanguageFlowTests {
    private val vehicle = "Agência Pedra Azul"
    private val title = "Governo anuncia novo programa para as escolas"
    private val ownUrl = "https://pedraazul.example.com/escolas/programa"
    private fun sentencesFor(n: Int, biasedCount: Int) = List(n) { i ->
        if (i < biasedCount) "A medida vergonhosa número $i foi anunciada pelo governo com aplausos exagerados." else "O programa número $i prevê a compra de material para as escolas públicas da região."
    }

    private fun service(roles: SentenceRoleClassifier?, web: FakeWeb = FakeWeb()) =
        ResearchService(fetcher = web, clock = { NOW }, roleClassifier = roles)

    @Test fun captureWithEnoughBodyIsAnalysedFromTheCapture() = runBlocking {
        val roles = FakeRoles()
        val ctx = ArticleContext(title, null, sentencesFor(10, 2).joinToString(" "), vehicle, d(2026, 9, 28))
        val e = service(roles).evaluate(title, "t", ctx)
        val l = e.linguagem!!
        assertEquals("captura", l.fonteTexto)
        assertEquals(LanguageState.SINAL_DE_VIES, l.estado)
        assertEquals(2, l.totalDestaques)
        assertEquals("título + 10 frases em uma só chamada", 11, roles.received.single().size)
        assertEquals(title, roles.received.single().first())
    }

    @Test fun titleOnlyCaptureReadsTheOwnArticleFoundOnline() = runBlocking {
        val roles = FakeRoles()
        val ctx = ArticleContext(title, null, "", vehicle, d(2026, 9, 28))
        val page = htmlPage(sentencesFor(12, 3).chunked(2).map { it.joinToString(" ") }, d(2026, 9, 28), vehicle)
        val web = FakeWeb(bingNews = listOf(News(title, vehicle, ownUrl, d(2026, 9, 28), "x", "pedraazul.example.com")), pages = mapOf(ownUrl to page))
        val l = service(roles, web).evaluate(title, "t", ctx).linguagem!!
        assertEquals("pagina", l.fonteTexto)
        assertTrue(l.fonteDescricao!!.startsWith(vehicle) && l.fonteDescricao!!.contains(title))
        assertEquals(LanguageState.SINAL_DE_VIES, l.estado)
        assertTrue("leu as frases da página, não só o título", l.frasesAnalisadas >= LanguageRules.MIN_BODY)
        assertTrue(roles.received.single().size > 8)
    }

    @Test fun titleOnlyAndOwnArticleNotFoundIsNoTextOrInconclusive() = runBlocking {
        val ctx = ArticleContext(title, null, "", vehicle, d(2026, 9, 28))
        val l = service(FakeRoles()).evaluate(title, "t", ctx).linguagem!!
        assertTrue(l.estado in setOf(LanguageState.SEM_TEXTO, LanguageState.INCONCLUSIVO))
        assertEquals(0, l.frasesAnalisadas)
    }

    @Test fun typedSingleClaimIsTreatedAsTitleAndNeverConcludes() = runBlocking {
        val claim = "Esta medida vergonhosa arruinou o país inteiro nos últimos anos"
        val l = service(FakeRoles()).evaluate(claim, "t", null).linguagem!!
        assertEquals(claim, l.titulo!!.texto)
        assertTrue(l.indicioNoTitulo)
        assertEquals(LanguageState.INCONCLUSIVO, l.estado)
    }

    @Test fun typedHeadlineReadsTheArticleWithTheSameTitleOnline() = runBlocking {
        val roles = FakeRoles()
        val page = htmlPage(sentencesFor(12, 3).chunked(2).map { it.joinToString(" ") }, d(2026, 9, 28), vehicle)
        val web = FakeWeb(bingNews = listOf(News(title, vehicle, ownUrl, d(2026, 9, 28), "x", "pedraazul.example.com")), pages = mapOf(ownUrl to page))
        val l = service(roles, web).evaluate(title, "t", null).linguagem!!
        assertEquals("pagina", l.fonteTexto)
        assertTrue("mostra de onde veio o texto", l.fonteDescricao!!.contains(vehicle) && l.fonteDescricao!!.contains(title))
        assertEquals(title, l.titulo!!.texto)
        assertEquals(LanguageState.SINAL_DE_VIES, l.estado)
    }

    @Test fun analysisLooksAtWhatTheUserTypedNotAtTheReformulatedQuery() = runBlocking {
        val roles = FakeRoles()
        val typed = "A medida vergonhosa arruinou o país inteiro nos últimos anos"
        val reformulated = "Houve um caso recente: “$typed”?"
        val l = service(roles).evaluate(reformulated, "t", null, languageText = typed).linguagem!!
        assertEquals(typed, l.titulo!!.texto)
        assertEquals(listOf(typed), roles.received.single())
        assertTrue(roles.received.flatten().none { "Houve um caso recente" in it })
    }

    @Test fun typedHeadlineWithNoMatchingArticleStaysTitleOnly() = runBlocking {
        val other = "Prefeitura inaugura nova biblioteca no centro da cidade"
        val web = FakeWeb(bingNews = listOf(News(other, vehicle, ownUrl, d(2026, 9, 28), "x", "pedraazul.example.com")),
            pages = mapOf(ownUrl to htmlPage(sentencesFor(12, 3).chunked(2).map { it.joinToString(" ") })))
        val l = service(FakeRoles(), web).evaluate(title, "t", null).linguagem!!
        assertEquals("digitado", l.fonteTexto)
        assertEquals(0, l.frasesAnalisadas)
        assertTrue(l.estado in setOf(LanguageState.SEM_TEXTO, LanguageState.INCONCLUSIVO))
    }

    @Test fun textsAboveTheReadingLimitReportWhatWasNotRead() = runBlocking {
        val roles = FakeRoles()
        val ctx = ArticleContext(title, null, sentencesFor(230, 40).joinToString(" "), vehicle, d(2026, 9, 28))
        val l = service(roles).evaluate(title, "t", ctx).linguagem!!
        assertEquals(LanguageRules.MAX_SENTENCES, l.frasesAnalisadas)
        assertEquals(30, l.frasesNaoLidas)
        assertEquals("título + as primeiras 200 frases", 201, roles.received.single().size)
        assertTrue(l.alemDaCalibracao)
        assertTrue(l.motivos.none { "mais longo" in it })
    }

    @Test fun unavailableServerKeepsTheSearchWorking() = runBlocking {
        val ctx = ArticleContext(title, null, sentencesFor(10, 2).joinToString(" "), vehicle, d(2026, 9, 28))
        val e = service(FakeRoles(fail = true)).evaluate(title, "t", ctx)
        assertEquals(LanguageState.INDISPONIVEL, e.linguagem!!.estado)
        assertNotNull(e.partes.single())
    }

    @Test fun featureOffMeansNoSection() = runBlocking {
        val ctx = ArticleContext(title, null, sentencesFor(10, 2).joinToString(" "), vehicle, d(2026, 9, 28))
        assertNull(service(null).evaluate(title, "t", ctx).linguagem)
    }

    @Test fun throwingClassifierIsContained() = runBlocking {
        val boom = SentenceRoleClassifier { error("falha simulada") }
        val ctx = ArticleContext(title, null, sentencesFor(10, 2).joinToString(" "), vehicle, d(2026, 9, 28))
        assertEquals(LanguageState.INDISPONIVEL, service(boom).evaluate(title, "t", ctx).linguagem!!.estado)
    }

    @Test fun sameTextIsClassifiedOnceThanksToTheCache() = runBlocking {
        val roles = FakeRoles()
        val svc = service(roles)
        val ctx = ArticleContext(title, null, sentencesFor(10, 0).joinToString(" "), vehicle, d(2026, 9, 28))
        svc.evaluate(title, "t1", ctx); svc.evaluate(title, "t2", ctx)
        assertEquals(1, roles.received.size)
    }
}

class LocalClientTests {
    private class Fake(val status: Int = 200, val dropOne: Boolean = false) {
        val sizes = mutableListOf<Int>()
        val seen = mutableListOf<String>()
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/classify") { ex ->
                val raw = ex.requestBody.readBytes().toString(Charsets.UTF_8)
                val sentences = JsonParser.parseString(raw).asJsonObject.getAsJsonArray("sentences").map { it.asString }
                synchronized(this@Fake) { sizes.add(sentences.size); seen.addAll(sentences) }
                val answered = if (dropOne) sentences.drop(1) else sentences
                val rows = answered.joinToString(",") { """{"label":"factual","confidence":0.9,"probs":{"factual":0.9,"citacao":0.05,"enviesada":0.05}}""" }
                val bytes = (if (status == 200) """{"results":[$rows]}""" else """{"error":"x"}""").toByteArray(Charsets.UTF_8)
                ex.sendResponseHeaders(status, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val url get() = "http://127.0.0.1:${server.address.port}"
        fun stop() = server.stop(0)
    }

    @Test fun sendsAtMostOneHundredSentencesPerCallAndKeepsOrder() = runBlocking {
        val f = Fake()
        try {
            val texts = List(250) { "Frase número $it com ação e acentuação do país." }
            val out = LocalSentenceRoleClient(f.url).classify(texts)!!
            assertEquals(250, out.size)
            assertEquals(listOf(100, 100, 50), f.sizes)
            assertEquals("a ordem das frases se mantém entre os blocos", texts, f.seen)
        } finally { f.stop() }
    }

    @Test fun portugueseCharactersTravelAsUtf8() = runBlocking {
        val f = Fake()
        try {
            LocalSentenceRoleClient(f.url).classify(listOf("A decisão da ministra não agradou à população do país."))
            assertEquals("A decisão da ministra não agradou à população do país.", f.seen.single())
        } finally { f.stop() }
    }

    @Test fun serverErrorGivesNullAndThenPausesInsteadOfHammeringTheServer() = runBlocking {
        val f = Fake(status = 500)
        try {
            val client = LocalSentenceRoleClient(f.url)
            assertNull(client.classify(listOf("Uma frase qualquer para testar a falha do servidor.")))
            assertNull(client.classify(listOf("Outra frase qualquer para testar a pausa depois da falha.")))
            assertEquals("a segunda chamada nem chegou ao servidor", 1, f.sizes.size)
        } finally { f.stop() }
    }

    @Test fun answerWithTheWrongNumberOfResultsIsRejected() = runBlocking {
        val f = Fake(dropOne = true)
        try {
            assertNull(LocalSentenceRoleClient(f.url).classify(listOf("Primeira frase longa o bastante.", "Segunda frase longa o bastante.")))
        } finally { f.stop() }
    }

    @Test fun serverNotRunningGivesNullWithoutThrowing() = runBlocking {
        val f = Fake(); val url = f.url; f.stop()   // porta fechada
        assertNull(LocalSentenceRoleClient(url).classify(listOf("Frase de teste com o servidor desligado.")))
    }
}

class HeadlineMatchingTests {
    @Test fun toleratesTheVehicleNameAndCapitalisation() {
        assertTrue(sameHeadline("A CRUZADA ELEITORAL PELAS MULHERES", "A cruzada eleitoral pelas mulheres - Revista Vale Claro"))
        assertTrue(sameHeadline("Governo anuncia novo programa para as escolas", "Governo anuncia novo programa para as escolas | Agência Pedra Azul"))
    }

    @Test fun rejectsADifferentStory() {
        assertFalse(sameHeadline("Governo anuncia novo programa para as escolas", "Governo anuncia corte de verbas para as universidades"))
    }

    @Test fun rejectsDifferentNumbers() {
        assertFalse(sameHeadline("Brasil tem 5 títulos mundiais", "Brasil tem 6 títulos mundiais"))
    }

    @Test fun rejectsTooShortKeysAndMuchLongerTitles() {
        assertFalse(sameHeadline("Chuva em Brasília", "Chuva em Brasília hoje"))
        assertFalse(sameHeadline("Cruzada eleitoral pelas mulheres", "Cruzada eleitoral pelas mulheres divide partidos de direita e esquerda no Senado"))
    }
}
