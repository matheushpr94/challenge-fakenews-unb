package com.example.lumeocrtest.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressões da leitura de capturas. As capturas reais usam a saída gravada do ML Kit (src/test/resources/ocr);
 * os casos sintéticos cobrem situações que as capturas não têm. As expectativas só exigem o que aparece na imagem.
 */
class ArticleReaderRegressionTest {
    private val reader = ArticleReader()
    private fun real(name: String): Pair<ArticleReading, ClaimChoice> {
        val f = OcrFixtures.load(name)
        val r = reader.read(f.blocks, f.width, f.height)
        return r to reader.claimFor(r)
    }

    // ---- capturas reais ----------------------------------------------------------------------

    @Test fun senadoInstitutionalBylineCreditCaptionAndSidebar() {
        val (r, c) = real("agencia-senado-bets")
        assertEquals("MP proíbe bets e determina encerramento das operações em 30 dias", r.title)
        assertEquals("Agência Senado", r.metadata.source)
        assertEquals(MetadataOrigin.BYLINE, r.metadata.sourceOrigin)
        assertEquals("Agência Senado", r.metadata.institutionalByline)
        assertNull("crédito de foto não é autor", r.metadata.author)
        assertEquals("28/09/2026, 10h51", r.metadata.publishedAt)
        assertNotNull(r.metadata.publishedAtMs)
        assertTrue(r.metadata.imageCredits.contains("Anna Tolipova"))
        assertTrue(r.metadata.captions.single().startsWith("A MP prevê a extinção"))
        assertNull("a linha de assinatura não é subtítulo", r.subtitle)
        assertTrue(r.body.startsWith("Começou a tramitar no Congresso Nacional"))
        for (bad in listOf("Proposições", "MPV 1394", "Anna Tolipova", "BETTING", "FOOTBALL", "A MP prevê a extinção, em 30 dias, das autorizações concedidas a empresas"))
            assertFalse("corpo não pode conter “$bad”", r.body.contains(bad))
        assertEquals("MP proíbe bets e determina encerramento das operações em 30 dias", c.claim)
        assertFalse(c.needsChoice)
    }

    @Test fun bbcIndividualAuthorAndRecommendationColumn() {
        val (r, c) = real("bbc-fux-article")
        assertTrue(r.title!!.startsWith("Fux derruba decisão de Dino"))
        assertTrue("título de duas caixas do OCR", r.title!!.endsWith("eleição"))
        assertEquals("Pedro Martins", r.metadata.author)
        assertEquals("BBC News Brasil", r.metadata.source)
        assertTrue(r.metadata.publishedAt!!.startsWith("29 setembro 2026"))
        assertTrue(r.metadata.imageCredits.any { it.startsWith("GETTY") })
        assertTrue(r.body.contains("Nossa Senhora Aparecida voltou a colocar"))
        for (bad in listOf("Piketty", "Leia mais", "Principais notícias", "pesquisas diziam", "Padroeira do Bra pelo Vaticano"))
            assertFalse("corpo não pode conter “$bad”", r.body.contains(bad))
        // Afirmação sem a cauda editorial “entenda o vaivém…”.
        assertEquals("Fux derruba decisão de Dino sobre posts de Nossa Senhora Aparecida", c.claim)
    }

    @Test fun agenciaBrasilUppercaseAuthorSplitOverTwoLines() {
        val (r, c) = real("agenciabrasil-bets")
        assertEquals("Bets passam a ser proibidas no país e apostador receberá saldo", r.title)
        assertEquals("MP diz que plataformas ficarão fora do ar e proíbe novos depósitos", r.subtitle)
        assertEquals("Marcelo Brandão", r.metadata.author)
        assertEquals("Agência Brasil", r.metadata.source)
        assertEquals("25/09/2026, 20:33", r.metadata.publishedAt)
        assertEquals("Brasília", r.metadata.place)
        assertTrue(r.metadata.imageCredits.single().contains("PAULO PINTO"))
        assertFalse("banner de cookies não é conteúdo", r.body.contains("cookies"))
        assertEquals("Bets passam a ser proibidas no país e apostador receberá saldo", c.claim)
    }

    @Test fun camaraMastheadIsNotTitleAndInstitutionalByline() {
        val (r, c) = real("camara-bets-pl")
        assertTrue(r.title!!.startsWith("Projeto de lei apresentado pelo governo"))
        assertNotNullAndNot("AGÊNCIA CÂMARA DE NOTÍCIAS", c.claim)
        assertEquals("Agência Câmara", r.metadata.source)
        assertEquals("Agência Câmara", r.metadata.institutionalByline)
        assertNull(r.metadata.author)
        assertEquals("28/09/2026, 18:47", r.metadata.publishedAt)
        assertTrue(r.subtitle!!.startsWith("Proposta foi enviada pelo Poder Executivo"))
        assertFalse(r.metadata.imageCredits.any { it.contains("MEDIDA", true) })
    }

    @Test fun g1MobileTitleAfterAdsAndTicker() {
        val (r, c) = real("g1-inadimplencia")
        assertEquals("Em meio à corrida eleitoral, inadimplência volta a subir e bate novo recorde em agosto", r.title)
        assertEquals("Alexandro Martello", r.metadata.author)
        assertEquals("g1", r.metadata.source)
        assertEquals("Brasília", r.metadata.place)
        assertTrue(r.metadata.publishedAt!!.startsWith("29/09/2026"))
        assertEquals("há 7 horas", r.metadata.updated)
        assertFalse("cotação não entra na afirmação", c.claim!!.contains("R$ 5"))
    }

    @Test fun g1PartialTitleAsksUserInsteadOfGuessing() {
        val (r, c) = real("g1-inadimplencia-corpo")
        assertNull(r.title)
        assertEquals("agosto", r.partialTitle)
        assertNull(c.claim)
        assertTrue(c.needsChoice)
        assertTrue(c.note!!.contains("cortado"))
        assertEquals("Alexandro Martello", r.metadata.author)
        assertFalse("anúncio não é título nem corpo", r.body.contains("feitos") || r.body.contains("Abra sua conta"))
        assertTrue(r.body.contains("taxa de inadimplência média total"))
        assertTrue(c.alternatives.any { it.contains("taxa de inadimplência") })
    }

    @Test fun cropWithoutHeaderLeavesFieldsUnidentified() {
        val (r, c) = real("recorte-senado-sem-titulo")
        assertNull(r.title); assertNull(r.metadata.source); assertNull(r.metadata.author); assertNull(r.metadata.publishedAt)
        // Sem título, a frase que abre o texto vira a sugestão (editável); veículo, autor e data seguem sem palpite.
        assertFalse(c.needsChoice)
        assertTrue(c.claim!!.startsWith("Começou a tramitar"))
        assertTrue(c.note!!.contains("Não encontramos um título"))
        assertFalse(r.body.contains("Proposições"))
    }

    private fun assertNotNullAndNot(forbidden: String, value: String?) {
        assertNotNull(value); assertFalse(value!!.contains(forbidden))
    }

    // ---- casos sintéticos --------------------------------------------------------------------

    private fun b(text: String, x: Int, y: Int, w: Int, lineH: Int, lines: List<String> = listOf(text), conf: Float = 0.9f): OcrBlock {
        val details = lines.mapIndexed { i, t -> OcrLine(t, OcrRect(x, y + i * (lineH + 6), x + w, y + i * (lineH + 6) + lineH), conf) }
        return OcrBlock(text, OcrRect(x, y, x + w, details.last().box!!.bottom), lines, details)
    }
    private fun para(text: String, x: Int, y: Int, w: Int, lineH: Int, n: Int): OcrBlock {
        val words = text.split(" ")
        val lines = words.chunked((words.size + n - 1) / n).map { it.joinToString(" ") }
        return b(text, x, y, w, lineH, lines)
    }

    @Test fun noAuthorVisibleStaysUnidentified() {
        val blocks = listOf(
            b("Prefeitura amplia horário das unidades de saúde", 40, 60, 600, 40, listOf("Prefeitura amplia horário das", "unidades de saúde")),
            b("14/06/2026 09h10", 40, 170, 200, 14),
            para("As unidades passam a funcionar até as 22h a partir de segunda-feira, segundo a secretaria municipal de saúde.", 40, 220, 600, 16, 3),
            para("A medida vale para as quatro regiões da cidade e deve atender cerca de duas mil pessoas por dia.", 40, 300, 600, 16, 3),
        )
        val r = reader.read(blocks, 700, 600)
        assertEquals("Prefeitura amplia horário das unidades de saúde", r.title)
        assertNull(r.metadata.author); assertNull(r.metadata.institutionalByline); assertNull(r.metadata.source)
        assertEquals("14/06/2026 09h10", r.metadata.publishedAt)
    }

    @Test fun photoCreditNameBelowImageIsNotAuthor() {
        val blocks = listOf(
            b("Cidade inaugura ponte sobre o rio central", 40, 40, 620, 38, listOf("Cidade inaugura ponte sobre", "o rio central")),
            b("Por Carla Nunes, Jornal do Vale", 40, 140, 300, 14),
            b("A ponte liga os bairros do norte ao centro", 40, 520, 400, 12), // legenda no pé da imagem
            b("Rafael Costa", 560, 536, 100, 11), // crédito da foto
            para("A obra levou dois anos e custou recursos estaduais e municipais, segundo a prefeitura da cidade.", 40, 580, 620, 16, 3),
            para("Os moradores poderão atravessar a pé ou de bicicleta a partir desta semana, informou a secretaria.", 40, 660, 620, 16, 3),
        )
        val r = reader.read(blocks, 700, 800)
        assertEquals("Carla Nunes", r.metadata.author)
        assertEquals("Jornal do Vale", r.metadata.source)
        assertTrue(r.metadata.imageCredits.contains("Rafael Costa"))
        assertTrue(r.metadata.captions.single().startsWith("A ponte liga"))
        assertFalse(r.body.contains("Rafael Costa")); assertFalse(r.body.contains("A ponte liga"))
    }

    @Test fun recommendationsAndMenuAfterBodyAreExcluded() {
        val blocks = listOf(
            b("Menu", 20, 10, 60, 14), b("Entrar", 600, 10, 60, 14),
            b("Governo federal publica regra para trens regionais", 40, 60, 620, 36, listOf("Governo federal publica regra", "para trens regionais")),
            b("Da Redação | 10/05/2026", 40, 160, 260, 14),
            para("A regra define prazos para a concessão de linhas regionais e foi publicada no diário oficial nesta sexta.", 40, 210, 620, 16, 3),
            b("Leia também", 40, 300, 150, 16),
            para("Prefeito anuncia novas linhas de ônibus elétricos para a região metropolitana da capital.", 40, 330, 620, 16, 2),
        )
        val r = reader.read(blocks, 700, 500)
        assertTrue(r.body.contains("prazos para a concessão"))
        assertFalse(r.body.contains("ônibus elétricos"))
        assertFalse(r.body.contains("Menu"))
        assertEquals("Redação", r.metadata.institutionalByline)
        assertNull("“Redação” não identifica o veículo", r.metadata.source)
        assertEquals(BlockRole.RECOMENDACAO, r.roleOf(6))
    }

    @Test fun sidebarHeadlinesBesideBodyAreLateral() {
        val blocks = listOf(
            b("Senado aprova regra para energia solar", 40, 40, 560, 36, listOf("Senado aprova regra para", "energia solar")),
            b("Por Ana Souza", 40, 140, 160, 14),
            para("Os senadores aprovaram a nova regra que altera a compensação de créditos de energia solar nas residências.", 40, 190, 560, 16, 3),
            para("O texto segue agora para sanção e deve valer a partir do próximo ano, segundo o relator do projeto.", 40, 270, 560, 16, 3),
            b("Ministro anuncia plano para energia solar em outra cidade", 640, 190, 220, 14, listOf("Ministro anuncia plano", "para energia solar em", "outra cidade")),
        )
        val r = reader.read(blocks, 900, 500)
        assertEquals(BlockRole.LATERAL, r.roleOf(4))
        assertFalse(r.body.contains("outra cidade"))
        assertEquals("Ana Souza", r.metadata.author)
    }

    @Test fun serviceTitleKeepsPlaceAndPeriodInsteadOfBareTopic() {
        val (r, c) = real("metropoles-lei-seca")
        assertEquals("Lei Seca: saiba em quais estados é proibido beber no 1° turno das eleições", r.title)
        assertEquals("Lei Seca: em quais estados é proibido beber no 1° turno das eleições", c.claim)
        assertFalse(c.needsChoice)
        assertTrue(c.note!!.contains("guia"))
        assertTrue(r.subtitle!!.startsWith("Levantamento do Metrópoles"))
        assertEquals("Giovanna Estrela", r.metadata.author)
        assertEquals("30/09/2026 04:00", r.metadata.publishedAt)
        assertFalse(r.socialPost)
    }

    @Test fun realPostsKeepOnlyTheAuthorsText() {
        val (almoco, ca) = real("x-post-almoco")
        assertTrue(almoco.socialPost)
        assertEquals("SPACE LIBERDADE @NewsLiberdade", almoco.postProfile)
        assertFalse("resposta de outro perfil", almoco.body.contains("impostos"))
        assertFalse("contadores", almoco.body.contains("113"))
        assertFalse(ca.needsChoice)
        assertTrue(ca.claim!!.contains("organizado por Vorcaro para Moraes"))
        val (taxa, ct) = real("x-post-sobretaxa")
        assertTrue(taxa.socialPost); assertNull("texto dentro da foto não é título", taxa.title)
        assertEquals("China impõe sobretaxa de 55% sobre as carnes bovinas brasileiras a partir de amanhã", ct.claim)
        assertFalse(taxa.body.contains("Por isso"))
        val (fla, cf) = real("x-post-flamengo")
        assertTrue(fla.socialPost)
        assertTrue(cf.claim!!.startsWith("Flamengo se antecipa aos clubes"))
        assertFalse(cf.needsChoice)
    }

    @Test fun titleCutOnlyWhenTheFirstPartIsTheWholeNews() {
        fun claimOf(title: String) = reader.claimFor(reader.read(listOf(
            b(title, 40, 40, 620, 36, listOf(title.take(title.length / 2), title.drop(title.length / 2))),
            para("O texto da matéria explica a medida e traz a posição das autoridades envolvidas no caso.", 40, 160, 620, 16, 3),
        ), 700, 400)).claim
        assertEquals("Torvana aprova nova regra para os vinhos importados", claimOf("Torvana aprova nova regra para os vinhos importados: entenda o que muda"))
        assertEquals("Vale Claro amplia vacinação: quem pode se vacinar a partir de 5 de outubro",
            claimOf("Vale Claro amplia vacinação: saiba quem pode se vacinar a partir de 5 de outubro"))
        assertEquals("IPVA: como pagar com desconto em Valdória", claimOf("IPVA: veja como pagar com desconto em Valdória"))
        assertEquals("Frete grátis: o que muda para quem compra em Torvana", claimOf("Frete grátis: o que muda para quem compra em Torvana"))
    }

    @Test fun newsPageWithProfileHandleAtTheTopKeepsTheTitleAndOffersTheOtherReading() {
        // Barra com @ do jornal no alto, uma chamada de texto e, abaixo, título, linha fina, assinatura e parágrafos.
        val blocks = listOf(
            b("Siga @jornalvaldoria", 40, 20, 300, 16),
            b("Receba as principais notícias do dia no seu celular", 40, 50, 600, 16),
            b("Torvana aprova nova regra para os vinhos importados de Valdória", 40, 110, 620, 36,
                listOf("Torvana aprova nova regra para os", "vinhos importados de Valdória")),
            b("Texto segue para sanção e muda a cobrança sobre os produtores da região", 40, 200, 620, 20),
            b("Por Ana Souza", 40, 240, 200, 14),
            para("O parlamento de Torvana aprovou nesta terça a nova regra para os vinhos importados de Valdória.", 40, 280, 620, 16, 3),
            para("A regra entra em vigor em janeiro, segundo o governo, e vale para todos os importadores.", 40, 360, 620, 16, 3),
        )
        val r = reader.read(blocks, 700, 500)
        assertEquals("Torvana aprova nova regra para os vinhos importados de Valdória", r.title)
        assertFalse(r.socialPost)
        val c = reader.claimFor(r)
        assertEquals("Torvana aprova nova regra para os vinhos importados de Valdória", c.claim)
        assertFalse(c.needsChoice)
        assertTrue(c.note!!.contains("notícia e de post"))
        assertTrue(c.alternatives.first().startsWith("Receba as principais"))
    }

    // ---- posts de rede social (nomes fictícios) ------------------------------------------------

    private fun postHeader(y: Int) = listOf(b("Rádio Valdória", 150, y, 260, 22), b("@valdoria_fm · 3h", 440, y, 200, 22))

    @Test fun postAboutOneEventSuggestsOpeningSentenceAndKeepsDetailsAside() {
        val blocks = postHeader(60) + listOf(
            para("URGENTE! Clube Ardeo entra com pedido no Tribunal de Torvana contra Norma Geral que proibiu os patrocínios.", 150, 100, 620, 22, 3),
            para("O clube pediu hoje ao juiz Ivo Lestan para participar da ação que questiona a NG. A diretoria também quer que sua manifestação seja considerada.", 150, 230, 620, 22, 4),
            para("Na petição, o Ardeo afirma que a proibição afeta o financiamento do esporte.", 150, 390, 620, 22, 2),
        )
        val r = reader.read(blocks, 800, 600)
        assertNull(r.title)
        assertTrue(r.socialPost)
        assertEquals("Rádio Valdória @valdoria_fm", r.postProfile)
        val c = reader.claimFor(r)
        assertEquals("Clube Ardeo entra com pedido no Tribunal de Torvana contra Norma Geral que proibiu os patrocínios", c.claim)
        assertFalse("um acontecimento só: não pede escolha", c.needsChoice)
        assertEquals(3, c.alternatives.size)
        assertTrue(c.alternatives.first().startsWith("O clube pediu hoje"))
    }

    @Test fun postIsRecognizedWhenBadgeIsReadAsLetterGluedToHandle() {
        val blocks = listOf(b("Rádio Valdória", 150, 60, 260, 22), b("O@valdoria... . 3h", 440, 60, 200, 22),
            para("Clube Ardeo entra com pedido no Tribunal de Torvana contra Norma Geral que proibiu os patrocínios.", 150, 100, 620, 22, 3))
        val r = reader.read(blocks, 800, 300)
        assertTrue(r.socialPost)
        assertEquals("Rádio Valdória", r.postProfile)
        assertFalse(reader.claimFor(r).needsChoice)
    }

    @Test fun textWithoutTitleOrProfileStillGetsOneSuggestion() {
        val blocks = listOf(
            para("Clube Ardeo entra com pedido no Tribunal de Torvana contra Norma Geral que proibiu os patrocínios.", 150, 100, 620, 22, 3),
            para("O clube pediu hoje ao juiz Ivo Lestan para participar da ação que questiona a NG.", 150, 230, 620, 22, 3),
        )
        val r = reader.read(blocks, 800, 400)
        val c = reader.claimFor(r)
        assertFalse(r.socialPost)
        assertTrue(c.claim!!.startsWith("Clube Ardeo entra com pedido"))
        assertFalse(c.needsChoice)
        assertEquals(1, c.alternatives.size)
    }

    @Test fun postTextWinsOverBigTextInsideAttachedImage() {
        // Perfil, rótulo do aplicativo, texto do post (duas caixas), arte com letras grandes, data e uma resposta.
        val blocks = listOf(
            b("VAL Valdória Agora @valdoriaagora", 66, 324, 432, 44, listOf("VAL Valdória Agora", "@valdoriaagora")),
            b("Show translation", 66, 489, 300, 30),
            b("BOMBA: Torvana impõe sobretaxa de 40%", 101, 548, 900, 50),
            b("sobre os vinhos de Valdória a partir de amanhã.", 28, 615, 1030, 50, listOf("sobre os vinhos de Valdória a partir de", "amanhã.")),
            b("Taxa de 40% de Torvana", 774, 974, 250, 66, listOf("Taxa de 40%", "de Torvana")),
            b("Imposto Novo", 846, 1130, 130, 30),
            b("08:57 · 30/09/26 · 8,5K Views", 30, 1652, 560, 40),
            b("Ivo Lestan @ivolestan · 9m Por isso eu sempre digo que devemos ter", 186, 2008, 817, 44,
                listOf("Ivo Lestan @ivolestan · 9m", "Por isso eu sempre digo que devemos ter")),
        )
        val r = reader.read(blocks, 1170, 2532)
        assertNull("letras grandes dentro da imagem não são título", r.title)
        assertTrue(r.socialPost)
        assertEquals("Valdória Agora @valdoriaagora", r.postProfile)
        assertEquals("BOMBA: Torvana impõe sobretaxa de 40% sobre os vinhos de Valdória a partir de amanhã.", r.body)
        assertFalse("a resposta de outro perfil não é texto do post", r.body.contains("Por isso"))
        val c = reader.claimFor(r)
        assertEquals("Torvana impõe sobretaxa de 40% sobre os vinhos de Valdória a partir de amanhã", c.claim)
        assertFalse(c.needsChoice)
        assertEquals(BlockRole.TEXTO_NA_IMAGEM, r.decisions.first { it.text.startsWith("Taxa de 40%") }.role)
    }

    @Test fun postBodyStopsBeforeCountersAndRepliesFromOtherProfiles() {
        // Nome do perfil acima do @, letras do avatar ao lado, texto do post, fotos (espaço vazio), data, contadores e resposta.
        val blocks = listOf(
            b("RADAR VALDÓRIA", 206, 328, 380, 40),
            b("@RadarValdoria", 186, 386, 348, 34),
            b("WEW", 79, 398, 35, 18, conf = 0.3f),
            b("Show translation", 31, 488, 350, 33),
            b("URGENTE - Imagens mostram como foio jantar 'de gala' organizado por Lestan para Ardeo, diz O Correio", 29, 555, 1037, 50,
                listOf("URGENTE - Imagens mostram como foio", "jantar 'de gala' organizado por Lestan para", "Ardeo, diz O Correio")),
            b("10:05 · 30/09/26 · 21K Views", 29, 1447, 549, 42),
            b("9 12 L 113", 30, 1543, 406, 50),
            b("Ivo Lestan ® @ivolestan · 17m Pague seus impostos em dia, \"companheiro\".", 185, 1800, 896, 46,
                listOf("Ivo Lestan ® @ivolestan · 17m", "Pague seus impostos em dia, \"companheiro\".")),
        )
        val r = reader.read(blocks, 1170, 2532)
        assertTrue(r.socialPost)
        assertEquals("RADAR VALDÓRIA @RadarValdoria", r.postProfile)
        assertEquals("URGENTE - Imagens mostram como foio jantar 'de gala' organizado por Lestan para Ardeo, diz O Correio", r.body)
        val c = reader.claimFor(r)
        assertFalse("um post, uma sugestão: a resposta de outro perfil não vira opção", c.needsChoice)
        assertTrue(c.claim!!.startsWith("Imagens mostram como foio jantar"))
        assertTrue(c.alternatives.isEmpty())
    }

    @Test fun articleWithHandleInBylineKeepsItsTitle() {
        val blocks = listOf(
            b("Torvana aprova nova regra para vinhos importados", 40, 60, 620, 40, listOf("Torvana aprova nova regra", "para vinhos importados")),
            b("Por Ana Souza @anasouza", 40, 180, 300, 16),
            para("O parlamento de Torvana aprovou nesta terça a nova regra para os vinhos importados de Valdória.", 40, 230, 620, 16, 3),
        )
        val r = reader.read(blocks, 700, 400)
        assertEquals("Torvana aprova nova regra para vinhos importados", r.title)
        assertFalse(r.socialPost)
    }

    @Test fun postWithTwoUnrelatedEventsStillAsksWhichOne() {
        val blocks = postHeader(60) + listOf(
            para("Prefeitura de Valdória inaugura ponte sobre o rio Ardeo nesta segunda com presença do governador.", 150, 100, 620, 22, 3),
            para("Seleção de Torvana vence o torneio continental de vôlei por 3 sets a 1 contra a Merídia.", 150, 230, 620, 22, 3),
        )
        val r = reader.read(blocks, 800, 500)
        val c = reader.claimFor(r)
        assertTrue(r.socialPost)
        assertNull("dois assuntos: não escolhe por conta própria", c.claim)
        assertTrue(c.needsChoice)
        assertEquals(2, c.alternatives.size)
        assertTrue(c.alternatives[0].startsWith("Prefeitura de Valdória"))
        assertTrue(c.alternatives[1].startsWith("Seleção de Torvana"))
    }

    @Test fun postThatOpensDependingOnEarlierContextDoesNotGuess() {
        val blocks = postHeader(60) + listOf(
            para("Ele disse ontem que a medida vai ser revista pelo conselho de Valdória ainda neste mês.", 150, 100, 620, 22, 3),
            para("A decisão do conselho de Valdória deve sair até sexta, segundo a assessoria do órgão.", 150, 230, 620, 22, 3),
        )
        val c = reader.claimFor(reader.read(blocks, 800, 500))
        assertNull(c.claim)
        assertTrue(c.needsChoice)
    }

    @Test fun questionTitleSuggestsTheStatementBelowIt() {
        val blocks = listOf(
            b("Por que o governo mudou a regra do transporte?", 40, 40, 620, 36, listOf("Por que o governo mudou a", "regra do transporte?")),
            para("O Ministério dos Transportes alterou o prazo de renovação das concessões de ônibus interestaduais.", 40, 160, 620, 16, 3),
            para("Segundo a pasta, a mudança busca reduzir tarifas e ampliar a concorrência entre as empresas.", 40, 240, 620, 16, 3),
        )
        val r = reader.read(blocks, 700, 400)
        val c = reader.claimFor(r)
        assertNull(r.metadata.author)
        // A pergunta não é pesquisada como afirmação; a frase logo abaixo dá o foco.
        assertTrue(c.claim!!.startsWith("O Ministério dos Transportes alterou"))
        assertFalse(c.needsChoice)
        assertTrue(c.note!!.contains("pergunta"))
        assertTrue(c.alternatives.single().startsWith("Segundo a pasta"))
    }

    @Test fun questionTitleWithoutClearStatementStillAsks() {
        val blocks = listOf(
            b("Por que a regra mudou de novo?", 40, 40, 620, 36, listOf("Por que a regra mudou", "de novo?")),
            para("Ela passa a valer ainda neste mês, segundo pessoas que acompanham as conversas sobre o tema.", 40, 160, 620, 16, 3),
        )
        val c = reader.claimFor(reader.read(blocks, 700, 300))
        assertNull(c.claim)
        assertTrue(c.needsChoice)
    }
}
