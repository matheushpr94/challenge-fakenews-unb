package com.example.lumeocrtest.research

/*
 * Estrutura verificável da afirmação: tipo, entidade principal, propriedade, quantidade,
 * período, negação e qualificadores. As regras cobrem formatos estruturados (números, unidades,
 * anos, comparadores); não entendem o sentido de frases livres.
 */

private val NUM_WORDS = mapOf("dois" to 2.0, "duas" to 2.0, "tres" to 3.0, "quatro" to 4.0, "cinco" to 5.0,
    "seis" to 6.0, "sete" to 7.0, "oito" to 8.0, "nove" to 9.0, "dez" to 10.0, "onze" to 11.0, "doze" to 12.0,
    "treze" to 13.0, "catorze" to 14.0, "quatorze" to 14.0, "quinze" to 15.0, "dezesseis" to 16.0,
    "dezessete" to 17.0, "dezoito" to 18.0, "dezenove" to 19.0, "vinte" to 20.0, "trinta" to 30.0,
    "quarenta" to 40.0, "cinquenta" to 50.0, "sessenta" to 60.0, "setenta" to 70.0, "oitenta" to 80.0,
    "noventa" to 90.0, "cem" to 100.0, "nenhum" to 0.0, "nenhuma" to 0.0)

/** Ordinal seguido da unidade no singular: "nono título" = 9 no total (contagem acumulada). */
val ORDINALS = mapOf("primeiro" to 1.0, "primeira" to 1.0, "segundo" to 2.0, "segunda" to 2.0,
    "terceiro" to 3.0, "terceira" to 3.0, "quarto" to 4.0, "quarta" to 4.0, "quinto" to 5.0, "quinta" to 5.0,
    "sexto" to 6.0, "sexta" to 6.0, "setimo" to 7.0, "setima" to 7.0, "oitavo" to 8.0, "oitava" to 8.0,
    "nono" to 9.0, "nona" to 9.0, "decimo" to 10.0, "decima" to 10.0)
private val WEAK_NUM_WORDS = mapOf("um" to 1.0, "uma" to 1.0)
private val SCALE = mapOf("mil" to 1e3, "milhao" to 1e6, "milhoes" to 1e6, "bilhao" to 1e9, "bilhoes" to 1e9,
    "trilhao" to 1e12, "trilhoes" to 1e12)
private val MULT_RE = Regex("^(bi|tri|tetra|penta|hexa|hepta|octa|enea|deca)-?campe")
private val MULT = mapOf("bi" to 2.0, "tri" to 3.0, "tetra" to 4.0, "penta" to 5.0, "hexa" to 6.0,
    "hepta" to 7.0, "octa" to 8.0, "enea" to 9.0, "deca" to 10.0)
val COMPARATORS = listOf("mais de" to "min_excl", "acima de" to "min_excl", "superior a" to "min_excl",
    "pelo menos" to "min", "ao menos" to "min", "no minimo" to "min", "menos de" to "max_excl",
    "abaixo de" to "max_excl", "inferior a" to "max_excl", "no maximo" to "max", "cerca de" to "approx",
    "aproximadamente" to "approx", "quase" to "approx", "em torno de" to "approx", "mais ou menos" to "approx")
val QUAL_STOP: Set<String> = setOf("em", "segundo", "desde", "ate", "apos", "para", "por", "com", "que", "e", "ou",
    "entre", "durante", "contra", "sobre", "ao", "aos", "como") + WEEKDAYS.map { norm(it) } +
    MONTHS.keys.map { norm(it) } + setOf("nesta", "neste", "nessa", "nesse", "hoje", "ontem", "agora",
    "atualmente", "feira", "segundo", "conforme")
private val QUAL_LINK = setOf("de", "da", "do", "das", "dos", "no", "na", "nos", "nas")
private val TOKEN_RE = Regex("R\\$|US\\$|€|\\d+[ºª°]|\\d+(?:[.,]\\d+)*|%|\\p{L}[\\p{L}\\p{N}_\\-]*|[.,;:!?()\"“”]")
private val VERB_RE = Regex("^[a-z]{3,}(ou|eu|iu|aram|eram|iram)$")
private val PARTICIPLE_RE = Regex("^[a-z]{3,}(ados|idos|adas|idas|ado|ido|ada|ida)$")
val YEAR_RE = Regex("${WB}(1[5-9]\\d\\d|20[0-9]\\d)$WE")
private val YEAR_FULL = Regex("1[5-9]\\d\\d|20[0-9]\\d")
private val NUMERIC = Regex("\\d+(?:[.,]\\d+)*")
private val ORDINAL_NUM = Regex("\\d+[ºª°]")
val GENERIC_UNITS = setOf("%", "pessoas", "reais", "dolares", "euros", "anos", "vezes", "casos", "mortes", "mortos")

private val IC = setOf(RegexOption.IGNORE_CASE)
private val FUTURE_RE = Regex("$WB(vai|v[aã]o|ir[aá]|ir[aã]o|ser[aá]|ser[aã]o|dever[aá]|pretende|pretendem|" +
    "previs[aã]o|prev[eê]|promete|prometem|pr[oó]xim[oa]s?|futuramente)$WE", IC)
private val OPINION_RE = Regex("$WB(melhor(?:es)?|pior(?:es)?|lind[oa]s?|fei[oa]s?|incr[ií]ve(?:l|is)|" +
    "horr[ií]ve(?:l|is)|injust[oa]s?|absurd[oa]s?|vergonh$W*|deveria$W*|acho|acredito|opini[aã]o|genial|" +
    "rid[ií]cul$W*|maravilhos$W*|p[eé]ssim$W*|[oó]tim[oa]s?)$WE", IC)
private val CUMULATIVE_RE = Regex("$WB(tem|t[eê]m|possui|possuem|acumula$W*|soma$W*|total|ao todo|j[aá]|" +
    "conquist$W*|ganh$W*|venc$W*)$WE", IC)
private val RECENT_RE = Regex("$WB(hoje|ontem|anteontem|agora|nesta|neste|esta semana|recentemente|" +
    "acaba de|acabou de|atualmente)$WE", IC)
private val HOW_MANY_RE = Regex("^\\s*(é verdade que\\s+)?quant[oa]s$WE", IC)
private val HOW_MANY_UNIT = Regex("^\\s*quant[oa]s\\s+(\\p{L}+)((?:\\s+\\p{L}+){0,3})", IC)
private val SOURCE_QUAL = Regex("$WB(segundo|de acordo com|conforme)\\s+([^,.;]+)", IC)

val TYPE_LABEL = mapOf(
    "contagem" to "Quantidade que pode mudar com o tempo",
    "quantidade_periodo" to "Quantidade referente a um período",
    "fato_historico" to "Fato histórico ou estável",
    "acontecimento_recente" to "Acontecimento recente",
    "acontecimento" to "Acontecimento (período não informado)",
    "opiniao" to "Opinião ou valoração",
    "previsao" to "Previsão ou promessa",
)

data class Quantity(
    val valor: Double?,
    val texto: String?,
    val unidade: String,
    val chaveUnidade: String,
    val qualificadores: List<String> = emptyList(),
    val comparador: String = "eq",
    val negada: Boolean = false, // "não tem 5 títulos": a entrada nega este valor
    val posicao: Int = 0,
)

data class Afirmacao(
    val detalheNegado: Boolean,
    val tipo: String,
    val tipoTexto: String,
    val entidadePrincipal: String?,
    val propriedade: String,
    val quantidade: Quantity?,
    val anos: List<Int>,
    val datas: List<String>,
    val relativo: List<String>,
    val negacao: Boolean,
    val negacaoTexto: String?,
    val qualificadores: List<String>,
    val detalheVerificavel: String,
)

fun toFloat(s: String): Double? {
    val t = when {
        Regex("\\d{1,3}(\\.\\d{3})+(,\\d+)?").matches(s) -> s.replace(".", "").replace(",", ".")
        Regex("\\d{1,3}(,\\d{3})+").matches(s) && s.length > 5 -> s.replace(",", "")
        else -> s.replace(",", ".")
    }
    return t.toDoubleOrNull()
}

fun unitKey(word: String): String {
    val w = norm(word)
    if (word == "%" || w == "porcento" || w == "por cento") return "%"
    return stem(w)
}

private fun isLetterStart(s: String) = s.isNotEmpty() && s[0].isLetter()

/** Encontra "número + unidade (+ qualificadores)". `weakUnits` habilita "um/uma" e ordinais para essas unidades. */
fun extractQuantities(text: String?, excludeTokens: Set<String> = emptySet(), weakUnits: Set<String>? = null): List<Quantity> {
    val toks = TOKEN_RE.findAll(text ?: "").map { it.value }.toList()
    val ntoks = toks.map { if (it in setOf("%", "R$", "US$", "€")) it else norm(it) }
    val out = mutableListOf<Quantity>()
    for (i in toks.indices) {
        val t = toks[i]
        val n = ntoks[i]
        var value: Double? = null
        var j = i + 1
        var unit: String? = null
        var ukey: String? = null
        var multForm = false
        val mult = MULT_RE.find(n)
        if (NUMERIC.matches(t)) {
            val nxt = if (j < toks.size) ntoks[j] else ""
            if (YEAR_FULL.matches(t) && (nxt.isEmpty() || nxt in STOP_NORM || !(nxt[0].isLetterOrDigit()) || nxt in QUAL_STOP)) continue
            value = toFloat(t)
        } else if (n in NUM_WORDS) {
            value = NUM_WORDS[n]
        } else if (weakUnits != null && weakUnits.isNotEmpty() && n in WEAK_NUM_WORDS) {
            value = WEAK_NUM_WORDS[n]
        } else if (n in ORDINALS && i + 1 < toks.size && ntoks[i + 1] !in STOP_NORM && isLetterStart(toks[i + 1]) &&
            weakUnits != null && unitKey(toks[i + 1]) in weakUnits) {
            value = ORDINALS[n]
        } else if (ORDINAL_NUM.matches(t) && weakUnits != null) {
            value = t.dropLast(1).toDoubleOrNull()
        } else if (mult != null) {
            value = MULT[mult.groupValues[1]]
            unit = t
            ukey = unitKey("titulos")
            multForm = true
        }
        if (value == null) continue
        val raw = mutableListOf(t)
        if (!multForm) {
            if (j < toks.size && ntoks[j] in SCALE) {
                value *= SCALE.getValue(ntoks[j])
                raw.add(toks[j])
                j++
            }
            if (i > 0 && toks[i - 1] in setOf("R$", "US$", "€")) {
                unit = mapOf("R$" to "reais", "US$" to "dólares", "€" to "euros").getValue(toks[i - 1])
                raw.add(0, toks[i - 1])
            } else if (j < toks.size && toks[j] == "%") {
                unit = "%"; j++; raw.add("%")
            } else if (j + 1 < toks.size && ntoks[j] == "por" && ntoks[j + 1] == "cento") {
                unit = "%"; j += 2; raw.add("por cento")
            } else {
                while (j < toks.size && ntoks[j] in QUAL_LINK) j++
                if (j < toks.size && isLetterStart(toks[j]) && ntoks[j] !in STOP_NORM && ntoks[j] !in QUAL_STOP &&
                    ntoks[j] !in SCALE) {
                    unit = toks[j]
                    raw.add(toks[j])
                    j++
                }
            }
            if (unit == null) continue
            ukey = unitKey(unit)
            if (weakUnits != null && n in WEAK_NUM_WORDS && ukey !in weakUnits) continue
        }
        val quals = mutableListOf<String>()
        var k = j
        while (k < toks.size && k < j + 6 && quals.size < 3) {
            val nk = ntoks[k]
            if (!isLetterStart(toks[k]) || nk in QUAL_STOP || nk in NEG_NORM) break
            if (VERB_RE.containsMatchIn(nk) || nk in setOf("foram", "eram", "sao", "estao", "tem", "tinha", "tinham", "ha")) break
            if (PARTICIPLE_RE.containsMatchIn(nk)) { k++; continue } // "títulos conquistados": não é categoria
            if (nk !in QUAL_LINK && nk !in STOP_NORM && nk !in excludeTokens && nk.length >= 3) quals.add(toks[k])
            k++
        }
        var comp = "eq"
        var before = ntoks.subList(maxOf(0, i - 3), i).joinToString(" ")
        if (i > 0 && toks[i - 1] in setOf("R$", "US$", "€")) before = ntoks.subList(maxOf(0, i - 4), i - 1).joinToString(" ")
        for ((phrase, c) in COMPARATORS) if (before.endsWith(phrase)) { comp = c; break }
        val negated = n !in setOf("nenhum", "nenhuma") && (maxOf(0, i - 5) until i).any { ntoks[it] in setOf("nao", "nunca", "jamais") }
        out.add(Quantity(value, raw.joinToString(" "), unit!!, ukey!!, quals, comp, negated, i))
    }
    return out
}

fun interval(q: Quantity): Pair<Double, Double> {
    val v = q.valor ?: 0.0
    val eq = if (v >= 1000) v * 0.995 to v * 1.005 else v to v // números grandes costumam ser arredondados
    return when (q.comparador) {
        "approx" -> v * 0.9 to v * 1.1
        "min" -> v to Double.POSITIVE_INFINITY
        "min_excl" -> v * 1.0000001 + 1e-9 to Double.POSITIVE_INFINITY
        "max" -> 0.0 to v
        "max_excl" -> 0.0 to v * 0.9999999
        else -> eq
    }
}

fun compatible(a: Quantity, b: Quantity): Boolean {
    val (a0, a1) = interval(a)
    val (b0, b1) = interval(b)
    return a0 <= b1 && b0 <= a1
}

/** Prefixo curto: "mundo"/"mundiais", "nacional"/"nacionais", "Brasileirão"/"brasileiros". */
fun qualifierKeys(q: Quantity): Set<String> = q.qualificadores.map { norm(it) }.filter { it.length >= 4 }.map { it.take(4) }.toSet()

fun classify(text: String, quantity: Quantity?, years: List<Int>, nowYear: Int, relative: List<String>): String {
    if (OPINION_RE.containsMatchIn(text) && quantity == null) return "opiniao"
    if (FUTURE_RE.containsMatchIn(text) && years.none { it < nowYear }) return "previsao"
    if (quantity != null || HOW_MANY_RE.containsMatchIn(text)) {
        if (years.isNotEmpty() && years.all { it < nowYear } && !CUMULATIVE_RE.containsMatchIn(text)) return "quantidade_periodo"
        return "contagem"
    }
    if (relative.isNotEmpty() || RECENT_RE.containsMatchIn(text) || (years.isNotEmpty() && years.max() >= nowYear)) return "acontecimento_recente"
    if (years.isNotEmpty() && years.max() < nowYear) return "fato_historico"
    return "acontecimento"
}

fun detailText(q: Quantity, entity: String?): String {
    val unitTxt = (if (q.chaveUnidade == "%") "%" else q.unidade) +
        (if (q.qualificadores.isNotEmpty()) " " + q.qualificadores.joinToString(" ") else "")
    var detail = "Quantidade de $unitTxt" + (if (entity != null) " — $entity" else "")
    if (q.valor != null) detail += ": informado ${if (q.negada) "não " else ""}${q.texto}"
    return detail
}

fun structure(interp: Interpretation, mainText: String, nowYear: Int): Pair<Afirmacao, Quantity?> {
    val ents = interp.entidades
    // Só a entidade principal sai dos qualificadores; outras ("Copa do Mundo") podem ser a categoria.
    val entTokens = if (ents.isNotEmpty()) norm(ents[0]).split(" ").toSet() else emptySet()
    // "em 30 dias", "no prazo de 120 dias": prazo do fato, não uma contagem a comparar.
    var q = extractQuantities(mainText, entTokens).firstOrNull { !isDuration(it, mainText) }
    val tipo = classify(mainText, q, interp.anos, nowYear, interp.tempoRelativo)
    val unitStem = q?.chaveUnidade
    val propTerms = interp.termos.filter { stem(it) != unitStem }
    val m = HOW_MANY_UNIT.find(mainText)
    if (q == null && m != null) {
        val unit = m.groupValues[1]
        val qwords = m.groupValues[2].trim().split(Regex("\\s+")).filter {
            it.isNotBlank() && norm(it) !in STOP_NORM && norm(it) !in entTokens && norm(it) !in QUAL_STOP
        }.take(3)
        q = Quantity(null, null, unit, unitKey(unit), qwords)
    }
    val quals = mutableListOf<String>()
    val nm = norm(mainText)
    for ((phrase, _) in COMPARATORS) if (phrase in nm) quals.add(phrase)
    SOURCE_QUAL.findAll(mainText).forEach { quals.add(it.value.trim()) }
    val entity = ents.firstOrNull() ?: propTerms.firstOrNull()
    val detail = when {
        q != null -> detailText(q, entity)
        tipo == "fato_historico" && interp.anos.isNotEmpty() -> "Data/ano do fato (${interp.anos.joinToString(", ")})"
        else -> propTerms.take(4).joinToString(" ").ifBlank { interp.assunto }
    }
    val negDetail = q?.negada ?: (interp.negacoes.isNotEmpty() && tipo == "fato_historico")
    return Afirmacao(
        detalheNegado = negDetail, tipo = tipo, tipoTexto = TYPE_LABEL.getValue(tipo), entidadePrincipal = entity,
        propriedade = propTerms.take(4).joinToString(" "), quantidade = q, anos = interp.anos,
        datas = interp.datas.map { it.texto }, relativo = interp.tempoRelativo, negacao = interp.negacoes.isNotEmpty(),
        negacaoTexto = interp.negacoes.firstOrNull(), qualificadores = quals, detalheVerificavel = detail,
    ) to q
}
