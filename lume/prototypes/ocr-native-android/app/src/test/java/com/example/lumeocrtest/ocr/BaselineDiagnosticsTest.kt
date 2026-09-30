package com.example.lumeocrtest.ocr

import org.junit.Test

/** Reprodução exata do runOcr antigo (MainActivity) sobre as fixtures reais, só para registrar o "antes". */
class BaselineDiagnosticsTest {
    @Test fun printBaseline() {
        val sb = StringBuilder()
        fun println(x: Any?) { sb.appendLine(x.toString()) }
        for (name in listOf("agencia-senado-bets", "bbc-fux-article", "agenciabrasil-bets", "camara-bets-pl", "g1-inadimplencia", "g1-inadimplencia-corpo", "recorte-senado-sem-titulo")) {
            val f = OcrFixtures.load(name)
            val layout = ArticleLayoutAnalyzer().analyze(f.blocks, f.width, f.height)
            val md = ArticleMetadataExtractor().extractMetadata(f.blocks, layout.metadataIndexes, layout.headlineIndexes)
            val cleaned = ArticleTextExtractor().extractArticle(f.blocks, f.width, f.height, md.consumedBlockIndexes, layout)
            val claims = ClaimExtractor().extractClaims(cleaned)
            val titleClaim = layout.title?.takeIf { cleaned.startsWith(it) && it.length >= 20 }
            println("===== $name")
            println("main=${layout.mainIndexes.sorted()} headline=${layout.headlineIndexes} metaIdx=${layout.metadataIndexes.sorted()}")
            println("title=${layout.title}\nsubtitle=${layout.subtitle}")
            println("metadata=${md.metadata}\nconsumed=${md.consumedBlockIndexes}")
            println("BODY>>>\n$cleaned\n<<<")
            println("mainClaim=${titleClaim ?: claims.mainClaim}")
        }
        java.io.File("build/lume-live").mkdirs()
        java.io.File("build/lume-live/baseline-extraction.txt").writeText(sb.toString(), Charsets.UTF_8)
    }
}
