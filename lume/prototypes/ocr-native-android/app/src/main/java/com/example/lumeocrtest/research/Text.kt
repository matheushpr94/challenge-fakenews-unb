package com.example.lumeocrtest.research

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/*
 * Normalização, tokens e radicais simples para português (sem modelos).
 * As expressões usam limites de palavra explícitos (WB/WE) porque "\b" e "\w" se comportam
 * de forma diferente entre a JVM dos testes (ASCII) e o Android (Unicode).
 */

internal const val WB = "(?<![\\p{L}\\p{N}_])"
internal const val WE = "(?![\\p{L}\\p{N}_])"
internal const val W = "[\\p{L}\\p{N}_]"
internal val WORD_RE = Regex("$W[\\p{L}\\p{N}_'’\\-]*")

private val STOPWORDS_RAW = """
a à ao aos as às o os um uma uns umas de da do das dos dum duma em no na nos nas num numa
por pelo pela pelos pelas para pra pro com sob sobre entre até apos após ante contra desde e ou mas
que se como quando onde quem qual quais cujo cuja porque pois já tambem também só mais menos muito
muita muitos muitas pouco tão tanto isso isto aquilo esse essa esses essas este esta estes estas aquele
aquela aqueles aquelas ele ela eles elas eu tu voce você vocês voces nós nos vos lhe lhes me te seu sua
seus suas meu minha nosso nossa dele dela deles delas foi ser é e são sao era eram será sera seria sido
sendo está esta estão estao estava estavam estar esteve tem têm tinha ter tido há ha havia haver vai vão
vao ia fazer faz fez feito diz disse dizem dizer afirma afirmou afirmam declarou falou segundo sobre
aí ai lá la aqui ali bem ainda sim então entao todo toda todos todas outro outra outros outras mesmo
mesma cada qualquer algum alguma alguns algumas nada tudo ninguém algo coisa verdade
the of and to in on for is are was were be by with at from this that it as an or
"""

private val NEGATIONS_RAW = listOf("não", "nao", "nunca", "jamais", "nem", "nenhum", "nenhuma", "negou", "nega",
    "negam", "desmente", "desmentiu", "desmentem", "not", "never", "no")

val MONTHS: Map<String, Int> = mapOf("janeiro" to 1, "fevereiro" to 2, "março" to 3, "marco" to 3, "abril" to 4,
    "maio" to 5, "junho" to 6, "julho" to 7, "agosto" to 8, "setembro" to 9, "outubro" to 10, "novembro" to 11,
    "dezembro" to 12)
val WEEKDAYS: Set<String> = setOf("segunda", "terça", "terca", "quarta", "quinta", "sexta", "sábado", "sabado",
    "domingo", "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira")

private val SUFFIXES = listOf("amentos", "imentos", "amento", "imento", "acoes", "icoes", "acao", "icao", "mente",
    "aram", "eram", "iram", "ando", "endo", "indo", "ados", "idos", "adas", "idas", "ado", "ido", "ada", "ida",
    "ou", "am", "em", "ar", "er", "ir", "os", "as", "es", "s", "a", "o", "e")

private val MARKS = Regex("\\p{M}+")
private val NUM_SEP = Regex("(?<=\\d)[.,](?=\\d)")
private val NON_ALNUM = Regex("[^a-z0-9\u0000]+")
private val SPACES = Regex("\\s+")
private val NUMBER_TOKEN = Regex("\\d+(?:[.,]\\d+)*")

fun stripAccents(s: String): String = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFKD), "")

/** Minúsculas, sem acentos, apenas letras/dígitos separados por espaço (5,2 e 1.000 ficam juntos). */
fun norm(s: String?): String {
    var t = stripAccents(s ?: "").lowercase(Locale.ROOT)
    t = NUM_SEP.replace(t, "\u0000")
    t = NON_ALNUM.replace(t, " ").replace("\u0000", ",")
    return t.trim().split(SPACES).filter { it.isNotEmpty() }.joinToString(" ")
}

fun tokens(s: String?): List<String> = norm(s).split(" ").filter { it.isNotEmpty() }

fun stem(word: String): String {
    var w = norm(word).replace(" ", "")
    for (suf in SUFFIXES) {
        if (w.endsWith(suf) && w.length - suf.length >= 4) {
            w = w.dropLast(suf.length)
            break
        }
    }
    return w.take(6)
}

fun stemsOf(s: String?): Set<String> = tokens(s).filter { it.length >= 3 }.map { stem(it) }.toSet()

/** 'R$ 1.500,00' -> '1500,00'; '5,2%' -> '5,2'. */
fun numberKey(raw: String): String {
    var digits = raw.replace(Regex("[^\\d.,]"), "")
    digits = digits.replace(Regex("\\.(?=\\d{3}(\\D|$))"), "")
    return digits.trim('.', ',')
}

fun numbersIn(text: String?): Set<String> = NUMBER_TOKEN.findAll(text ?: "").map { numberKey(it.value) }.toSet()

fun <T> jaccard(a: Set<T>, b: Set<T>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    return (a intersect b).size.toDouble() / (a union b).size
}

val STOP_NORM: Set<String> = STOPWORDS_RAW.split(SPACES).filter { it.isNotBlank() }.map { norm(it) }.toSet()
val NEG_NORM: Set<String> = NEGATIONS_RAW.map { norm(it) }.toSet() - "no" // "no" em português é preposição

fun hasNegation(text: String?): Boolean = tokens(text).any { it in NEG_NORM }

fun String.isAllUpper(): Boolean {
    val letters = filter { it.isLetter() }
    return letters.isNotEmpty() && letters.all { it.isUpperCase() }
}

// ---------------- datas (milissegundos UTC; java.time exige API 26) -------------------------

object Dates {
    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    private val ISO_RE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})(?:[T ](\\d{2}):(\\d{2})(?::(\\d{2}))?(?:\\.\\d+)?)?\\s*(Z|[+-]\\d{2}:?\\d{2})?")
    private val RFC_PATTERNS = listOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss zzz")

    fun parse(s: String?): Long? {
        val t = s?.trim().orEmpty()
        if (t.isEmpty()) return null
        parseIso(t)?.let { return it }
        for (p in RFC_PATTERNS) {
            try {
                val f = SimpleDateFormat(p, Locale.US)
                f.isLenient = false
                return f.parse(t)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun parseIso(t: String): Long? {
        val m = ISO_RE.find(t) ?: return null
        val g = m.groupValues
        val cal = Calendar.getInstance(UTC)
        cal.clear()
        cal.set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].ifEmpty { "0" }.toInt(),
            g[5].ifEmpty { "0" }.toInt(), g[6].ifEmpty { "0" }.toInt())
        var ms = cal.timeInMillis
        val tz = g[7]
        if (tz.isNotEmpty() && tz != "Z") {
            val sign = if (tz[0] == '-') -1 else 1
            val digits = tz.drop(1).replace(":", "")
            val offset = (digits.take(2).toInt() * 60 + digits.drop(2).toInt()) * 60_000L
            ms -= sign * offset
        }
        return ms
    }

    fun year(ms: Long): Int = Calendar.getInstance(UTC).apply { timeInMillis = ms }.get(Calendar.YEAR)

    fun format(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = UTC }.format(ms)

    fun iso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = UTC }.format(ms)

    const val DAY_MS = 24L * 3600 * 1000
}
