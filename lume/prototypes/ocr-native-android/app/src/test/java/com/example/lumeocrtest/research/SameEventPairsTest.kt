package com.example.lumeocrtest.research

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regressão de pares difíceis: mesmo acontecimento × detalhe divergente × contexto × outro × incerto,
 * além da própria matéria importada e de republicações.
 */
class SameEventPairsTest {
    /** Fluxo anterior (triagem por termos + relação): usado só para registrar o "antes". */
    private fun baseline(p: HardPair): String {
        val interp = interpret(p.claim, HardPairs.nowFor(p), p.original)
        val a = assess(interp, p.candidate, HardPairs.nowFor(p), p.candidate.pageText)
        return when (relate(interp, p.candidate, a).tipo) {
            RelationKind.MESMO_FATO -> "mesmo_acontecimento"
            RelationKind.DIFERENTE -> "divergente"
            RelationKind.CONTEXTO, RelationKind.ANTERIOR -> "contexto"
            RelationKind.OUTRO -> "outro"
            else -> "incerta"
        }
    }

    /** Fluxo novo: independência da fonte e comparação estruturada do acontecimento (sem IA). */
    private fun classify(p: HardPair): Pair<String, EventComparison?> {
        independenceOf(p.original, p.candidate)?.let { return it.kind to null }
        val cmp = compareEvent(originalEvent(p.claim, p.original, HardPairs.nowFor(p)), p.candidate, HardPairs.nowFor(p))
        return label(cmp.kind) to cmp
    }

    private fun label(kind: String) = when (kind) {
        EventRelation.MESMO -> "mesmo_acontecimento"
        EventRelation.DIVERGENTE -> "divergente"
        EventRelation.CONTEXTO -> "contexto"
        EventRelation.OUTRO -> "outro"
        else -> "incerta"
    }

    @Test fun hardPairsAfter() {
        val pairs = HardPairs.load()
        val out = StringBuilder()
        val failures = mutableListOf<String>()
        for (p in pairs) {
            val before = baseline(p)
            val (after, cmp) = classify(p)
            val ok = after in p.accepted
            if (!ok) failures.add("${p.id}: esperado ${p.accepted}, obtido $after")
            out.appendLine("${if (ok) "ok  " else "ERRO"} ${p.id} [${p.tipo}] esperado=${p.expected} antes=$before depois=$after")
            out.appendLine("     fonte: ${p.candidate.sourceName} — ${p.candidate.title}")
            out.appendLine("     por quê (rótulo manual): ${p.why}")
            cmp?.let { c ->
                out.appendLine("     resumo: ${c.resumo} [leitura: ${c.base}]")
                c.motivos.forEach { out.appendLine("       · $it") }
                c.trechos.forEach { out.appendLine("       « ${it.onde}: ${it.texto.take(220)} »") }
            } ?: out.appendLine("     independência: ${independenceOf(p.original, p.candidate)?.motivo}")
        }
        val okCount = pairs.size - failures.size
        out.insert(0, "DEPOIS: $okCount/${pairs.size} corretos\n\n")
        File("build/lume-live").mkdirs()
        File("build/lume-live/pares-depois.txt").writeText(out.toString(), Charsets.UTF_8)
        // Detalhe numérico com "%" conferido no resumo do buscador (par só com resumo).
        val iptu = pairs.first { it.id == "s-so-resumo-mesmo" }
        val cmp = compareEvent(originalEvent(iptu.claim, iptu.original, HardPairs.nowFor(iptu)), iptu.candidate, HardPairs.nowFor(iptu))
        if (cmp.motivos.none { it.startsWith("Detalhe: cita 12%") }) failures.add("s-so-resumo-mesmo: não conferiu 12% (${cmp.motivos})")
        assertTrue("Pares com classificação errada:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun baselineReport() {
        val pairs = HardPairs.load()
        val out = StringBuilder()
        var ok = 0
        for (p in pairs) {
            val b = baseline(p)
            val hit = b in p.accepted
            if (hit) ok++
            out.appendLine("${if (hit) "ok  " else "ERRO"} ${p.id} [${p.tipo}] esperado=${p.expected} antes=$b | ${p.candidate.sourceName}: ${p.candidate.title.take(90)}")
        }
        out.insert(0, "ANTES (fluxo anterior): $ok/${pairs.size} corretos\n")
        File("build/lume-live").mkdirs()
        File("build/lume-live/pares-antes.txt").writeText(out.toString(), Charsets.UTF_8)
        // Leitura das capturas (como o app faz), para a avaliação dos modelos fora do app.
        val caps = com.google.gson.JsonObject()
        for (name in listOf("uol-flamengo-stf", "estadao-flamengo-stf")) {
            val (ctx, claim) = HardPairs.fromCapture(name)
            caps.add(name, com.google.gson.JsonObject().apply {
                addProperty("afirmacao", claim); addProperty("subtitulo", ctx.subtitle); addProperty("corpo", ctx.body)
                addProperty("veiculo", ctx.source); addProperty("autor", ctx.author)
            })
        }
        File("build/lume-live/capturas-lidas.json").writeText(caps.toString(), Charsets.UTF_8)
    }
}
