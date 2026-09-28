package com.example.lumeocrtest.research

import java.util.Calendar
import java.util.TimeZone

/*
 * Interpretação da entrada por regras: entidades, termos do acontecimento, números, datas,
 * negações e consultas. Não usa modelo.
 */

const val MAX_INPUT = 5000
val CONNECTORS = setOf("de", "da", "do", "das", "dos", "del")

/** Palavras genéricas que não descrevem o acontecimento em si. */
private val GENERIC: Set<String> = """aconteceu acontece acontecer ocorreu ocorre noticia noticias notícia
notícias informação informacao caso assunto polêmica polemica sabe saber alguém alguem pessoa
pessoas gente hoje ontem agora semana ano anos mês mes dia dias recentemente vídeo video post
publicação print foto mil milhão milhao milhões milhoes bilhão bilhao bilhões bilhoes reais
dólares dolares cento existe existem existir existia existiam pode podem poderia situação situacao situações
situacoes alguma algum""".split(Regex("\\s+")).filter { it.isNotBlank() }.map { norm(it) }.toSet()

val BYLINE_RE = Regex("(?:^|(?<=[.\\n]))\\s*(?:Por|By)\\s+[A-ZÀ-Ý][\\p{L}\\p{N}_'’]+(?:\\s+(?:d[aeo]s?\\s+)?[A-ZÀ-Ý][\\p{L}\\p{N}_'’]+)*\\s*[,|–-]?",
    RegexOption.MULTILINE)
private val TIME_WORDS: Set<String> = MONTHS.keys.map { norm(it) }.toSet() + WEEKDAYS.map { norm(it) } + "feira"

private val FRAMING_RE = Regex(
    "^\\s*(é|e)?\\s*(verdade|fato|real)\\s+que\\s+|^\\s*ser[aá]\\s+que\\s+|^\\s*procede\\s+que\\s+|" +
        "^\\s*(eu\\s+)?(vi|li|ouvi|recebi)\\s+(dizer\\s+)?que\\s+|^\\s*(dizem|disseram|falaram|estão\\s+dizendo|" +
        "est[aã]o\\s+falando)\\s+que\\s+|^\\s*(é|e)\\s+verdade\\s*[:,-]?\\s*", RegexOption.IGNORE_CASE)
private val RELATIVE_RE = Regex(
    "$WB(hoje|ontem|anteontem|agora h[aá] pouco|nesta semana|esta semana|neste fim de semana|" +
        "recentemente|acaba de|acabou de|nest[ae] (?:segunda|ter[çc]a|quarta|quinta|sexta|s[aá]bado|" +
        "domingo)(?:-feira)?)$WE", RegexOption.IGNORE_CASE)
private val MONTH_ALT = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")
private val DMY_RE = Regex("$WB(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?$WE")
private val DMONTHY_RE = Regex("$WB(\\d{1,2})º?\\s+de\\s+($MONTH_ALT)(?:\\s+de\\s+(\\d{4}))?$WE", RegexOption.IGNORE_CASE)
private val MONTHY_RE = Regex("$WB($MONTH_ALT)\\s+de\\s+(\\d{4})$WE", RegexOption.IGNORE_CASE)
private val NUMBER_RE = Regex(
    "(?:R\\$\\s*|US\\$\\s*)?$WB\\d+(?:[.,]\\d+)*$WE(?:\\s*(?:%|por cento|mil$WE|milh(?:ão|ões|ao|oes)|" +
        "bilh(?:ão|ões|ao|oes)|trilh(?:ão|ões|ao|oes)|reais|d[oó]lares|pessoas|mortos|anos))?", RegexOption.IGNORE_CASE)
private val QUESTION_RE = Regex("^\\s*(quem|qual|quais|quando|onde|como|por que|porque|o que|é verdade|será)$WE",
    RegexOption.IGNORE_CASE)
private val SENTENCE_SPLIT = Regex("(?<=[.!?])\\s+|\\n+")
private val STARTS_SENTENCE = Regex("(?:[.!?:\"“(]|\\n)\\s*$")

data class NumberMention(val texto: String, val chave: String)
data class DateMention(val texto: String, val dia: Int?, val mes: Int?, val ano: Int?)
data class SearchQuery(val texto: String, val finalidade: String)

data class Interpretation(
    val textoOriginal: String,
    val assunto: String,
    val tipo: String,
    val entidades: List<String> = emptyList(),
    val termos: List<String> = emptyList(),
    val numeros: List<NumberMention> = emptyList(),
    val datas: List<DateMention> = emptyList(),
    val anos: List<Int> = emptyList(),
    val negacoes: List<String> = emptyList(),
    val tempoRelativo: List<String> = emptyList(),
    val incompleto: Boolean = false,
    val consultas: List<SearchQuery> = emptyList(),
    val consultaContexto: String = "",
    val consultaContextoPropriedade: String = "",
    val afirmacao: Afirmacao? = null,
    val quantity: Quantity? = null,
)

fun cleanInput(text: String?): String {
    var t = (text ?: "").replace("\r", "\n").take(MAX_INPUT)
    t = t.replace(Regex("[ \\t\\u00a0]+"), " ")
    t = t.replace(Regex("\\n{2,}"), "\n").trim()
    var prev: String? = null
    while (prev != t) {
        prev = t
        t = FRAMING_RE.replaceFirst(t, "").trim()
    }
    return t.trim(' ', '?', '¿', '!', '-', '–', '—', ':')
}

fun sentencesOf(text: String): List<String> = text.split(SENTENCE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }

private fun startsSentence(text: String, start: Int) = start == 0 || STARTS_SENTENCE.containsMatchIn(text.substring(0, start))

fun extractEntities(text: String): List<String> {
    val toks = WORD_RE.findAll(text).map { Triple(it.value, it.range.first, it.range.last + 1) }.toList()
    val alpha = toks.map { it.first }.filter { it.first().isLetter() && it.length >= 3 }
    if (alpha.isNotEmpty() && alpha.count { it.isAllUpper() }.toDouble() / alpha.size > 0.6) {
        return emptyList() // texto todo em maiúsculas: a capitalização não indica nomes próprios
    }
    val ents = mutableListOf<String>()
    val cur = mutableListOf<Int>()
    fun flush() {
        while (cur.isNotEmpty() && norm(toks[cur.last()].first) in CONNECTORS) cur.removeAt(cur.size - 1)
        if (cur.isNotEmpty()) {
            val first = toks[cur.first()]
            val phrase = text.substring(first.second, toks[cur.last()].third)
            val acronym = cur.size == 1 && first.first.isAllUpper() && first.first.length >= 2
            if (cur.size > 1 || acronym || !startsSentence(text, first.second)) {
                val n = norm(phrase)
                if (n.length >= 2 && n !in STOP_NORM && n !in TIME_WORDS) ents.add(phrase)
            }
        }
        cur.clear()
    }
    for ((i, tok) in toks.withIndex()) {
        val (t, s, _) = tok
        val contiguous = cur.isEmpty() || text.substring(toks[cur.last()].third, s).isBlank()
        if (!contiguous) flush()
        val low = norm(t)
        if (t[0].isUpperCase() && !t[0].isDigit() && low !in STOP_NORM) {
            cur.add(i)
        } else if (cur.isNotEmpty() && low in CONNECTORS && i + 1 < toks.size && toks[i + 1].first[0].isUpperCase() &&
            text.substring(toks[i].third, toks[i + 1].second).isBlank()) {
            cur.add(i)
        } else {
            flush()
        }
    }
    flush()
    val out = mutableListOf<String>()
    for (e in ents.sortedByDescending { it.length }) {
        val n = norm(e)
        if (out.none { n == norm(it) || " $n " in " ${norm(it)} " }) out.add(e)
    }
    return out.sortedBy { text.indexOf(it) }
}

private fun termsOf(main: String, rest: String, entityTokens: Set<String>): List<String> {
    val scores = LinkedHashMap<String, Int>()
    val order = HashMap<String, Pair<Int, String>>()
    for ((weight, chunk) in listOf(2 to main, 1 to rest)) {
        for (m in WORD_RE.findAll(chunk)) {
            val n = norm(m.value)
            if (n.length < 3 || n in STOP_NORM || n in NEG_NORM || n in GENERIC || n in TIME_WORDS ||
                n in entityTokens || Regex("[\\d,]+").matches(n)) continue
            val key = stem(n)
            if (key !in scores) {
                scores[key] = 0
                order[key] = order.size to m.value.lowercase()
            }
            scores[key] = scores.getValue(key) + weight
        }
    }
    return scores.keys.sortedWith(compareBy({ -scores.getValue(it) }, { order.getValue(it).first }))
        .map { order.getValue(it).second }
}

private fun datesOf(text: String): List<DateMention> {
    val found = mutableListOf<DateMention>()
    fun add(d: DateMention) { if (found.none { d.texto in it.texto }) found.add(d) }
    for (m in DMY_RE.findAll(text)) {
        val dia = m.groupValues[1].toInt()
        val mes = m.groupValues[2].toInt()
        if (dia !in 1..31 || mes !in 1..12) continue
        val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) it + 2000 else it }
        add(DateMention(m.value, dia, mes, y))
    }
    for (m in DMONTHY_RE.findAll(text)) {
        add(DateMention(m.value, m.groupValues[1].toInt(), MONTHS[m.groupValues[2].lowercase()],
            m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()))
    }
    for (m in MONTHY_RE.findAll(text)) add(DateMention(m.value, null, MONTHS[m.groupValues[1].lowercase()], m.groupValues[2].toInt()))
    return found
}

fun interpret(text: String?, nowMs: Long = System.currentTimeMillis()): Interpretation {
    val nowYear = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMs }.get(Calendar.YEAR)
    val clean = cleanInput(text)
    val original = (text ?: "").trim().take(MAX_INPUT)
    val isQuestion = original.trimEnd().endsWith("?") || QUESTION_RE.containsMatchIn(original)
    val sents = sentencesOf(clean)
    val main = sents.firstOrNull { s -> tokens(s).count { it !in STOP_NORM } >= 3 } ?: sents.firstOrNull() ?: ""
    val rest = sents.filter { it !== main }.joinToString(" ")
    val mainN = " ${norm(main)} "
    val ents = extractEntities(BYLINE_RE.replace(clean, " "))
        .sortedBy { if (" ${norm(it)} " in mainN) 0 else 1 } // entidades da frase principal primeiro
    val mainEnts = ents.filter { " ${norm(it)} " in mainN }
    val entTokens = tokens(ents.joinToString(" ")).toSet()
    val terms = termsOf(main, rest, entTokens).take(6)

    val dates = datesOf(clean)
    val dateSpans = dates.joinToString(" ") { it.texto }
    val years = (YEAR_RE.findAll(clean).map { it.groupValues[1].toInt() }.toSet() + dates.mapNotNull { it.ano }).sorted()
    val nums = mutableListOf<NumberMention>()
    for (m in NUMBER_RE.findAll(clean)) {
        val raw = m.value.trim()
        val key = numberKey(raw)
        if (key.isEmpty() || raw in dateSpans || (key.all { it.isDigit() } && key.length == 4 && key.toInt() in years)) continue
        if (nums.none { it.chave == key }) nums.add(NumberMention(raw, key))
    }
    val words = WORD_RE.findAll(clean).map { it.value }.toList()
    val negs = words.indices.filter { norm(words[it]) in NEG_NORM }.map { words.subList(it, minOf(words.size, it + 4)).joinToString(" ") }
    val relative = RELATIVE_RE.findAll(original).map { it.value }.toList()

    var interp = Interpretation(
        textoOriginal = original, assunto = main.ifEmpty { clean }, tipo = if (isQuestion) "pergunta" else "afirmacao",
        entidades = ents.take(6), termos = terms, numeros = nums.take(4), datas = dates.take(3), anos = years.take(3),
        negacoes = negs.take(3), tempoRelativo = relative.take(2),
    )
    val (afirmacao, quantity) = structure(interp, main.ifEmpty { clean }, nowYear)
    // Incompleta = sem acontecimento ou propriedade identificável (não por falta de resultados).
    val incompleto = quantity == null && (terms.isEmpty() || ents.size + terms.size <= 1)
    interp = interp.copy(afirmacao = afirmacao, quantity = quantity, incompleto = incompleto)
    interp = interp.copy(
        consultas = buildQueries(interp, clean, mainEnts.ifEmpty { ents }),
        consultaContexto = ents.firstOrNull() ?: terms.take(2).joinToString(" "),
    )
    return interp.copy(consultaContextoPropriedade = contextQuery(interp))
}

/** Consulta de contexto voltada ao detalhe: entidade + propriedade (+ período). */
fun contextQuery(interp: Interpretation): String {
    val q = interp.quantity
    val ent = interp.entidades.take(1)
    val entTokens = interp.entidades.flatMap { norm(it).split(" ") }.toSet()
    val prop = mutableListOf<String>()
    if (q != null) {
        if (q.chaveUnidade != "%") prop.add(q.unidade)
        prop.addAll(q.qualificadores)
        if (q.chaveUnidade == "%" || ent.isEmpty()) {
            prop.addAll(interp.termos.filter { stem(it) != q.chaveUnidade && norm(it) !in entTokens }.take(1))
        }
    } else {
        prop.addAll(interp.termos.filter { norm(it) !in entTokens }.take(2))
    }
    if (prop.isEmpty()) return ""
    return joinQuery(ent + prop + interp.anos.take(1).map { it.toString() }, 8)
}

fun joinQuery(parts: List<String>, maxWords: Int = 10): String {
    val seen = HashSet<String>()
    val out = mutableListOf<String>()
    for (p in parts) for (w in p.split(Regex("\\s+"))) {
        val n = norm(w)
        if (n.isNotEmpty() && seen.add(n)) out.add(w.trim(',', '.', ';', ':', '!', '?', '"', '“', '”', '(', ')'))
    }
    return out.take(maxWords).joinToString(" ").trim()
}

/** Poucas consultas complementares: específica -> acontecimento/valor -> ampliada. */
fun buildQueries(interp: Interpretation, clean: String, ents: List<String>): List<SearchQuery> {
    val terms = interp.termos
    val nums = interp.numeros.take(1).map { it.texto }
    val years = interp.anos.take(1).map { it.toString() }
    val q = interp.quantity
    val tipo = interp.afirmacao?.tipo
    val candidates = mutableListOf<Pair<String, String>>()
    if (interp.incompleto) {
        candidates += "entrada" to joinQuery(listOf(clean), 12)
        candidates += "ampliada" to joinQuery(ents.take(2) + terms.take(2))
    } else if (q != null) {
        // Uma consulta com o valor alegado e outras SEM ele, para descobrir o valor documentado.
        // A comparação continua usando o valor alegado.
        val qualStems = q.qualificadores.map { stem(it) }.toSet()
        val others = terms.filter { stem(it) != q.chaveUnidade && stem(it) !in qualStems }.take(2)
        val genericUnit = q.chaveUnidade == "%" || norm(q.unidade) in setOf("pessoas", "casos", "mortes", "mortos",
            "reais", "dolares", "vezes", "anos")
        val subj = ents.take(2) + (if (genericUnit || ents.isEmpty()) others else emptyList())
        val unitWords = if (q.chaveUnidade == "%") emptyList() else listOf(q.unidade)
        val prop = unitWords + q.qualificadores
        if (q.valor != null) candidates += "especifica" to joinQuery(subj + listOfNotNull(q.texto) + q.qualificadores + years, 10)
        candidates += "valor_documentado" to joinQuery(subj + prop + years, 9)
        if (unitWords.isNotEmpty()) {
            val word = if (norm(q.unidade).endsWith("as") || norm(q.unidade).endsWith("a")) "quantas" else "quantos"
            candidates += "pergunta_valor" to joinQuery(listOf(word) + prop + subj + years, 10)
        } else {
            candidates += "dados_oficiais" to joinQuery(subj + years + listOf("dados", "oficiais"), 8)
        }
    } else if (tipo == "fato_historico") {
        candidates += "especifica" to joinQuery(ents.take(3) + terms.take(3) + years, 10)
        candidates += "sem_data" to joinQuery(ents.take(2) + terms.take(4), 8)
        candidates += "ampliada" to joinQuery(if (ents.isNotEmpty()) ents.take(2) + terms.take(1) else terms.take(2), 6)
    } else {
        candidates += "especifica" to joinQuery(ents.take(3) + terms.take(3) + nums + years, 11)
        candidates += "acontecimento" to joinQuery(ents.take(1) + terms.take(5), 8)
        candidates += "ampliada" to joinQuery(if (ents.isNotEmpty()) ents.take(2) + terms.take(1) else terms.take(2), 6)
    }
    val out = mutableListOf<SearchQuery>()
    val seen = HashSet<Set<String>>()
    for ((purpose, text) in candidates) {
        val key = norm(text).split(" ").toSet()
        if (text.isNotBlank() && seen.add(key)) out.add(SearchQuery(text, purpose))
    }
    return out
}
