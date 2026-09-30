package com.example.lumeocrtest.research

import java.net.URI

/*
 * Relevância pelo acontecimento (entidades + termos + detalhes), agrupamento de republicações e
 * classificação em: direto, anterior, contexto ou descartado.
 */

const val RECENT_DAYS = 30
const val RELATIVE_MAX_AGE_MS = 14 * Dates.DAY_MS
/** Publicações muito anteriores à data da matéria lida tratam de um episódio anterior. */
const val REFERENCE_WINDOW_MS = 21 * Dates.DAY_MS
val CONTENT_RANK = mapOf("completo" to 3, "resumo" to 2, "trecho" to 2, "titulo" to 1)

class Assessment(
    var category: String,
    val score: Double,
    var relation: String,
    val entHits: List<String> = emptyList(),
    val entMissing: List<String> = emptyList(),
    val termHits: List<String> = emptyList(),
    val numHits: List<String> = emptyList(),
    val period: String = "sem_data",
    val alerts: MutableList<String> = mutableListOf(),
    var reason: String = "",
    /** Evidência lexical forte: mesmo ator e ação+objeto (ou quase todos os termos). A IA não a derruba sozinha. */
    var strong: Boolean = false,
)

fun acronymsOf(text: String): Set<String> = extractEntities(text).mapNotNull { e ->
    val words = norm(e).split(" ").filter { it.isNotEmpty() && it !in CONNECTORS }
    if (words.size >= 2) words.joinToString("") { it.take(1) } else null
}.toSet()

fun entityMatch(entity: String, candNorm: String, candTokens: Set<String>, candAcronyms: Set<String>): Boolean {
    val n = norm(entity)
    if (" $n " in " $candNorm ") return true
    val toks = n.split(" ").filter { it.length >= 3 && it !in CONNECTORS }
    if (toks.size <= 1) return (entity.isAllUpper() && n in candAcronyms) || (toks.isNotEmpty() && toks[0] in candTokens)
    val words = candNorm.split(" ").filter { it.isNotEmpty() }
    for (start in words.indices.filter { words[it] == toks[0] }) {
        var pos = start
        var matches = true
        for (token in toks.drop(1)) {
            pos++
            while (pos < words.size && words[pos] in CONNECTORS) pos++
            if (pos >= words.size || words[pos] != token) { matches = false; break }
        }
        if (matches) return true
    }
    return toks.joinToString("") { it.take(1) } in candTokens
}

fun periodOf(cand: Candidate, nowMs: Long): String {
    val p = cand.published ?: return "sem_data"
    return if (nowMs - p <= RECENT_DAYS * Dates.DAY_MS) "recente" else "anterior"
}

/** `extra`: texto da página lida (quando houver), usado para reavaliar a relação depois da leitura. */
fun assess(interp: Interpretation, cand: Candidate, nowMs: Long, extra: String = ""): Assessment {
    val text = "${cand.title}. ${cand.snippet}" + if (extra.isNotBlank()) ". ${extra.take(4000)}" else ""
    val cn = norm(text)
    val ctoks = cn.split(" ").toSet()
    val cstems = ctoks.filter { it.length >= 3 }.map { stem(it) }.toSet()
    val ents = interp.entidades.take(4)
    val terms = interp.termos.take(5)
    val acr = acronymsOf(text)
    // Sigla ou nome por extenso (resolvidos pelo texto da própria matéria) contam como o mesmo nome.
    val entHits = ents.filter { e -> entityMatch(e, cn, ctoks, acr) || interp.aliases[e].orEmpty().any { " ${norm(it)} " in " $cn " } }
    val directTermHits = terms.filter { stem(it) in cstems }
    // Palavra que a própria matéria usa para o mesmo assunto substitui no máximo UM termo ausente.
    val vocabHit = interp.vocabulario.firstOrNull { stem(it) in cstems }
    val missingTerm = terms.firstOrNull { it !in directTermHits }
    val termHits = if (vocabHit != null && missingTerm != null && directTermHits.size == 1) directTermHits + missingTerm else directTermHits
    // Núcleo do acontecimento: os dois primeiros termos da frase (em manchetes, em geral a ação e o objeto).
    val core = terms.take(2)
    val coreHit = core.size == 2 && core.all { it in termHits }
    val candNums = numbersIn(text)
    val numHits = interp.numeros.filter { it.chave in candNums }.map { it.texto }
    val entCov = if (ents.isNotEmpty()) entHits.size.toDouble() / ents.size else 1.0
    val termCov = if (terms.isNotEmpty()) termHits.size.toDouble() / terms.size else 0.0
    val score = 0.5 * entCov + 0.5 * termCov + 0.1 * numHits.size + 0.02 * CONTENT_RANK.getValue(cand.contentKind)
    val a = Assessment("descartado", Math.round(score * 1000) / 1000.0, "", entHits, ents - entHits.toSet(), termHits,
        numHits, periodOf(cand, nowMs))

    var need = if (terms.size <= 1) 1 else 2
    if (ents.isEmpty()) need = if (terms.size <= 3) terms.size else (2 * terms.size + 2) / 3 // sem nomes: quase todos os termos
    if (interp.quantity == null && interp.afirmacao?.tipo !in STABLE_TYPES_FOR_CONTEXT && terms.size >= 4) {
        // Nome + dois verbos genéricos não bastam quando a entrada descreve mais detalhes.
        need = maxOf(need, (terms.size * 3 + 4) / 5)
    }
    // Números citados contam como um detalhe do acontecimento (ex.: placar, valor).
    val numDetail = numHits.isNotEmpty() && (numHits.size == interp.numeros.size ||
        interp.numeros.any { it.texto in numHits && it.chave.length >= 2 })
    // Mesmo mês citado conta como um detalhe do acontecimento; só outro mês indica outro período.
    val claimMonths = monthsIn(interp.assunto)
    val candMonths = monthsIn(text)
    val monthHit = claimMonths.isNotEmpty() && (claimMonths intersect candMonths).isNotEmpty()
    val otherMonth = claimMonths.isNotEmpty() && !monthHit && candMonths.isNotEmpty()
    val termHitsN = termHits.size + (if (numDetail) 1 else 0) + (if (monthHit) 1 else 0)

    if (cand.origin == "wikipedia") {
        when {
            cand.disambiguation -> a.reason = "página de desambiguação"
            interp.afirmacao?.tipo !in STABLE_TYPES_FOR_CONTEXT && termHits.size < 2 ->
                a.reason = "enciclopédia sobre nome ou assunto genérico, sem o acontecimento pesquisado"
            entHits.isNotEmpty() || termHits.isNotEmpty() -> { a.category = "contexto"; a.relation = "Contexto geral (enciclopédia)" }
            else -> a.reason = "enciclopédia sem relação com os termos"
        }
        return a
    }
    if (interp.incompleto) {
        if (entHits.isNotEmpty() || termHits.isNotEmpty()) { a.category = "contexto"; a.relation = "Relacionado aos termos informados" }
        else a.reason = "nenhum termo da entrada"
        return a
    }
    if (ents.isNotEmpty() && entHits.isNotEmpty() && termHitsN == 0) {
        val referenceClaim = interp.quantity != null || interp.afirmacao?.tipo == "fato_historico"
        if (referenceClaim && cand.origin == "bing_web") {
            // Página de referência sobre a entidade: pode conter o valor/ano; será lida, não é resposta direta.
            a.category = "contexto"; a.relation = "Página de referência sobre a entidade"
            return a
        }
        a.reason = "apenas o nome coincide; outro assunto"
        return a
    }
    val fullEnts = ents.isEmpty() || entCov == 1.0
    // Mesmo ator, ação e objeto, faltando só as palavras do detalhe numérico: mesmo acontecimento sem o detalhe.
    val anchor = (interp.prazos + interp.numeros.map { it.texto }).firstOrNull()
    val detailOnlyMissing = anchor != null && coreHit && ents.isNotEmpty() && entCov == 1.0 &&
        (terms - termHits.toSet()).all { it in termsNear(interp.assunto, anchor, terms) }
    // Frase com partes coordenadas: cobrir uma parte inteira (sem nomes em falta) já é relatar o acontecimento.
    // A parte coberta precisa ter um termo central da matéria (evita partes genéricas como "o texto foi publicado").
    val clauseCovered = fullEnts && interp.partes.any { p -> p.all { it in termHits } &&
        (interp.topicos.isEmpty() || p.any { topicKey(it) in interp.topicos }) }
    when {
        (fullEnts && termHitsN >= need) || (entCov >= 0.5 && termHitsN >= need + 1) || detailOnlyMissing || clauseCovered -> {
            a.category = "direto"; a.relation = "Trata do mesmo acontecimento"
            a.strong = (fullEnts && ents.isNotEmpty() && coreHit) || (fullEnts && termHitsN >= need + 1)
        }
        entHits.isNotEmpty() && termHits.isNotEmpty() -> {
            a.category = "contexto"; a.relation = "Mesma pessoa/instituição, outro aspecto ou só parte do acontecimento"
        }
        entHits.isNotEmpty() && termHitsN > 0 -> {
            a.reason = "só coincidem o nome e o número; trata de outro assunto"
            return a
        }
        ents.isEmpty() && termHits.size >= 2 && termCov >= 0.5 -> {
            a.category = "contexto"; a.relation = "Mesmo tema, sem todos os termos da entrada"
        }
        ents.isNotEmpty() && entHits.isEmpty() && (termHits.size >= need && termCov >= 0.6 || coreHit) -> {
            a.category = "contexto"; a.relation = "Mesmo tema, sem citar " + ents.take(2).joinToString(", ")
        }
        else -> {
            a.reason = "poucos detalhes em comum (entidades ${entHits.size}/${ents.size}, termos ${termHits.size}/${terms.size})"
            return a
        }
    }
    // Ação citada só em oração secundária ("MP que proíbe…", "após proibição…") e sem o detalhe: desdobramento.
    if (a.category == "direto" && core.isNotEmpty() && core.any { actionOnlySubordinate(cand.title, it) } &&
        (interp.etapa == null || actStage(mainClause(cand.title, core[0])) != interp.etapa)) {
        val anchor = (interp.prazos + interp.numeros.map { it.texto }).firstOrNull()
        val detailTerms = if (anchor != null) termsNear(interp.assunto, anchor, terms) - core.toSet() else terms.drop(2)
        // O foco da matéria vem do título/resumo: a página pode repetir o acontecimento só como contexto.
        val headStems = stemsOf("${cand.title} ${cand.snippet}")
        val hasDetail = interp.numeros.any { it.chave in numbersIn("${cand.title} ${cand.snippet}") } ||
            detailTerms.count { stem(it) in headStems } >= minOf(2, detailTerms.size).coerceAtLeast(1)
        if (!hasDetail) {
            a.category = "contexto"
            a.relation = "Desdobramento ou menção ao acontecimento; não relata o detalhe pesquisado"
            a.strong = false
        }
    }
    // Negação só conta quando está junto da ação/objeto da afirmação ("não proíbe"), não em qualquer frase do resumo.
    val coreStems = core.map { stem(it) }.toSet()
    // O título define o foco da matéria: com menos de dois elementos da afirmação nele, é outro aspecto do tema.
    val titleNorm = norm(cand.title)
    val titleStems = stemsOf(cand.title)
    val titleHits = ents.count { e -> entityMatch(e, titleNorm, titleNorm.split(" ").toSet(), acronymsOf(cand.title)) ||
        interp.aliases[e].orEmpty().any { " ${norm(it)} " in " $titleNorm " } } + terms.count { stem(it) in titleStems }
    if (a.category == "direto" && titleHits < 2) {
        a.category = "contexto"; a.strong = false
        a.relation = "O título trata de outro aspecto do tema; o acontecimento aparece só no resumo ou na página"
    }
    if (a.category == "direto" && (interp.negacoes.isNotEmpty() != negatedNear(text, coreStems))) {
        a.category = "contexto"
        a.relation = "Os textos não têm o mesmo sentido de afirmação ou negação"
    }
    if (a.category == "direto" && otherMonth) {
        a.category = "contexto"
        a.relation = "Refere-se a outro período (${candMonths.joinToString(", ")}); a afirmação cita ${claimMonths.joinToString(", ")}"
        a.strong = false
    }
    // Tempo: não tratar publicação antiga como confirmação de algo recente.
    val pub = cand.published
    if (a.category == "direto" && pub != null) {
        val textYears = interp.anos.filter { it.toString() in cn }
        if (interp.anos.isNotEmpty() && Dates.year(pub) !in interp.anos && textYears.isEmpty()) {
            a.category = "anterior"
            a.relation = "Mesmo assunto, publicado em ${Dates.year(pub)} (a entrada cita ${interp.anos.joinToString(", ")})"
        } else if (interp.dataReferencia != null && pub < interp.dataReferencia - REFERENCE_WINDOW_MS) {
            a.category = "anterior"; a.strong = false
            a.relation = "Publicado em ${Dates.format(pub)}, bem antes da matéria lida (${Dates.format(interp.dataReferencia)})"
        } else if (interp.tempoRelativo.isNotEmpty() && nowMs - pub > RELATIVE_MAX_AGE_MS) {
            a.category = "anterior"
            a.relation = "Mesmo assunto, mas publicado em ${Dates.format(pub)}; a entrada fala em “${interp.tempoRelativo[0]}”"
        }
    }
    if (interp.negacoes.isNotEmpty() && !hasNegation(text)) {
        a.alerts.add("A entrada contém negação e este texto não; confira se dizem o mesmo.")
    } else if (interp.negacoes.isEmpty() && hasNegation(text) && a.category != "contexto") {
        a.alerts.add("Este texto contém negação (ex.: “não”, “nega”); confira o sentido.")
    }
    val otherNums = candNums.filterNot { it.length == 4 && it.all(Char::isDigit) && (it.startsWith("19") || it.startsWith("20")) }
    if (interp.numeros.isNotEmpty() && numHits.isEmpty() && otherNums.isNotEmpty() && a.category != "contexto") {
        a.alerts.add("Cita números diferentes dos informados; confira os detalhes.")
    }
    if (a.entMissing.isNotEmpty() && a.category == "direto") a.alerts.add("Não menciona: " + a.entMissing.joinToString(", "))
    return a
}

private val STABLE_TYPES_FOR_CONTEXT = setOf("contagem", "quantidade_periodo", "fato_historico")

// --------- duplicatas e republicações ------------------------------------------------------------

fun canonical(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    // Versões móvel e desktop da mesma página são o mesmo conteúdo (pt.m.wikipedia.org = pt.wikipedia.org).
    val host = (uri.host ?: "").lowercase().removePrefix("www.").replace(Regex("(^|\\.)(m|mobile|amp)\\."), "$1")
    val path = (uri.rawPath ?: "").trimEnd('/')
    return if (host.endsWith("news.google.com")) host + path else host + path.lowercase()
}

fun titleKey(t: String): Set<String> = norm(t).split(" ").filter { it.length >= 3 && it !in STOP_NORM }.toSet()

class Group(val members: MutableList<Pair<Candidate, Assessment>>) {
    val lead: Pair<Candidate, Assessment>
        get() = members.maxWith(compareBy({ CONTENT_RANK.getValue(it.first.contentKind) }, { it.second.score }))
}

/** Remove URLs repetidas e agrupa o mesmo conteúdo publicado por vários veículos. */
fun groupCandidates(pairs: List<Pair<Candidate, Assessment>>,
                    onDuplicate: (dropped: Candidate, kept: Candidate) -> Unit = { _, _ -> }): List<Group> {
    val kept = HashMap<String, Candidate>()
    val unique = pairs.sortedByDescending { it.second.score }.filter { (c, _) ->
        val key = canonical(c.url)
        val first = kept[key]
        if (first == null) { kept[key] = c; true } else { onDuplicate(c, first); false }
    }
    val groups = mutableListOf<Group>()
    for (pair in unique) {
        val (cand, _) = pair
        val tk = titleKey(cand.title)
        val target = groups.firstOrNull { g ->
            val lead = g.members[0].first
            // Títulos parecidos que divergem em negação ou números não são cópias: dizem coisas diferentes.
            val sameTitle = jaccard(tk, titleKey(lead.title)) >= 0.7 && hasNegation(cand.title) == hasNegation(lead.title) &&
                numbersIn(cand.title) == numbersIn(lead.title)
            val sameSnip = cand.snippet.isNotEmpty() && lead.snippet.isNotEmpty() && cand.snippet.length > 60 &&
                jaccard(titleKey(cand.snippet), titleKey(lead.snippet)) >= 0.8
            sameTitle || sameSnip
        }
        if (target != null) target.members.add(pair) else groups.add(Group(mutableListOf(pair)))
    }
    return groups
}

private val SUBORDINATE = Regex("""(?iu)(?<![\p{L}\d])(?:que|contra|sobre|após|apos|depois d[aoe]s?|antes d[aoe]s?|com a|pela|pelo|para)\s+(?:[\p{L}\d.]+\s+){0,2}""")

/** A ação da afirmação só aparece logo depois de "que/após/contra/sobre…" no título (não é a ação principal). */
fun actionOnlySubordinate(title: String, action: String): Boolean {
    val st = stem(action)
    val words = WORD_RE.findAll(title).toList()
    val hits = words.filter { stem(it.value) == st }
    if (hits.isEmpty()) return false
    return hits.all { h ->
        val before = title.substring(0, h.range.first)
        SUBORDINATE.findAll(before).any { it.range.last + 1 == before.length }
    }
}

/** Título sem a oração secundária que contém a ação ("governo publica MP [que proíbe as bets]"). */
fun mainClause(title: String, action: String): String {
    val st = stem(action)
    val hit = WORD_RE.findAll(title).firstOrNull { stem(it.value) == st } ?: return title
    val before = title.substring(0, hit.range.first)
    val sub = SUBORDINATE.findAll(before).lastOrNull { it.range.last + 1 == before.length } ?: return title
    val after = title.substring(hit.range.last + 1).split(Regex("[,;:]")).drop(1).joinToString(" ")
    return (title.substring(0, sub.range.first) + " " + after).trim()
}

/** Há negação a até 4 palavras antes de um dos radicais (ex.: "não proíbe", "nega que proibiu")? */
fun negatedNear(text: String, stems: Set<String>): Boolean {
    if (stems.isEmpty()) return hasNegation(text)
    val toks = tokens(text)
    return toks.indices.any { i -> toks[i] in NEG_NORM && toks.subList(i + 1, minOf(toks.size, i + 5)).any { stem(it) in stems } }
}
