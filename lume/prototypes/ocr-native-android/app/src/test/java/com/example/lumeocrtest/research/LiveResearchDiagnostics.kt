package com.example.lumeocrtest.research

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Diagnóstico com rede REAL (Google Notícias, Bing, Wikipédia e páginas). Não roda na suíte normal:
 * `LUME_LIVE=1 gradlew :app:testDebugUnitTest --tests '*LiveResearchDiagnostics*'`.
 * `LUME_LIVE_CLAIMS` (separadas por "||") troca as afirmações; `LUME_LIVE_SEMANTIC=1` usa a comparação de sentido do Ollama local.
 * O relatório vai para `app/build/lume-live/`.
 */
class LiveResearchDiagnostics {
    @Test fun liveTrace() {
        assumeTrue(System.getenv("LUME_LIVE") == "1")
        // Cada caso: captura real (fixture do OCR) -> leitura -> afirmação -> pesquisa com o contexto da matéria.
        val captures = System.getenv("LUME_LIVE_CAPTURES")?.split(",")?.map { it.trim() }
            ?: listOf("agencia-senado-bets", "bbc-fux-article", "g1-inadimplencia")
        val claims = captures.map { name ->
            val f = com.example.lumeocrtest.ocr.OcrFixtures.load(name)
            val reader = com.example.lumeocrtest.ocr.ArticleReader()
            val reading = reader.read(f.blocks, f.width, f.height)
            val choice = reader.claimFor(reading)
            val override = System.getenv("LUME_LIVE_CLAIM")?.takeIf { it.isNotBlank() }
            Triple(name, override ?: choice.claim ?: choice.alternatives.first(), ArticleContext(reading.title, reading.subtitle, reading.body,
                reading.metadata.source, reading.metadata.publishedAtMs, author = reading.metadata.author))
        }
        val semantic = if (System.getenv("LUME_LIVE_SEMANTIC") == "1") OllamaEmbeddingRanker() else null
        val service = ResearchService(semanticRanker = semantic)
        val out = File("build/lume-live").apply { mkdirs() }
        var lastCands: List<Candidate> = emptyList()
        claims.forEachIndexed { n, (name, claim, context) ->
            val report = StringBuilder()
            runBlocking {
                val c = service.clarify(claim)
                val text = if (c.precisaEscolher) claim else c.textoPesquisa
                report.appendLine("CAPTURA: $name | AFIRMAÇÃO: $claim")
                report.appendLine("esclarecimento: escolher=${c.precisaEscolher} opções=${c.opcoes.map { it.texto }} motivo=${c.motivo}")
                val ev = service.evaluate(text, "live-$n", context)
                report.appendLine("modo=${ev.modo} status=${ev.status}")
                ev.partes.forEach { r ->
                    val i = r.interpretacao
                    report.appendLine("--- parte: ${r.trechoDaEntrada ?: i.assunto}")
                    report.appendLine("tipo=${i.afirmacao?.tipo} entidades=${i.entidades} termos=${i.termos} numeros=${i.numeros.map { it.texto }}")
                    report.appendLine("partes=${i.partes} tópicos=${i.topicos.take(40)}")
                    report.appendLine("siglas=${i.aliases} variações=${i.vocabulario} prazos=${i.prazos} etapa=${i.etapa} dataRef=${i.dataReferencia?.let { Dates.format(it) }}")
                    report.appendLine("consultas=${i.consultas.map { "${it.finalidade}: ${it.texto}" }}")
                    report.appendLine("status=${r.status} síntese=${r.sintese?.situacao} descartados=${r.descartados} ${r.duracaoMs} ms semântica=${r.comparacaoSemanticaLocal}")
                    r.resultados.forEach { (k, v) -> report.appendLine("  seção $k: ${v.size} -> ${v.map { it.veiculo }}") }
                    report.append(r.diagnostico?.report().orEmpty())
                    lastCands = r.diagnostico?.candidatos.orEmpty()
                }
            }
            File(out, "live-$name.txt").writeText(report.toString(), Charsets.UTF_8)
            // Textos recebidos (título, trecho, página), para montar pares de regressão sem rede.
            val arr = com.google.gson.JsonArray()
            lastCands.forEach { c ->
                arr.add(com.google.gson.JsonObject().apply {
                    addProperty("veiculo", c.sourceName); addProperty("url", c.url); addProperty("dominio", c.domain)
                    addProperty("origem", c.origin); addProperty("consulta", c.query); addProperty("titulo", c.title)
                    addProperty("trecho", c.snippet); addProperty("data", c.published?.let { Dates.iso(it) })
                    addProperty("pagina", c.pageText.take(6000))
                })
            }
            File(out, "cands-$name.json").writeText(com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(arr), Charsets.UTF_8)
        }
    }
}
