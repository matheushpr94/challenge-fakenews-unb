package com.example.lumeocrtest.research

import java.net.URI

/*
 * Relevância pelo acontecimento (entidades + termos + detalhes), agrupamento de republicações e
 * classificação em: direto, anterior, contexto ou descartado.
 */

const val RECENT_DAYS = 30
private const val RELATIVE_MAX_AGE_MS = 14 * Dates.DAY_MS
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
    val hits = toks.filter { it in candTokens }
    if (hits.size >= (toks.size + 1) / 2 && hits.any { it.length >= 4 }) return true
    return toks.joinToString("") { it.take(1) } in candTokens
}

fun periodOf(cand: Candidate, nowMs: Long): String {
    val p = cand.published ?: return "sem_data"
    return if (nowMs - p <= RECENT_DAYS * Dates.DAY_MS) "recente" else "anterior"
}

fun assess(interp: Interpretation, cand: Candidate, nowMs: Long): Assessment {
    val text = "${cand.title}. ${cand.snippet}"
    val cn = norm(text)
    val ctoks = cn.split(" ").toSet()
    val cstems = ctoks.filter { it.length >= 3 }.map { stem(it) }.toSet()
    val ents = interp.entidades.take(4)
    val terms = interp.termos.take(5)
    val acr = acronymsOf(text)
    val entHits = ents.filter { entityMatch(it, cn, ctoks, acr) }
    val termHits = terms.filter { stem(it) in cstems }
    val candNums = numbersIn(text)
    val numHits = interp.numeros.filter { it.chave in candNums }.map { it.texto }
    val entCov = if (ents.isNotEmpty()) entHits.size.toDouble() / ents.size else 1.0
    val termCov = if (terms.isNotEmpty()) termHits.size.toDouble() / terms.size else 0.0
    val score = 0.5 * entCov + 0.5 * termCov + 0.1 * numHits.size + 0.02 * CONTENT_RANK.getValue(cand.contentKind)
    val a = Assessment("descartado", Math.round(score * 1000) / 1000.0, "", entHits, ents - entHits.toSet(), termHits,
        numHits, periodOf(cand, nowMs))

    var need = if (terms.size <= 1) 1 else 2
    if (ents.isEmpty()) need = if (terms.size <= 3) terms.size else (2 * terms.size + 2) / 3 // sem nomes: quase todos os termos
    // Números citados contam como um detalhe do acontecimento (ex.: placar, valor).
    val numDetail = numHits.isNotEmpty() && (numHits.size == interp.numeros.size ||
        interp.numeros.any { it.texto in numHits && it.chave.length >= 2 })
    val termHitsN = termHits.size + if (numDetail) 1 else 0

    if (cand.origin == "wikipedia") {
        when {
            cand.disambiguation -> a.reason = "página de desambiguação"
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
    when {
        (fullEnts && termHitsN >= need) || (entCov >= 0.5 && termHitsN >= need + 1) -> {
            a.category = "direto"; a.relation = "Trata do mesmo acontecimento"
        }
        entHits.isNotEmpty() && termHitsN > 0 -> {
            a.category = "contexto"; a.relation = "Mesma pessoa/instituição, outro aspecto ou só parte do acontecimento"
        }
        ents.isEmpty() && termHits.size >= 2 && termCov >= 0.5 -> {
            a.category = "contexto"; a.relation = "Mesmo tema, sem todos os termos da entrada"
        }
        ents.isNotEmpty() && entHits.isEmpty() && termHits.size >= need && termCov >= 0.6 -> {
            a.category = "contexto"; a.relation = "Mesmo tema, sem citar " + ents.take(2).joinToString(", ")
        }
        else -> {
            a.reason = "poucos detalhes em comum (entidades ${entHits.size}/${ents.size}, termos ${termHits.size}/${terms.size})"
            return a
        }
    }
    // Tempo: não tratar publicação antiga como confirmação de algo recente.
    val pub = cand.published
    if (a.category == "direto" && pub != null) {
        val textYears = interp.anos.filter { it.toString() in cn }
        if (interp.anos.isNotEmpty() && Dates.year(pub) !in interp.anos && textYears.isEmpty()) {
            a.category = "anterior"
            a.relation = "Mesmo assunto, publicado em ${Dates.year(pub)} (a entrada cita ${interp.anos.joinToString(", ")})"
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
fun groupCandidates(pairs: List<Pair<Candidate, Assessment>>): List<Group> {
    val seen = HashSet<String>()
    val unique = pairs.sortedByDescending { it.second.score }.filter { seen.add(canonical(it.first.url)) }
    val groups = mutableListOf<Group>()
    for (pair in unique) {
        val (cand, _) = pair
        val tk = titleKey(cand.title)
        val target = groups.firstOrNull { g ->
            val lead = g.members[0].first
            val sameTitle = jaccard(tk, titleKey(lead.title)) >= 0.7
            val sameSnip = cand.snippet.isNotEmpty() && lead.snippet.isNotEmpty() && cand.snippet.length > 60 &&
                jaccard(titleKey(cand.snippet), titleKey(lead.snippet)) >= 0.8
            sameTitle || sameSnip
        }
        if (target != null) target.members.add(pair) else groups.add(Group(mutableListOf(pair)))
    }
    return groups
}
