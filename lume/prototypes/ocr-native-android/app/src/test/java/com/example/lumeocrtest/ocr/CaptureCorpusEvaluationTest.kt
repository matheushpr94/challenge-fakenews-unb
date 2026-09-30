package com.example.lumeocrtest.ocr

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

/**
 * Mede a leitura de capturas reais sem transformar falhas conhecidas em um teste verde artificial.
 * O relatório explicita OCR, tipo de conteúdo, foco e vazamento de elementos da interface.
 * A avaliação não classifica a veracidade das notícias ou dos posts.
 */
class CaptureCorpusEvaluationTest {
    private data class Case(
        val id: String,
        val kind: String,
        val scenario: String,
        val expectedFocus: String,
        val mustKeep: List<String>,
        val claimMustExclude: List<String>,
        val bodyMustExclude: List<String>,
    )

    private fun loadCases(): List<Case> {
        val stream = javaClass.classLoader!!.getResourceAsStream("ocr/evaluation-corpus.json")
            ?: error("Corpus de avaliação não encontrado")
        val root = stream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        fun strings(case: com.google.gson.JsonObject, field: String) =
            case.getAsJsonArray(field).map { it.asString }
        return root.getAsJsonArray("cases").map { entry ->
            val c = entry.asJsonObject
            Case(
                c.get("id").asString,
                c.get("kind").asString,
                c.get("scenario").asString,
                c.get("expectedFocus").asString,
                strings(c, "mustKeep"),
                strings(c, "claimMustExclude"),
                strings(c, "bodyMustExclude"),
            )
        }
    }

    private fun normalized(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("\\s+"), " ")

    @Test fun reportBaselineFromRealCaptures() {
        val cases = loadCases()
        assertTrue("O corpus não pode estar vazio", cases.isNotEmpty())
        assertEquals("IDs de casos repetidos", cases.size, cases.map { it.id }.toSet().size)
        val reader = ArticleReader()
        var ocrPassed = 0
        var typePassed = 0
        var focusPassed = 0
        var separationPassed = 0
        val report = StringBuilder("# Avaliação de capturas do Lume\n\n")
        report.append("Mede OCR e seleção do texto; **não avalia se o conteúdo é verdadeiro**. ")
            .append("As frases esperadas são apenas o foco visível nas capturas.\n\n")

        for (case in cases) {
            val fixture = OcrFixtures.load(case.id)
            val rawOcr = normalized(fixture.blocks.joinToString(" ") { it.text })
            val reading = reader.read(fixture.blocks, fixture.width, fixture.height)
            val choice = reader.claimFor(reading)
            val claim = choice.claim.orEmpty()
            val normalizedClaim = normalized(claim)
            val normalizedBody = normalized(reading.body)
            val ocrMissing = case.mustKeep.filterNot { rawOcr.contains(normalized(it)) }
            val focusMissing = case.mustKeep.filterNot { normalizedClaim.contains(normalized(it)) }
            val claimLeaks = case.claimMustExclude.filter { normalizedClaim.contains(normalized(it)) }
            val bodyLeaks = case.bodyMustExclude.filter { normalizedBody.contains(normalized(it)) }
            val typeOk = reading.socialPost == (case.kind == "post")
            val ocrOk = ocrMissing.isEmpty()
            val focusOk = focusMissing.isEmpty()
            val separationOk = claimLeaks.isEmpty() && bodyLeaks.isEmpty()
            if (ocrOk) ocrPassed++
            if (typeOk) typePassed++
            if (focusOk) focusPassed++
            if (separationOk) separationPassed++

            report.append("## ${case.id}\n\n")
                .append("- Cenário: ${case.scenario}\n")
                .append("- Foco esperado: ${case.expectedFocus}\n")
                .append("- Tipo: esperado=${case.kind}, observado=${if (reading.socialPost) "post" else "matéria"}, ${if (typeOk) "OK" else "FALHA"}\n")
                .append("- Título detectado: ${reading.title ?: "(nenhum)"}\n")
                .append("- Sugestão editável: ${choice.claim ?: "(pedir esclarecimento)"}\n")
                .append("- Foco: ${if (focusOk) "OK" else "FALHA"}; detalhes ausentes na sugestão: ${focusMissing.ifEmpty { listOf("nenhum") }.joinToString()}\n")
                .append("- OCR: ${if (ocrOk) "OK" else "FALHA"}; detalhes ausentes já no OCR: ${ocrMissing.ifEmpty { listOf("nenhum") }.joinToString()}\n")
                .append("- Mistura indevida: ${if (separationOk) "não detectada" else "FALHA"}; na sugestão: ${claimLeaks.ifEmpty { listOf("nenhuma") }.joinToString()}; no corpo: ${bodyLeaks.ifEmpty { listOf("nenhuma") }.joinToString()}\n")
                .append("- Etapa a investigar: ${when {
                    !ocrOk -> "OCR"
                    !typeOk -> "identificação de post ou matéria"
                    !focusOk -> "escolha da afirmação"
                    !separationOk -> "organização dos blocos"
                    else -> "nenhuma falha medida"
                }}\n")
                .append("- Pede esclarecimento: ${choice.needsChoice}\n\n")
        }

        report.insert(report.indexOf("## "),
            "**${cases.size} capturas**: OCR $ocrPassed/${cases.size}; tipo $typePassed/${cases.size}; foco $focusPassed/${cases.size}; separação $separationPassed/${cases.size}.\n\n")
        val file = File("build/lume-corpus/capture-baseline.md")
        requireNotNull(file.parentFile).mkdirs()
        file.writeText(report.toString())
        println("Relatório do corpus: ${file.absolutePath}")
        println("OCR $ocrPassed/${cases.size}; tipo $typePassed/${cases.size}; foco $focusPassed/${cases.size}; separação $separationPassed/${cases.size}")
        assertTrue("Relatório não foi criado", file.isFile && file.length() > 0)
    }
}
