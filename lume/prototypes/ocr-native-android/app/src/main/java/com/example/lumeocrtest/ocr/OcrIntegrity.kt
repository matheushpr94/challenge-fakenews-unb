package com.example.lumeocrtest.ocr

import kotlin.math.abs

/**
 * Defesas contra erros de leitura do OCR. Nada aqui inventa texto: ou conserta um defeito mecânico
 * (maiúscula no meio da palavra), ou compara a manchete com o resto do que foi lido e avisa.
 */
object OcrTextRepair {
    /** Marcas escritas com minúscula inicial e maiúscula depois (iPhone, iFood…): não são defeito. */
    private val BRANDS = setOf("iphone", "ipad", "ipod", "ifood", "imac", "icloud", "itunes", "ebay", "ios", "esports")
    private val WORD = Regex("\\p{L}+")

    /** `asSociado` → `associado`, `goOvernador` → `governador`. Não mexe em links, @perfis, #hashtags nem siglas. */
    fun repairCase(text: String): String = text.split(Regex("(?<=\\s)|(?=\\s)")).joinToString("") { token ->
        if (token.isBlank() || token.any { it in "@#/_" } || token.contains("www.", true) || token.contains("://")) token
        else WORD.replace(token) { fixWord(it.value) }
    }

    internal fun fixWord(w: String): String {
        if (w.length < 4 || !w.first().isLowerCase()) return w
        val inner = w.drop(1).any { it.isUpperCase() }
        if (!inner || w.lowercase() in BRANDS) return w
        // "eBay", "iFood": só "i" ou "e" seguidos de uma maiúscula (senão "gOvernador" seria poupado).
        if (w[0] in "ie" && w.drop(1).count { it.isUpperCase() } == 1 && w[1].isUpperCase()) return w
        return w.lowercase()
    }
}

data class NumberFinding(val corrected: String?, val note: String)

/** Compara os números da manchete com os do subtítulo e do corpo lidos na mesma imagem. */
object NumberCheck {
    internal val NUM = Regex("(?<![\\p{L}\\d])\\d+(?:[.,]\\d+)*(?![\\p{L}\\d\\u00BA\\u00B0\\u00AA])")
    private const val MIN_CONTEXT = 80

    private fun canon(raw: String) = raw.replace(',', '.')
    private fun isYear(n: String) = n.length == 4 && n.all(Char::isDigit) && n.toInt() in 1900..2100
    private fun digits(n: String) = n.filter(Char::isDigit)

    fun numbers(text: String): List<String> = NUM.findAll(text).map { it.value }.toList()

    fun check(claim: String, context: String): NumberFinding? {
        if (context.length < MIN_CONTEXT) return null
        val inContext = NUM.findAll(context).map { it.value }.toList()
        val known = inContext.map(::canon).toSet()
        var corrected = claim
        val notes = mutableListOf<String>()
        val warn = mutableListOf<Pair<String, List<String>>>()
        for (m in NUM.findAll(claim).toList().reversed()) {
            val raw = m.value
            val c = canon(raw)
            if (c in known) continue
            val unit = Regex("^\\s+(\\p{L}+)").find(claim.substring(m.range.last + 1))?.groupValues?.get(1)?.lowercase()
            val near = inContext.filter { cand ->
                digits(cand) != digits(raw) && levenshtein(digits(cand), digits(raw)) == 1 && digits(raw).length >= 3 &&
                    (unit == null || Regex("\\b" + Regex.escape(cand) + "\\s+" + Regex.escape(unit) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(context))
            }.distinct()
            // Só corrige sozinho o defeito mecânico do OCR (um dígito repetido a mais: 2044 → 204). Números que
            // diferem por outro motivo (120 mil na manchete, 125 mil no texto) podem ser legítimos: só avisa.
            val duplicated = near.singleOrNull()?.takeIf { cand -> Regex("(\\d)\\1").findAll(raw).any { d -> raw.removeRange(d.range.last, d.range.last + 1) == cand } }
            when {
                duplicated != null -> { corrected = corrected.replaceRange(m.range, duplicated); notes += "“$raw” virou “$duplicated”, o número que aparece no texto da matéria" }
                isYear(raw) -> Unit
                near.isNotEmpty() -> warn += raw to near
                else -> warn += raw to emptyList()
            }
        }
        if (notes.isEmpty() && warn.isEmpty()) return null
        val parts = mutableListOf<String>()
        if (notes.isNotEmpty()) parts += "Corrigimos na manchete: " + notes.reversed().joinToString("; ") + "."
        if (warn.isNotEmpty()) {
            val list = warn.reversed().joinToString(", ") { (n, near) -> "“$n”" + if (near.isEmpty()) "" else " (o texto traz ${near.joinToString(" ou ") { "“$it”" }})" }
            parts += "O número $list da manchete não aparece no resto do texto lido; confira se foi lido certo."
        }
        return NumberFinding(corrected.takeIf { it != claim }, parts.joinToString(" "))
    }

    internal fun levenshtein(a: String, b: String): Int {
        if (abs(a.length - b.length) > 1) return 2
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length]
    }
}

/** Resultado do voto entre a leitura original da manchete e as releituras ampliadas. */
data class VoteResult(val title: String, val note: String?)

object NumberVote {
    /**
     * `rereads` = texto da manchete lido de novo em recortes ampliados. Só vale quando a releitura tem a mesma
     * quantidade de números da manchete (senão o recorte pegou outra coisa). Maioria entre as leituras vence;
     * sem maioria, mantém a original e avisa.
     */
    fun resolve(title: String, rereads: List<String>): VoteResult {
        val original = NumberCheck.numbers(title)
        if (original.isEmpty()) return VoteResult(title, null)
        val comparable = rereads.map { NumberCheck.numbers(it) }.filter { it.size == original.size }
        if (comparable.isEmpty() || comparable.all { it == original }) return VoteResult(title, null)
        val all = listOf(original) + comparable
        val winner = all.groupingBy { it }.eachCount().filterValues { it >= 2 && it * 2 > all.size }.keys.firstOrNull()
        if (winner != null && winner != original) {
            var out = title
            for ((m, new) in NumberCheck.NUM.findAll(title).toList().zip(winner).reversed())
                out = out.replaceRange(m.range, new)
            val changed = original.zip(winner).filter { (x, y) -> x != y }
            return VoteResult(out, "Relemos a manchete ampliada e corrigimos o número: " + changed.joinToString("; ") { (x, y) -> "“$x” virou “$y”" } + ".")
        }
        val seen = all.flatten().distinct().joinToString(", ") { "“$it”" }
        return VoteResult(title, "Os números da manchete mudaram entre leituras da mesma imagem ($seen); confira.")
    }
}

object OcrIntegrity {
    /** Confiança média (0 a 1) abaixo da qual a manchete é tratada como duvidosa; calibrada nas capturas reais dos testes. */
    const val LOW_TITLE_CONFIDENCE = 0.75f

    /** Aplica as conferências ao resultado da leitura. `rereads` vem da releitura ampliada (vazio = não feita). */
    fun verify(reading: ArticleReading, rereads: List<String> = emptyList()): ArticleReading {
        val title = reading.title ?: return reading
        val notes = reading.integrityNotes.toMutableList()
        var current = title
        NumberVote.resolve(current, rereads).let { v -> current = v.title; v.note?.let(notes::add) }
        val context = listOfNotNull(reading.subtitle, reading.body.takeIf { it.isNotBlank() }).joinToString("\n")
        NumberCheck.check(current, context)?.let { f -> f.corrected?.let { current = it }; notes += f.note }
        reading.titleConfidence?.takeIf { it < LOW_TITLE_CONFIDENCE }?.let {
            notes += "A manchete foi lida com baixa confiança pelo leitor de imagem; confira o texto."
        }
        return reading.copy(title = current, integrityNotes = notes.distinct())
    }
}
