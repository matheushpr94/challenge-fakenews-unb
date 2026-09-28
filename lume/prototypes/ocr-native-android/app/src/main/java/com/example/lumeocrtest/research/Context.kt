package com.example.lumeocrtest.research

/*
 * Seleção conservadora de contexto: entidade + propriedade (+ período), frase completa, com fonte.
 * Só entra uma frase que (1) trata da entidade, (2) cita a propriedade perguntada e (3) explica o dado:
 * definição, método de medição, período ou condição. Frases com o próprio valor comparado ficam de
 * fora (já aparecem na resposta). Sem frase que cumpra tudo, não há contexto.
 */

data class ContextItem(val texto: String, val veiculo: String, val url: String, val titulo: String,
                       val motivo: String, val conteudo: String)

private const val MAX_ITEMS = 2
private const val MIN_LEN = 50
private const val MAX_LEN = 320
private const val A = "[a-z0-9]"
// Expressões gerais (sobre texto normalizado) que indicam explicação do dado; não são assuntos.
private val METHOD_RE = Regex("$WB(segundo (?:o|a|os|as|dados|estimativas?|levantamentos?|registros?)|de acordo com|" +
    "conforme|estimad$A*|estimativ$A*|censo|metodolog$A*|metodo|calculad$A*|calculo|consider$A*|inclu$A*|exclu$A*|" +
    "defin$A*|contabiliz$A*|reconhecid$A*|criterio$A*|medid$A*|medicao|levantamento|apurad$A*|contagem|" +
    "referencia|refere se|oficial|oficialmente)$WE")
private val PERIOD_RE = Regex("$WB(desde|anual$A*|mensal$A*|acumulad$A*|periodo|data de referencia)$WE")
// Frase que começa retomando algo anterior não se sustenta sozinha como trecho.
private val ANAPHORIC_START = Regex("^[\"“(]?(ele|ela|eles|elas|isso|isto|este|esta|estes|estas|esse|essa|esses|essas|" +
    "aquele|aquela|al[eé]m disso|no entanto|por[eé]m|contudo|assim|tamb[eé]m|antes disso|depois disso|nesse|nessa|" +
    "neste|nesta|dessa|desse|desta|deste)$WE", RegexOption.IGNORE_CASE)
private val CTX_SENTENCE = Regex("(?<=[.!?])[\"”)]?\\s+(?=[\"“(]?[A-ZÀ-Ý0-9])")
private val STARTS_OK = Regex("^[\"“(]?[A-ZÀ-Ý0-9]")
private val ENDS_OK = Regex("[.!?][\"”)]?$")

/** Frases inteiras: começam com maiúscula/dígito, terminam com pontuação final, sem reticências nem retomada. */
fun completeSentences(text: String?): List<String> = (text ?: "").split(CTX_SENTENCE).map { it.trim() }.filter { s ->
    s.length in MIN_LEN..MAX_LEN && STARTS_OK.containsMatchIn(s) && ENDS_OK.containsMatchIn(s) &&
        !s.endsWith("...") && "…" !in s && !ANAPHORIC_START.containsMatchIn(s)
}

private fun isCue(word: String): Boolean {
    val n = norm(word)
    return METHOD_RE.containsMatchIn(n) || PERIOD_RE.containsMatchIn(n)
}

private fun propertyStems(interp: Interpretation): Pair<Set<String>, Int> {
    val q = interp.quantity
    // Palavras de método/período ("oficial", "anual") não identificam a propriedade.
    val excluded = interp.entidades.flatMap { norm(it).split(" ") }.toSet() + interp.termos.filter { isCue(it) }.map { norm(it) }
    if (q != null) {
        val props = mutableSetOf<String>()
        if (q.chaveUnidade != "%") props.add(q.chaveUnidade)
        props += q.qualificadores.filter { norm(it) !in excluded }.map { stem(it) }
        if (q.chaveUnidade == "%") props += interp.termos.filter { norm(it) !in excluded }.map { stem(it) }
        val clean = props.filter { it.isNotEmpty() }.toSet()
        return clean to clean.size // unidade e todas as categorias precisam aparecer na frase
    }
    val terms = interp.termos.filter { norm(it) !in excluded && norm(it) !in STOP_NORM }.map { stem(it) }.toSet()
    return terms to minOf(2, terms.size)
}

private fun entityIs(name: String, entity: String): Boolean {
    val n = norm(name)
    return entityMatch(entity, n, n.split(" ").toSet(), emptySet())
}

private fun aboutEntity(sentence: String, cand: Candidate, entity: String?, props: Set<String>): Boolean {
    if (entity == null) return true
    val named = extractEntities(sentence).filter { e -> (norm(e).split(" ").map { stem(it) }.toSet() intersect props).isEmpty() }
    if (named.isNotEmpty() && !entityIs(named[0], entity)) return false // a frase fala primeiro de outra entidade
    if (mentionsEntity(entity, sentence)) return true
    return pageIsEntity(cand, entity) // artigo de enciclopédia cujo título é a própria entidade
}

/** Até 2 trechos de contexto úteis, cada um com fonte e link; lista vazia se nada for útil. */
fun selectContext(interp: Interpretation, groups: List<Group>, answerTexts: Collection<String>): List<ContextItem> {
    val entity = interp.entidades.firstOrNull()
    val (props, need) = propertyStems(interp)
    if (props.isEmpty() || need == 0) return emptyList()
    val q = interp.quantity
    val answers = answerTexts.map { norm(it) }.toSet()
    data class Pick(val score: Int, val s: String, val cand: Candidate, val why: String)
    val picked = mutableListOf<Pick>()
    for (g in groups) for ((cand, _) in g.members) {
        val text = when {
            cand.contentKind == "completo" && cand.pageText.isNotEmpty() -> cand.pageText
            cand.contentKind == "resumo" && cand.snippet.isNotEmpty() -> cand.snippet
            else -> continue // título ou trecho de buscador: não serve de contexto
        }
        for (s in completeSentences(text)) {
            val sn = norm(s)
            if (sn in answers) continue
            if (q != null && extractQuantities(s).any { it.chaveUnidade == q.chaveUnidade }) continue // é resposta, não contexto
            val sstems = sn.split(" ").filter { it.length >= 3 }.map { stem(it) }.toSet()
            val hits = (sstems intersect props).size
            if (hits < need || !aboutEntity(s, cand, entity, props)) continue
            val method = METHOD_RE.containsMatchIn(sn)
            // Ano solto não é "período": só conta o ano da própria afirmação ou expressões de período.
            val period = PERIOD_RE.containsMatchIn(sn) || interp.anos.any { it.toString() in s }
            if (!method && !period) continue // não explica o dado
            picked.add(Pick(2 * hits + (if (method) 2 else 0) + (if (period) 1 else 0), s, cand,
                if (method) "definição ou método" else "período"))
        }
    }
    val out = mutableListOf<ContextItem>()
    val usedUrls = HashSet<String>()
    for (p in picked.sortedByDescending { it.score }) {
        if (p.cand.url in usedUrls || out.any { norm(it.texto) == norm(p.s) }) continue
        usedUrls.add(p.cand.url)
        out.add(ContextItem(p.s, p.cand.sourceName, p.cand.url, p.cand.title, p.why, p.cand.contentKind))
        if (out.size >= MAX_ITEMS) break
    }
    return out
}
