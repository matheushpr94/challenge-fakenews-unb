package com.example.lumeocrtest.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticlePipelineRegressionTest {
    private fun block(text: String, x: Int, y: Int, w: Int, h: Int, lines: Int = 1): OcrBlock {
        val words = text.split(" ")
        val lineTexts = if (lines == 1) listOf(text) else words.chunked((words.size + lines - 1) / lines)
            .map { it.joinToString(" ") }
        return OcrBlock(text, OcrRect(x, y, x + w, y + h), lineTexts)
    }

    private fun read(blocks: List<OcrBlock>, w: Int, h: Int): Triple<ArticleLayout, ArticleMetadata, String> {
        val layout = ArticleLayoutAnalyzer().analyze(blocks, w, h)
        val metadata = ArticleMetadataExtractor().extractMetadata(blocks, layout.metadataIndexes, layout.headlineIndexes)
        return Triple(layout, metadata.metadata,
            ArticleTextExtractor().extractArticle(blocks, w, h, metadata.consumedBlockIndexes, layout))
    }

    @Test fun desktopLayoutUsesArticleColumnAndItsOwnByline() {
        val blocks = listOf(
            block("Economia", 40, 15, 130, 20),
            block("Senado aprova nova regra para energia solar", 45, 70, 490, 96, 2),
            block("Mais lidas", 625, 72, 150, 22),
            block("Ministro anuncia plano para energia solar em outra cidade", 625, 120, 330, 72, 3),
            block("Autor: Carlos Silva", 625, 198, 260, 18),
            block("Por Ana Souza", 45, 190, 180, 20),
            block("Publicado em 12 de maio de 2026", 45, 218, 290, 18),
            block("Os senadores aprovaram nesta terça-feira a nova regra que altera a compensação de créditos de energia solar.",
                45, 290, 490, 66, 3),
        )
        val (layout, metadata, body) = read(blocks, 1000, 900)
        assertEquals("Senado aprova nova regra para energia solar", layout.title)
        assertEquals("Ana Souza", metadata.author)
        assertNull(metadata.source)
        assertTrue(body.contains("Os senadores aprovaram"))
        assertFalse(body.contains("Carlos Silva"))
        assertFalse(body.contains("outra cidade"))
    }

    @Test fun mobileLayoutCombinesTitleButStopsAtRecommendations() {
        val blocks = listOf(
            block("Agência Horizonte", 30, 20, 330, 25),
            block("Cidade anuncia vacinação para crianças", 30, 95, 360, 75, 2),
            block("a partir da próxima semana", 30, 178, 320, 38),
            block("Unidades de saúde divulgarão o calendário por bairro", 30, 225, 340, 25),
            block("Por João Pereira", 30, 285, 210, 20),
            block("Fonte: Agência Horizonte", 30, 311, 290, 20),
            block("Publicado em 14/06/2026", 30, 337, 280, 20),
            block("A secretaria municipal informou que a campanha começará nas escolas e nos postos de saúde.",
                30, 410, 360, 70, 3),
            block("Leia também", 30, 525, 150, 20),
            block("Time local anuncia contratação de novo atacante para a próxima temporada.", 30, 555, 350, 70, 3),
            block("Fonte: Outro Portal", 30, 640, 220, 20),
        )
        val (layout, metadata, body) = read(blocks, 420, 900)
        assertEquals("Cidade anuncia vacinação para crianças a partir da próxima semana", layout.title)
        assertEquals("Unidades de saúde divulgarão o calendário por bairro", layout.subtitle)
        assertEquals("João Pereira", metadata.author)
        assertEquals("Agência Horizonte", metadata.source)
        assertTrue(body.contains("A secretaria municipal informou"))
        assertFalse(body.contains("Time local"))
    }

    @Test fun nearbyDateOrQuestionDoesNotInventAnAuthor() {
        val blocks = listOf(
            block("Por que o governo mudou a regra de transporte", 40, 60, 400, 90, 2),
            block("Publicado em 04/07/2026", 40, 170, 240, 20),
            block("O texto da medida foi divulgado na noite de sexta-feira pelo órgão responsável.", 40, 240, 400, 70, 3),
        )
        val (_, metadata, body) = read(blocks, 500, 700)
        assertNull(metadata.author)
        assertNull(metadata.source)
        assertEquals("04/07/2026", metadata.publishedAt)
        assertTrue(body.startsWith("Por que o governo"))
    }

    @Test fun rightHandArticleDoesNotUseLeftHandNavigation() {
        val blocks = listOf(
            block("Mais notícias", 35, 70, 200, 25),
            block("Deputado anuncia plano de transporte no interior", 35, 115, 300, 60, 3),
            block("Tribunal suspende contrato de transporte urbano", 505, 90, 460, 100, 2),
            block("Fonte: Jornal Capital", 505, 230, 270, 20),
            block("A decisão do tribunal suspendeu o contrato firmado pela prefeitura nesta semana.",
                505, 300, 460, 65, 3),
        )
        val (layout, metadata, body) = read(blocks, 1000, 800)
        assertEquals("Tribunal suspende contrato de transporte urbano", layout.title)
        assertEquals("Jornal Capital", metadata.source)
        assertFalse(body.contains("Deputado"))
    }

    @Test fun unlabeledMastheadIsMarkedAsTentativeNotInvented() {
        val blocks = listOf(
            block("Portal Cidade", 30, 20, 250, 25),
            block("Hospital amplia atendimento infantil nesta semana", 30, 90, 390, 86, 2),
            block("A unidade informou que terá novos horários de atendimento para famílias.", 30, 280, 390, 65, 3),
        )
        val (_, metadata, _) = read(blocks, 460, 700)
        assertEquals("Portal Cidade", metadata.source)
        assertEquals(MetadataOrigin.HEADER_CANDIDATE, metadata.sourceOrigin)
        assertNull(metadata.author)
    }

    @Test fun largeSidebarPromotionDoesNotBeatHeadlineWithArticleBody() {
        val blocks = listOf(
            block("Cidade amplia rede de ônibus elétricos", 40, 80, 490, 88, 2),
            block("PROMOÇÃO IMPERDÍVEL NA SEMANA", 650, 80, 320, 65),
            block("A prefeitura apresentou novas linhas de ônibus elétricos para os bairros da zona norte.",
                40, 270, 490, 70, 3),
            block("O projeto prevê veículos adicionais e divulgação das rotas até o final do mês.",
                40, 365, 490, 68, 3),
            block("As equipes técnicas publicarão o cronograma após a consulta com moradores.",
                40, 455, 490, 68, 3),
        )
        val (layout, _, body) = read(blocks, 1000, 850)
        assertEquals("Cidade amplia rede de ônibus elétricos", layout.title)
        assertFalse(body.contains("PROMOÇÃO"))
    }
}
