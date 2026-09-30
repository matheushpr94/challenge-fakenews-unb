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
        assertTrue(c.needsChoice)
        assertNull(c.claim)
        assertFalse(r.body.contains("Proposições"))
        assertTrue(c.alternatives.first().startsWith("Começou a tramitar"))
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

    @Test fun questionTitleRequiresChoice() {
        val blocks = listOf(
            b("Por que o governo mudou a regra do transporte?", 40, 40, 620, 36, listOf("Por que o governo mudou a", "regra do transporte?")),
            para("O Ministério dos Transportes alterou o prazo de renovação das concessões de ônibus interestaduais.", 40, 160, 620, 16, 3),
            para("Segundo a pasta, a mudança busca reduzir tarifas e ampliar a concorrência entre as empresas.", 40, 240, 620, 16, 3),
        )
        val r = reader.read(blocks, 700, 400)
        val c = reader.claimFor(r)
        assertNull(r.metadata.author)
        assertNull(c.claim)
        assertTrue(c.needsChoice)
        assertTrue(c.alternatives.first().startsWith("O Ministério dos Transportes alterou"))
    }
}
