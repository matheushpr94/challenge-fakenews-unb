package com.example.lumeocrtest.ocr

import org.junit.Test
import java.io.File

/** Relatório legível (blocos, papéis, motivos e campos) de cada captura real, gravado em build/lume-live. */
class ReaderDiagnosticsTest {
    @Test fun writeReadingReports() {
        val out = StringBuilder()
        for (name in RealCaptures.ALL + RealCaptures.MORE) {
            val f = OcrFixtures.load(name)
            val reader = ArticleReader()
            val reading = reader.read(f.blocks, f.width, f.height)
            val claim = reader.claimFor(reading)
            out.appendLine("===== $name (${f.width}x${f.height}, ${f.blocks.size} blocos)")
            out.append(reading.report())
            out.appendLine("afirmação=${claim.claim} | escolher=${claim.needsChoice} | nota=${claim.note}")
            out.appendLine("alternativas=${claim.alternatives}")
        }
        File("build/lume-live").mkdirs()
        File("build/lume-live/reading.txt").writeText(out.toString(), Charsets.UTF_8)
    }
}

object RealCaptures {
    val ALL = listOf("agencia-senado-bets", "bbc-fux-article", "agenciabrasil-bets", "camara-bets-pl",
        "g1-inadimplencia", "g1-inadimplencia-corpo", "recorte-senado-sem-titulo", "uol-flamengo-stf", "estadao-flamengo-stf")
    /** Capturas acrescentadas depois: uma página de notícia com título de serviço e três posts de rede social. */
    val MORE = listOf("metropoles-lei-seca", "x-post-almoco", "x-post-sobretaxa", "x-post-flamengo")
}
