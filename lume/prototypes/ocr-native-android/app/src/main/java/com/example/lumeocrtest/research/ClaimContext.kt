package com.example.lumeocrtest.research

/*
 * Contexto que vem da própria matéria lida na captura (título, subtítulo, corpo, veículo e data).
 * Serve para: resolver siglas ("MP" ↔ "medida provisória") pelo texto da própria matéria, descobrir as
 * palavras que o próprio texto usa para o mesmo assunto (variação de redação) e situar a data.
 * Nada aqui vem de listas de temas, veículos ou pessoas: tudo é derivado do texto recebido.
 */

data class ArticleContext(
    val title: String? = null,
    val subtitle: String? = null,
    val body: String = "",
    val source: String? = null,
    /** Data de publicação lida na captura (meia-noite UTC), quando reconhecida. */
    val publishedAtMs: Long? = null,
    /** Autor individual lido na assinatura, quando houver. */
    val author: String? = null,
    /** Endereço da matéria importada, quando a pessoa colou um link. */
    val url: String? = null,
) {
    val fullText: String get() = listOfNotNull(title, subtitle, body).joinToString("\n")
}

private val ACRONYM_TOKEN = Regex("(?<![\\p{L}\\p{N}])(\\p{Lu}{2,6})(?![\\p{L}\\p{N}])")
private val WORDS = Regex("[\\p{L}][\\p{L}\\-]*")

/** Duração ou prazo ("em 30 dias", "no prazo de 120 dias"): é um detalhe do fato, não uma contagem. */
private val TIME_UNITS = setOf("dia", "dias", "mes", "meses", "semana", "semanas", "hora", "horas", "minuto", "minutos", "ano", "anos")
private val DURATION_BEFORE = Regex("(?iu)(?:\\bem|\\bpor|\\baté|\\bate|dentro de|prazo de|\\bapós|\\bapos|daqui a|\\bdurante|\\bhá|\\bha|\\bcom)\\s+(?:até\\s+|cerca de\\s+)?$")

fun isDuration(q: Quantity, text: String): Boolean {
    if (norm(q.unidade) !in TIME_UNITS) return false
    val raw = q.texto ?: return false
    val at = text.indexOf(raw.substringBefore(" "))
    if (at < 0) return false
    return DURATION_BEFORE.containsMatchIn(text.substring(0, at))
}

/** Prazos/durações citados na frase ("30 dias"). */
fun durationsIn(text: String): List<String> = extractQuantities(text).filter { isDuration(it, text) }.mapNotNull { it.texto }

/**
 * Siglas da afirmação resolvidas pelo próprio texto: "Nome Longo (SIGLA)" ou uma sequência de palavras cujas
 * iniciais formam a sigla. Também o inverso (nome longo na afirmação, sigla no texto).
 */
fun resolveAliases(claim: String, context: String): Map<String, List<String>> {
    if (context.isBlank()) return emptyMap()
    val out = LinkedHashMap<String, MutableList<String>>()
    val acronyms = ACRONYM_TOKEN.findAll(claim).map { it.groupValues[1] }.filter { it.any(Char::isLetter) }.toSet()
    val ctxWords = WORDS.findAll(context).map { it.value }.toList()
    for (acr in acronyms) {
        val counts = HashMap<String, Int>()
        Regex("([\\p{L}][\\p{L}\\s\\-]{3,80}?)\\s*\\(\\s*${Regex.escape(acr)}\\s*\\)").findAll(context).forEach { m ->
            val words = m.groupValues[1].trim().split(Regex("\\s+"))
            phraseWithInitials(words.takeLast(acr.length + 3), acr)?.let { counts[it] = (counts[it] ?: 0) + 3 }
        }
        for (start in ctxWords.indices) {
            val cand = phraseStartingAt(ctxWords, start, acr) ?: continue
            counts[cand] = (counts[cand] ?: 0) + 1
        }
        counts.entries.sortedByDescending { it.value }.take(2).forEach { out.getOrPut(acr) { mutableListOf() }.add(it.key) }
    }
    // Inverso: "Supremo Tribunal Federal" na afirmação e "STF" no texto.
    for (ent in extractEntities(claim)) {
        val words = ent.split(Regex("\\s+")).filter { norm(it) !in CONNECTORS }
        if (words.size < 2) continue
        val acr = words.joinToString("") { it.take(1) }.uppercase()
        if (Regex("(?<![\\p{L}])${Regex.escape(acr)}(?![\\p{L}])").containsMatchIn(context)) out.getOrPut(ent) { mutableListOf() }.add(acr)
    }
    return out
}

private fun phraseStartingAt(words: List<String>, start: Int, acr: String): String? {
    val picked = mutableListOf<String>()
    var i = start
    var letter = 0
    while (i < words.size && letter < acr.length) {
        val w = words[i]
        if (picked.isNotEmpty() && norm(w) in CONNECTORS) { picked += w; i++; continue }
        if (!w.first().equals(acr[letter], ignoreCase = true) || w.length < 3 || norm(w) in STOP_NORM) return null
        picked += w; letter++; i++
    }
    if (letter < acr.length) return null
    val phrase = picked.joinToString(" ")
    return phrase.takeIf { phrase.length > acr.length + 3 && !phrase.all { it.isUpperCase() || !it.isLetter() } }?.lowercase()
}

private fun phraseWithInitials(words: List<String>, acr: String): String? {
    for (s in words.indices) phraseStartingAt(words, s, acr)?.let { return it }
    return null
}

/**
 * Palavras que a própria matéria repete para o assunto da afirmação, sem estar nela ("apostas" quando o título
 * diz "bets"). Só entram termos frequentes em frases que compartilham palavras com a afirmação.
 */
fun contextVocabulary(claim: String, context: String, exclude: Set<String>, max: Int = 3): List<String> {
    if (context.isBlank()) return emptyList()
    val claimStems = stemsOf(claim)
    val counts = LinkedHashMap<String, Pair<Int, String>>()
    for (sentence in sentencesOf(context)) {
        val stems = stemsOf(sentence)
        if ((stems intersect claimStems).isEmpty()) continue
        for (m in WORD_RE.findAll(sentence)) {
            val n = norm(m.value)
            if (n.length < 5 || n in STOP_NORM || n in exclude || m.value.first().isUpperCase() && m.range.first > 0 &&
                sentence[m.range.first - 1] != ' ') continue
            if (m.value.all { it.isUpperCase() } || n.any(Char::isDigit)) continue
            val st = stem(n)
            if (st in claimStems || exclude.any { stem(it) == st }) continue
            val prev = counts[st]
            counts[st] = (prev?.first ?: 0) + 1 to (prev?.second ?: m.value.lowercase())
        }
    }
    return counts.values.filter { it.first >= 2 }.sortedByDescending { it.first }.map { it.second }.take(max)
}

/** Radicais das frases do texto que citam os mesmos números da afirmação: dizem “de que” é o número. */
fun detailStems(claim: String, context: String): Set<String> {
    val nums = numbersIn(claim).filter { it.length >= 1 }
    if (nums.isEmpty()) return emptySet()
    val sentences = sentencesOf(claim) + sentencesOf(context).filter { s -> numbersIn(s).any { it in nums } }
    return sentences.flatMap { s -> windowAround(s, nums, 5) }
        .filter { it.length >= 4 && it !in STOP_NORM && !it.any(Char::isDigit) && it !in GENERIC_DETAIL }.map { stem(it) }.toSet()
}

/** Palavras (normalizadas) a até `radius` posições de um dos números. */
fun windowAround(text: String, numberKeys: Collection<String>, radius: Int): List<String> {
    val toks = tokens(text)
    val out = mutableListOf<String>()
    toks.forEachIndexed { i, t -> if (numberKey(t) in numberKeys) out += toks.subList(maxOf(0, i - radius), minOf(toks.size, i + radius + 1)) }
    return out
}

private val GENERIC_DETAIL = setOf("dias", "meses", "anos", "horas", "semanas", "prazo", "texto", "sexta", "feira")

/**
 * Etapa do ato descrito: uma proposta não equivale a uma decisão tomada, que não equivale a entrada em vigor
 * nem a revogação. Retorna o rótulo da etapa ou null quando o texto não indica.
 */
fun actStage(text: String): String? {
    val t = " ${norm(text)} "
    val stages = listOf(
        "revogado" to Regex(" (revoga|revogou|revogad\\w*|derruba|derrubou|derrubad\\w*|anula|anulou|anulad\\w*|suspende|suspendeu|suspens\\w*|barra|barrou|veta|vetou|vetad\\w*) "),
        "negado" to Regex(" (rejeita|rejeitou|rejeitad\\w*|nega|negou|arquiva|arquivou|arquivad\\w*) "),
        "vigente" to Regex(" (entra em vigor|entrou em vigor|passa a valer|passou a valer|em vigor|sanciona|sancionou|sancionad\\w*|promulga|promulgou) "),
        "aprovado" to Regex(" (aprova|aprovou|aprovad\\w*) "),
        "proposto" to Regex(" (propoe|propos|proposta|propostas|projeto de lei|pl|pretende|planeja|estuda|quer|deve votar|apresenta|apresentou|apresentad\\w*|avalia|discute) "),
        "decidido" to Regex(" (determina|determinou|proibe|proibiu|proibid\\w*|decreta|decretou|decide|decidiu|edita|editou|assina|assinou|publica|publicou|obriga|obrigou) "),
        "previsto" to Regex(" (preve|previu|previst\\w*|vai|devera|deverao) "),
    )
    return stages.firstOrNull { (_, re) -> re.containsMatchIn(t) }?.first
}
