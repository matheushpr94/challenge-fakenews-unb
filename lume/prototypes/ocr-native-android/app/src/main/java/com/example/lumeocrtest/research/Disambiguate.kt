package com.example.lumeocrtest.research

/*
 * Esclarecimento antes da pesquisa: "O que você quer saber?".
 * Esta versão não tem modelo de linguagem; só detecta padrões estruturais fundamentados:
 *  - termo com vários significados ou nome compartilhado (sentidos vindos da Wikipédia);
 *  - característica x aparência em entradas telegráficas ("substantivo + adjetivo perceptivo");
 *  - pergunta geral x episódio específico (ação no passado sem sujeito nomeado, sem data).
 * Não há lista de assuntos. Ambiguidades de tempo, local ou categoria não são detectadas.
 */

const val MAX_ROUNDS = 2
private const val MAX_OPTIONS = 3
const val QUESTION = "O que você quer saber?"
const val NOT_THIS = "Não é isso — adicionar detalhes"

// Léxico gramatical de adjetivos perceptivos (cor, brilho, forma). Não é uma lista de assuntos.
private val PERCEPTUAL_BASE = listOf("azul", "vermelho", "verde", "amarelo", "laranja", "roxo", "rosa", "branco", "preto",
    "cinza", "marrom", "dourado", "prateado", "violeta", "brilhante", "escuro", "claro", "transparente", "plano",
    "redondo", "quadrado", "esférico", "oval")
private val COPULAS = setOf("e", "sao", "fica", "ficam", "ficou", "esta", "estao", "parece", "parecem", "era", "foi")
private val PAST_RE = Regex("^[a-z]{3,}(ou|eu|iu|aram|eram|iram)$")
private val SKIP_BEFORE_NOUN = setOf("maior", "menor", "principal", "unico", "unica", "grande", "pequeno", "pequena",
    "mais", "menos", "antigo", "antiga", "famoso", "famosa", "importante")
private val DEFINITION_RE = Regex("^\\s*(?:(O|A|Os|As)(?!\\p{L}))?[^.;]{0,80}?\\s(?:é|são|foi)\\s+(um|uma|o|a|uns|umas)\\s+((?:\\p{L}+\\s+){0,2}\\p{L}+)")

private fun forms(base: String): Set<String> {
    val b = norm(base)
    return when {
        b.endsWith("o") -> setOf(b, b.dropLast(1) + "a", b + "s", b.dropLast(1) + "as")
        b.endsWith("l") -> setOf(b, b.dropLast(1) + "is")
        b.endsWith("e") || b.endsWith("a") -> setOf(b, b + "s")
        else -> setOf(b, b + "es", b + "s")
    }
}

private val PERCEPTUAL: Map<String, String> = PERCEPTUAL_BASE.flatMap { b -> forms(b).map { it to b } }.toMap()

data class Clarification(
    val precisaEscolher: Boolean,
    val textoPesquisa: String,
    val entradaOriginal: String,
    val detalhes: String = "",
    val rodada: Int = 0,
    val pergunta: String = QUESTION,
    val opcoes: List<Option> = emptyList(),
    val opcaoDetalhes: String = NOT_THIS,
    val metodo: String = "regras",
    val motivo: String = "",
    val aviso: String = "",
)

fun combine(texto: String, detalhes: String?): String {
    val t = texto.trim()
    val d = detalhes?.trim().orEmpty()
    return if (d.isEmpty()) t else "${t.trimEnd(' ', '?', '.', '!')}. $d"
}

fun plural(w: String): String {
    val lw = norm(w)
    return when {
        lw.endsWith("ao") -> w.dropLast(2) + "ões"
        lw.endsWith("al") || lw.endsWith("el") || lw.endsWith("ol") || lw.endsWith("ul") -> w.dropLast(1) + "is"
        lw.endsWith("m") -> w.dropLast(1) + "ns"
        lw.endsWith("r") || lw.endsWith("z") -> w + "es"
        lw.endsWith("s") -> w
        else -> w + "s"
    }
}

/** Concorda o adjetivo perceptivo com o gênero (plana/plano, vermelha/vermelho). */
fun agree(adj: String, feminine: Boolean): String {
    val base = PERCEPTUAL[norm(adj)] ?: return adj
    if (!norm(base).endsWith("o")) return adj
    return if (feminine) base.dropLast(1) + "a" else base
}

private data class Definition(val categoria: String, val feminino: Boolean, val artigo: String?)

/** "O Sol é a estrela central..." -> categoria "estrela", feminino, artigo "O". */
private fun definition(extract: String): Definition? {
    val text = extract.replace(Regex("\\s*\\([^)]*\\)"), "")
    val m = DEFINITION_RE.find(text) ?: return null
    val noun = m.groupValues[3].split(Regex("\\s+")).firstOrNull { norm(it) !in ORDINALS && norm(it) !in SKIP_BEFORE_NOUN }
    if (noun == null || noun.length < 3) return null
    return Definition(noun.lowercase(), m.groupValues[2] in setOf("uma", "a", "umas"), m.groupValues[1].ifEmpty { null })
}

private fun telegraphicAppearance(clean: String, wiki: List<Candidate>): List<Option> {
    var words = WORD_RE.findAll(clean).map { it.value }.toList()
    if (words.size !in 2..5) return emptyList()
    var art: String? = null
    if (norm(words[0]) in setOf("o", "a", "os", "as")) { art = words[0]; words = words.drop(1) }
    if (words.size < 2 || words.any { norm(it) in COPULAS }) return emptyList() // com verbo de ligação já é explícita
    val adj = words.last()
    if (norm(adj) !in PERCEPTUAL) return emptyList()
    val subject = words.dropLast(1)
    if (subject.any { norm(it) in STOP_NORM } || subject.size > 3) return emptyList()
    var subj = subject.joinToString(" ")
    val page = wiki.firstOrNull { norm(it.title) == norm(subj) && !it.disambiguation }
    if (page != null) subj = page.title // grafia da fonte (ex.: nome próprio com maiúscula)
    val d = page?.let { definition(it.snippet) }
    // Gênero do sujeito: artigo digitado > artigo da definição na fonte > forma do adjetivo digitado.
    val fem = when {
        art != null -> norm(art) in setOf("a", "as")
        d?.artigo != null -> norm(d.artigo) in setOf("a", "as")
        else -> norm(PERCEPTUAL[norm(adj)] ?: "").endsWith("o") && norm(adj).endsWith("a")
    }
    val article = art?.replaceFirstChar { it.uppercase() } ?: if (fem) "A" else "O"
    val adjS = agree(adj, fem)
    val opts = mutableListOf(Option("$article $subj é $adjS?", "caracteristica_aparencia"),
        Option("$article $subj pode parecer $adjS em alguma situação?", "caracteristica_aparencia"))
    if (d != null) opts.add(Option("Existem ${plural(d.categoria)} ${plural(agree(adj, d.feminino))}?", "categoria"))
    return opts
}

private val PAREN_END = Regex("\\s*\\(.*\\)$")

/** Sentidos/entidades distintos na Wikipédia para o termo-chave. */
private fun senses(key: String, otherTerms: List<String>, wiki: List<Candidate>): List<Candidate> {
    val nk = norm(key)
    val hasDisamb = wiki.any { it.disambiguation && norm(PAREN_END.replace(it.title, "")) == nk }
    val senses = wiki.filter { c ->
        if (c.disambiguation) return@filter false
        val nt = norm(c.title)
        if (nt.split(" ").firstOrNull() in ARTICLE_TYPE_WORDS) return@filter false
        val base = norm(PAREN_END.replace(c.title, ""))
        base == nk || (" $nk " in " $nt " && nt.length > nk.length)
    }
    if (senses.size < 2 && !(hasDisamb && senses.isNotEmpty())) return emptyList()
    val others = otherTerms.map { stem(it) }.toSet()
    if (others.isNotEmpty()) {
        val matching = senses.filter { c -> (norm(c.title + " " + c.snippet).split(" ").map { stem(it) }.toSet() intersect others).isNotEmpty() }
        if (matching.size == 1) return emptyList() // o restante da entrada já escolhe um sentido
    }
    return if (senses.size >= 2) senses.take(MAX_OPTIONS) else emptyList()
}

private fun senseOptions(clean: String, key: String, senses: List<Candidate>): List<Option> {
    val onlyKey = norm(clean) == norm(key)
    return senses.map { c ->
        val text = if (onlyKey) "O que é ${c.title}?"
        else clean.trimEnd(' ', '?').replaceFirst(Regex(Regex.escape(key), RegexOption.IGNORE_CASE), Regex.escapeReplacement(c.title)) + "?"
        Option(text.replaceFirstChar { it.uppercase() }, "significado_ou_entidade")
    }
}

private fun episodeVsGeneral(clean: String, interp: Interpretation): List<Option> {
    val words = WORD_RE.findAll(clean).map { it.value }.toList()
    if (interp.entidades.isNotEmpty() || interp.anos.isNotEmpty() || interp.datas.isNotEmpty() ||
        interp.tempoRelativo.isNotEmpty() || words.size !in 2..10) return emptyList()
    if (words.none { PAST_RE.matches(norm(it)) && norm(it) !in STOP_NORM }) return emptyList()
    val phrase = clean.trimEnd(' ', '?', '.', '!')
    return listOf(Option("Houve um caso recente: “$phrase”?", "geral_especifico"),
        Option("Isso acontece em geral: “$phrase”?", "geral_especifico"))
}

fun ruleOptions(clean: String, interp: Interpretation, wiki: List<Candidate>): Pair<List<Option>, String> {
    telegraphicAppearance(clean, wiki).takeIf { it.isNotEmpty() }?.let { return it to "característica x aparência" }
    val short = WORD_RE.findAll(clean).count { norm(it.value) !in STOP_NORM } <= 4
    val key = interp.entidades.firstOrNull() ?: if (interp.incompleto) interp.termos.firstOrNull() else null
    if (key != null && (short || interp.incompleto)) {
        val s = senses(key, interp.termos.filter { norm(it) != norm(key) }, wiki)
        if (s.isNotEmpty()) return senseOptions(clean, key, s) to "termo com vários significados ou nome compartilhado"
    }
    episodeVsGeneral(clean, interp).takeIf { it.isNotEmpty() }?.let { return it to "pergunta geral x caso específico" }
    return emptyList<Option>() to ""
}

/** Palavras com maiúscula que não iniciam frase (candidatas a nomes próprios). */
private fun properNouns(text: String): Set<String> = Regex("\\p{L}[\\p{L}\\p{N}_\\-]*").findAll(text).mapNotNull { m ->
    val before = text.substring(0, m.range.first).trimEnd(' ', '“', '"', '\'', '(')
    if (m.value[0].isUpperCase() && before.isNotEmpty() && before.last() !in ".!?:") norm(m.value) else null
}.toSet()

/** Descarta opções que inventam nomes/números, repetem rejeitadas ou usam palavras de veredito. */
fun validateOptions(opts: List<Option>, allowedText: String, rejected: List<String>): List<Option> {
    val allowed = norm(allowedText).split(" ").toSet()
    fun content(t: String) = norm(t).split(" ").filter { it.isNotEmpty() && it !in STOP_NORM }.toSet()
    val rej = rejected.map { content(it) }
    val seen = mutableListOf<Set<String>>()
    val out = mutableListOf<Option>()
    for (o in opts) {
        var t = o.texto.split(Regex("\\s+")).joinToString(" ").take(140)
        if (t.isBlank() || Regex("$WB(verdadeir|fals|fake|mentira)", RegexOption.IGNORE_CASE).containsMatchIn(t)) continue
        if (!t.endsWith("?")) t += "?"
        val toks = norm(t).split(" ").toSet()
        if (properNouns(t).any { it !in allowed }) continue // nome ausente da entrada e do contexto
        if (toks.any { Regex("\\d+").matches(it) && it !in allowed }) continue // número/ano inventado
        val key = content(t)
        if (key in rej || key in seen) continue // mesma pergunta (já rejeitada ou repetida)
        seen.add(key)
        out.add(Option(t, o.diferenca))
    }
    return out.take(MAX_OPTIONS)
}

suspend fun analyzeClarification(
    texto: String, detalhes: String, rejeitadas: List<String>, rodada: Int, nowMs: Long,
    wikiLookup: suspend (String) -> List<Candidate>,
): Clarification {
    val original = texto.trim()
    val combined = combine(original, detalhes)
    val clean = cleanInput(combined)
    val base = Clarification(false, combined, original, detalhes.trim(), rodada)
    if (!Regex("$W{2,}").containsMatchIn(clean)) return base.copy(motivo = "entrada vazia")
    if (rodada >= MAX_ROUNDS) return base.copy(motivo = "limite de rodadas atingido; pesquisando com os detalhes informados")
    val interp = interpret(combined, nowMs)
    val key = interp.entidades.firstOrNull() ?: interp.termos.firstOrNull()
    var aviso = ""
    var wiki = emptyList<Candidate>()
    if (key != null) {
        try {
            wiki = wikiLookup(key)
        } catch (e: SourceException) {
            aviso = "Contexto da Wikipédia indisponível (${e.kind}); desambiguação limitada."
        }
    }
    val allowed = combined + " " + wiki.joinToString(" ") { "${it.title} ${it.snippet}" }
    val (raw, why) = ruleOptions(clean, interp, wiki)
    val opts = validateOptions(raw, allowed, rejeitadas)
    return if (opts.size >= 2) base.copy(precisaEscolher = true, opcoes = opts, motivo = why, aviso = aviso)
    else base.copy(motivo = if (why.isEmpty()) "intenção clara para as regras" else "opções insuficientes após filtrar; pesquisando direto", aviso = aviso)
}
