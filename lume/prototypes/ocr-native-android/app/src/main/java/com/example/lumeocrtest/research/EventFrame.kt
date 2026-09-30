package com.example.lumeocrtest.research

import java.util.Calendar
import java.util.TimeZone

/*
 * Comparação estruturada de acontecimentos. A busca (inclusive a semântica) só encontra candidatas; a decisão
 * de "mesmo acontecimento" vem daqui: quem agiu (e em que papel), qual ação e em que etapa, sobre o quê,
 * quando (data do fato ≠ data de publicação), números, prazos e negação. Tudo é lido de trechos literais
 * da matéria importada e da fonte; cada decisão guarda os trechos que a sustentam.
 *
 * As classes de ação são vocabulário geral do português jornalístico (acionar a Justiça, aprovar, proibir,
 * declarar...). Nenhuma regra conhece veículos, pessoas, clubes ou manchetes específicas.
 */

object EventRelation {
    const val MESMO = "mesmo_acontecimento"
    const val DIVERGENTE = "detalhe_divergente"
    const val CONTEXTO = "contexto_relacionado"
    const val OUTRO = "outro_acontecimento"
    const val INCERTA = "relacao_incerta"
}

/** Trecho literal usado na comparação. `onde`: captura | titulo | resumo | pagina. */
data class EventEvidence(val onde: String, val texto: String)

data class EventFrame(
    /** Frase (literal) de onde o quadro foi lido. */
    val sentence: String,
    val agentText: String,
    val agentNames: Set<String>,
    val agentWords: Set<String>,
    /** Classe da ação (ex.: "justica", "aprovar") e as palavras literais que a indicam. */
    val action: String?,
    val actionText: String?,
    val objectKeys: Set<String>,
    val names: Set<String>,
    val negated: Boolean,
    val numbers: List<Pair<String, String>>,
    val stage: String?,
    /** Nomes que recebem a ação (ex.: o tribunal acionado). */
    val target: Set<String> = emptySet(),
    /** Palavras do objeto em posição de substantivo. */
    val nouns: Set<String> = emptySet(),
    /** Quem é citado como origem do dado ("diz BC", "mostra CNC"). */
    val attribution: Set<String> = emptySet(),
    /** Substantivos dentro do trecho de quem agiu ("o coro das casas de apostas"): podem ser assunto. */
    val agentNouns: Set<String> = emptySet(),
)

/** Data do fato: explícita no texto (dia, mês ou ano) ou, na falta, a data de publicação. */
data class FactDate(val ms: Long, val granularity: String, val text: String, val explicit: Boolean)

data class OriginalEvent(
    val frame: EventFrame,
    /** Palavras do objeto vindas do título, subtítulo e abertura da matéria importada. */
    val objectKeys: Set<String>,
    val agentNames: Set<String>,
    val agentWords: Set<String>,
    val names: Set<String>,
    val date: FactDate?,
    val evidence: List<EventEvidence>,
    /** Texto usado para decidir quais palavras são nomes próprios (capitalização no meio da frase). */
    val contextText: String,
    val aliases: Map<String, List<String>>,
    /** Palavras do assunto em posição de substantivo. */
    val nouns: Set<String> = emptySet(),
    /** Ações das outras orações da afirmação ("proíbe X e determina Y"): também descrevem o mesmo ato. */
    val otherActions: Set<String> = emptySet(),
    /** Origem do dado citada na matéria importada ("segundo o Banco Central"). */
    val attribution: Set<String> = emptySet(),
)

data class EventComparison(
    val kind: String,
    /** Frase curta para a pessoa (sem jargão). */
    val resumo: String,
    /** Por que: quem, ação, objeto, data, números, cada um com o resultado da comparação. */
    val motivos: List<String>,
    /** Trechos da fonte e da captura que sustentam a decisão (literais). */
    val trechos: List<EventEvidence>,
    /** pagina | trecho | titulo: o que da fonte foi realmente lido. */
    val base: String,
    val alertas: List<String>,
    /** Mesmo tipo de fato, em data anterior: contexto que não mostra que o fato ocorreu agora. */
    val anterior: Boolean = false,
)

// ------------------------------------------------------------------------------------------------
// Vocabulário geral

private class ActionPattern(val cls: String, val re: Regex)

private fun ap(cls: String, pattern: String) = ActionPattern(cls, Regex("(?<![a-z0-9])(?:$pattern)(?![a-z0-9])"))

private const val COURT = "(?:stf|stj|tse|tst|stm|trf\\d?|tj\\w*|supremo|justica|tribunal|tribunais|corte|cortes|juiz|juiza|vara)"

/** Ordem importa só como desempate; vence a ocorrência mais à esquerda na frase. */
private val ACTIONS = listOf(
    ap("justica", "acion\\w+|recorr\\w+|contest(?:a|am|ou|aram)|ajuiz\\w+|protocol\\w+|petici\\w+|impetr\\w+|process(?:a|am|ou|aram|ara|ar)"),
    ap("justica", "(?:entr\\w+|ingress\\w+) com (?:uma |um |nova |novo |o |a )?(?:acao|acoes|pedido|pedidos|peticao|recurso|mandado|representacao|queixa|habeas)"),
    ap("justica", "ingress\\w+ (?:em|na|no|numa|num|em uma) (?:\\w+ ){0,2}(?:acao|processo|$COURT)"),
    ap("justica", "(?:pede|pedem|pediu|pediram|pedido de) ingresso"),
    ap("justica", "(?:vai|vao|foi|foram|ir|leva\\w*|entr\\w+|ingress\\w+|recorre\\w*) (?:ao|a|a|aos|na|no|junto ao) $COURT"),
    ap("justica", "(?:pede|pedem|pediu|pediram|solicit\\w+|requer\\w*|cobr\\w+) (?:ao|a|aos|junto ao) $COURT"),
    ap("rejeitar", "(?:nega|negam|negou|negaram|rejeit\\w+|arquiv\\w+|indefer\\w+) (?:o |a |os |as )?(?:recurso|recursos|pedido|pedidos|habeas|liminar|acao|mandado|denuncia)"),
    ap("confirmar", "confirm\\w+|nega|negam|negou|negaram|desment\\w+"),
    ap("prazo", "(?:da|dao|deu|deram|estabelec\\w+|fix\\w+|defin\\w+|concede\\w*|concedeu) (?:um |o |novo )?(?:prazo|\\d+)"),
    ap("aprovar", "aprov\\w+|sancion\\w+|promulg\\w+"),
    ap("proibir", "proib\\w+|proibe|bane|baniu|banir|ved(?:a|am|ou|ar)|restring\\w+"),
    ap("revogar", "revog\\w+|suspend\\w+|suspens\\w+|derrub\\w+|anul\\w+|cancel\\w+|interromp\\w+|cass(?:a|am|ou|aram)|sust(?:a|am|ou|aram)|invalid\\w+|revert(?:e|em|eu)"),
    ap("rejeitar", "rejeit\\w+|arquiv\\w+"),
    ap("decidir", "decid\\w+|determin\\w+|julg\\w+|mantem|manteve|autoriz\\w+|liber(?:a|am|ou|aram)|conden\\w+|absolv\\w+|decret\\w+|ordena\\w*|acat(?:a|am|ou|aram)|defer(?:e|em|iu)|restabelec\\w+|restaur(?:a|am|ou)|preve|preveem|previu|estabelec(?:e|em|eu)"),
    ap("propor", "propo\\w+|propos\\w*|sugere\\w*|sugeriu|planej\\w+|pretend\\w+|articul\\w+|prepar\\w+|estud(?:a|am)|projeto de lei|avali(?:a|am)"),
    ap("anunciar", "anunci\\w+|lanc(?:a|am|ou|aram)|divulg\\w+"),
    ap("declarar", "diz|dizem|disse|disseram|afirm\\w+|declar\\w+|defend\\w+|critic\\w+|coment\\w+|posicion\\w+|manifest\\w+|rebat\\w+|alert\\w+|reag\\w+|cobr(?:a|am|ou)|elogi\\w+|atac\\w+"),
    ap("pedir", "pede|pedem|pediu|pediram|solicit\\w+|requer\\w*|reivindic\\w+"),
    ap("investigar", "investig\\w+|apur\\w+|denunci\\w+|indici\\w+|prend\\w+|preso|presos|presa|operacao"),
    ap("visitar", "visit\\w+|inaugur\\w+|particip\\w+|(?:vai|vao|foi|foram) (?:a|ao) (?:final|festa|evento|jogo|posse|cerimonia)"),
    ap("contratar", "contrat\\w+|assin\\w+|renov\\w+|patrocin\\w+|fech\\w+ acordo"),
    ap("competir", "venc\\w+|derrot\\w+|perd(?:e|em|eu|eram)|empat\\w+|golei\\w+|elimin\\w+|classific\\w+"),
    ap("ocorrencia", "transbord\\w+|ating\\w+|desab\\w+|incendi\\w+|alag\\w+|explod\\w+|colid\\w+|morr\\w+|mata|matou"),
    ap("nomear", "nome(?:a|ia|ou|iam)|nomead\\w+|demit\\w+|exoner\\w+|renunci\\w+|elege|elegem|elegeu|eleit(?:o|a|os|as)|reeleit(?:o|a)|empossad\\w+|assum(?:e|em|iu|iram)"),
    // Indicadores: sobe/cai. Direções opostas sobre o mesmo indicador são detalhe divergente, não outro fato.
    ap("alta", "sob(?:e|em)|subi\\w*|subir|aument(?:a|am|ou|aram)|cresc(?:e|em|eu|eram)|avanc(?:a|am|ou|aram)|dispar(?:a|am|ou)|dobr(?:a|am|ou)|(?:bate|batem|bateu|atinge|atingem|atingiu|renova|renovou) (?:novo |um novo |o |nova )?(?:recorde|maxima|pico)"),
    ap("queda", "cai|caem|caiu|recu(?:a|am|ou)|diminu(?:i|em|iu)|despenc\\w+|reduz|reduzem|reduziu|encolh\\w+|(?:atinge|atingiu|bate|bateu) (?:o |a |novo |nova )?(?:minima|piso)"),
)

private val DECISION_CLASSES = setOf("aprovar", "proibir", "revogar", "rejeitar", "decidir")
private val COURT_WORDS = setOf("stf", "stj", "tse", "tst", "supremo", "justica", "tribunal", "corte", "acao", "pedido", "peticao",
    "recurso", "ingresso", "mandado").map { topicKey(it) }.toSet()
private val ATTRIBUTION = Regex("(?iu)(?:,|—|-|;)?\\s*(?:diz|dizem|mostra|mostram|aponta|apontam|revela|revelam|indica|indicam|segundo|conforme|de acordo com)\\s+(?:o |a |os |as )?([\\p{L}\\p{N}.&-]+(?:\\s+[\\p{Lu}\\p{N}][\\p{L}\\p{N}.&-]*){0,3})\\s*$")

/** Origem do dado citada no fim da frase ("…, diz Banco Central", "…, mostra CNC"). */
fun attributionIn(sentence: String): Set<String> {
    val end = ATTRIBUTION.find(sentence.trim().trimEnd('.'))?.groupValues?.get(1)
    // "segundo dados do Banco Central", "de acordo com a Serasa" no meio da frase (só nomes próprios: instituições).
    val mid = ATTRIBUTION_MID.findAll(sentence).map { it.groupValues[1] }.toList()
    return (listOfNotNull(end) + mid).flatMap { tokens(it) }.filter { w -> w.length >= 2 && w !in STOP_NORM && w !in CONNECTORS }.toSet()
}

private val ATTRIBUTION_MID = Regex("(?u)(?:[Ss]egundo|[Cc]onforme|[Dd]e acordo com)\\s+(?:(?:os )?dados\\s+d[oa]s?\\s+|o\\s+|a\\s+)?(\\p{Lu}[\\p{L}]+(?:\\s+\\p{Lu}[\\p{L}]+){0,2})")
private val OPPOSITE = setOf("alta", "queda")

/** Mesma classe de ação; alta e queda contam como a mesma ação (sobre o mesmo indicador), em direções opostas. */
private fun sameClass(a: String?, b: String?) = a != null && b != null && (a == b || setOf(a, b) == OPPOSITE)
private val PLAN_CLASSES = setOf("propor", "anunciar")

/** Núcleos genéricos de instituição: sozinhos não identificam quem agiu ("governo", "clube"). */
private val GENERIC_HEADS = setOf("governo", "prefeitura", "camara", "secretaria", "ministerio", "tribunal", "justica",
    "federacao", "associacao", "entidade", "entidades", "clube", "clubes", "time", "times", "empresa", "empresas",
    "vereadores", "deputados", "senadores", "ministros", "ministro", "ministra", "presidente", "governador", "governadora",
    "prefeito", "prefeita", "policia", "estado", "municipio", "cidade", "orgao", "instituto", "conselho", "comissao",
    "sindicato", "partido", "grupo", "jogadores", "torcedores", "moradores", "autoridades", "especialistas", "agremiacao",
    "equipe", "equipes", "federal", "estadual", "municipal", "nacional", "brasileiro", "brasileira", "diretoria")
    .flatMap { listOf(it, topicKey(it)) }.toSet()

private val LIGHT = setOf("primeiro", "primeira", "segundo", "segunda", "ultimo", "ultima", "novo", "nova", "mais",
    "menos", "quer", "querem", "pode", "podem", "sera", "vai", "vao", "tambem", "apenas", "ainda", "sobre", "contra",
    "apos", "antes", "depois", "hoje", "ontem", "noite", "manha", "tarde", "entenda", "veja", "saiba", "diz", "afirma",
    // verbos de atribuição ("…, mostra Instituto X"): indicam a origem do dado, não o assunto
    "mostra", "mostram", "aponta", "apontam", "revela", "revelam", "indica", "indicam", "conforme", "acordo")

// "se" fica de fora: nos títulos é quase sempre pronome ("se posiciona"), não condição.
private val SUBORDINATORS = setOf("que", "apos", "depois", "antes", "enquanto", "quando", "caso", "sobre")
private val INTRO = Regex("(?iu)^\\s*(?:após|apos|depois|antes|durante|diante|com|sem|segundo|conforme|em meio|apesar|mesmo com|horas|dias|semanas|meses|foragid\\p{L}*|enquanto)\\b")
private val QUOTED = Regex("[“\"«][^”\"»]{3,160}[”\"»]\\s*:?")

// ------------------------------------------------------------------------------------------------
// Tokens com posição

private class Tok(val n: String, val lit: String, val start: Int, val capital: Boolean, val sentenceStart: Boolean)

private fun toks(text: String): List<Tok> {
    val out = mutableListOf<Tok>()
    for (m in WORD_RE.findAll(text)) {
        val before = text.substring(0, m.range.first)
        val sentStart = before.isBlank() || Regex("(?:[.!?:\"“(]|\\n)\\s*$").containsMatchIn(before)
        for (piece in norm(m.value).split(" ").filter { it.isNotEmpty() }) {
            out.add(Tok(piece, m.value, m.range.first, m.value.first().isUpperCase(), sentStart))
        }
    }
    return out
}

/** Texto entre dois tokens ("" quando são partes da mesma palavra, como em "rubro-negro"). */
private fun between(text: String, a: Tok, b: Tok): String =
    if (b.start <= a.start) "" else text.substring(minOf(a.start + a.lit.length, b.start), b.start)

/** Todas as classes de ação presentes na frase, em qualquer oração. */
private fun actionClassesIn(text: String): Set<String> {
    val s = toks(text).joinToString(" ") { it.n }
    return ACTIONS.filter { it.re.containsMatchIn(s) }.map { it.cls }.toSet()
}

/** Primeira ação da oração principal: (índice inicial, índice final exclusivo, classe). */
private fun findAction(t: List<Tok>): Triple<Int, Int, String>? {
    if (t.isEmpty()) return null
    val joined = StringBuilder()
    val startOf = IntArray(t.size)
    t.forEachIndexed { i, tok -> if (i > 0) joined.append(' '); startOf[i] = joined.length; joined.append(tok.n) }
    val s = joined.toString()
    fun tokAt(ch: Int) = startOf.indexOfLast { it <= ch }
    val hits = ACTIONS.flatMap { p -> p.re.findAll(s).map { m -> Triple(tokAt(m.range.first), tokAt(m.range.last) + 1, p.cls) }.toList() }
        .sortedWith(compareBy({ it.first }, { -(it.second - it.first) }))
    if (hits.isEmpty()) return null
    // Ação só em oração secundária ("após proibição das bets…", "…, que pede…"): a frase não diz o que o sujeito fez.
    // O conectivo pertence à ação mais próxima depois dele: em "que proíbe rinhas prevê", "que" é de "proíbe".
    return hits.firstOrNull { h ->
        (maxOf(0, h.first - 3) until h.first).none { i -> t[i].n in SUBORDINATORS && hits.none { o -> o.first in (i + 1) until h.first } }
    }
}

// ------------------------------------------------------------------------------------------------
// Nomes próprios e palavras do objeto

/** Palavras de nomes próprios: entidades, siglas e palavras com inicial maiúscula que aparecem no meio de frase no contexto. */
private fun nameTokens(text: String, contextText: String): Set<String> {
    val out = mutableSetOf<String>()
    extractEntities(text).forEach { e -> tokens(e).filter { it !in CONNECTORS && it !in STOP_NORM }.forEach { out.add(it) } }
    for (tok in toks(text)) {
        if (!tok.capital || tok.n in STOP_NORM || tok.n.length < 2) continue
        if (tok.lit.length >= 2 && tok.lit.all { !it.isLetter() || it.isUpperCase() }) { out.add(tok.n); continue }
        // Maiúscula no meio de uma frase (depois de palavra e espaço na mesma linha) indica nome próprio.
        if (tok.sentenceStart && Regex("(?<=[\\p{L},;] )${Regex.escape(tok.lit)}(?![\\p{L}])").containsMatchIn(contextText)) out.add(tok.n)
    }
    return out - GENERIC_HEADS
}

private fun contentKeys(t: List<Tok>, exclude: Set<String>): Set<String> = t.map { it.n }
    .filter { it.length >= 3 && it !in STOP_NORM && it !in NEG_NORM && it !in GENERIC && it !in TIME_WORDS && it !in LIGHT &&
        it !in GENERIC_HEADS && it !in exclude && !it.any(Char::isDigit) }
    .map { topicKey(it) }.toSet()

private val NUM_UNIT = Regex("(?<![\\d/.,])(\\d+(?:[.,]\\d+)?)\\s*(%|[\\p{L}]+)")

/** Números com a palavra que os acompanha ("30 dias", "12%"), sem anos e sem datas. */
fun numbersWithUnit(text: String): List<Pair<String, String>> = NUM_UNIT.findAll(text).mapNotNull { m ->
    val v = m.groupValues[1]
    // "%" some na normalização: é tratado antes.
    val unit = if (m.groupValues[2] == "%") "%" else norm(m.groupValues[2])
    if (v.length == 4 && (v.startsWith("19") || v.startsWith("20"))) return@mapNotNull null
    if (unit in STOP_NORM || unit.isEmpty() || unit in TIME_WORDS && unit !in setOf("dias", "meses", "anos", "semanas", "horas")) return@mapNotNull null
    numberKey(v) to (if (unit == "%") "%" else topicKey(unit))
}.toList()

/** Número como aparece no texto ("30 dias", "12%"), para mostrar à pessoa. */
fun literalNumber(text: String, value: String, unit: String): String =
    NUM_UNIT.findAll(text).firstOrNull { m -> numberKey(m.groupValues[1]) == value &&
        (if (unit == "%") m.groupValues[2] == "%" else topicKey(norm(m.groupValues[2])) == unit) }?.value ?: "$value $unit"

/** Palavras como aparecem no texto ("bets", "casas de apostas") para as chaves comparadas ("bet", "casa"). */
private fun literalWords(text: String, keys: Set<String>): List<String> =
    WORD_RE.findAll(text).map { it.value }.filter { w -> tokens(w).any { topicKey(it) in keys } }.distinctBy { topicKey(norm(it)) }.toList()

private fun stripQuotes(s: String) = QUOTED.replace(s, " ").trim().ifBlank { s }

/** Quadro do acontecimento numa frase (título, resumo ou frase da página). */
fun eventFrame(sentence: String, contextText: String): EventFrame {
    val clean = stripQuotes(sentence)
    val t = toks(clean)
    val act = findAction(t)
    val names = nameTokens(clean, contextText)
    if (act == null) {
        // Sem ação reconhecida: o sujeito provável é o nome no início da frase ("Criadores ameaçam...", "IPTU de X: ...").
        val lead = mutableListOf<Tok>()
        for ((i, tok) in t.withIndex()) {
            if (i > 0 && between(clean, t[i - 1], tok).let { ':' in it || ';' in it || ',' in it }) break
            if (tok.capital || (lead.isNotEmpty() && tok.n in CONNECTORS) || (lead.isNotEmpty() && tok.start == lead.last().start)) lead += tok else break
        }
        while (lead.isNotEmpty() && lead.last().n in CONNECTORS) lead.removeAt(lead.size - 1)
        val leadWords = lead.map { it.n }.filter { it.length >= 3 && it !in STOP_NORM && it !in LIGHT }.map { topicKey(it) }.toSet()
        return EventFrame(sentence, lead.joinToString(" ") { it.lit }, lead.map { it.n }.filter { it in names }.toSet(), leadWords,
            null, null, contentKeys(t, names), names, hasNegation(clean), numbersWithUnit(clean), actStage(clean), emptySet(), nounKeys(t, names))
    }
    val (a0, a1, cls) = act
    // Oração da ação: começa depois do último ":" ou ";" antes dela.
    val clauseStart = (a0 - 1 downTo 1).firstOrNull { i ->
        between(clean, t[i - 1], t[i]).let { ';' in it || ':' in it }
    } ?: 0
    val afterAll = t.subList(a1, t.size).let { rest ->
        val stop = rest.indices.firstOrNull { i -> i > 0 && between(clean, rest[i - 1], rest[i]).let { ';' in it || ':' in it } }
        rest.subList(0, stop ?: rest.size)
    }
    var agentToks = t.subList(clauseStart, a0)
    var agentText = if (agentToks.isEmpty()) "" else clean.substring(agentToks.first().start, t[a0].start).trim()
    if (',' in agentText && INTRO.containsMatchIn(agentText)) {
        // "Após o pedido, X decide": quem agiu vem depois da introdução.
        val comma = clean.lastIndexOf(',', t[a0].start)
        agentToks = agentToks.filter { it.start > comma }
        agentText = clean.substring(comma + 1, t[a0].start).trim()
    }
    var objToks = afterAll.take(14)
    // Voz passiva ("foi aprovado pelos vereadores"): quem agiu vem depois de "pelo/pela"; o sujeito é o objeto.
    val passive = t[a0].n.matches(Regex(".*(ado|ada|ados|adas|ido|ida|idos|idas)")) &&
        (maxOf(clauseStart, a0 - 3) until a0).any { t[it].n in setOf("foi", "foram", "sera", "serao", "seria", "sao", "e", "era", "sendo", "ser", "sido") }
    if (passive) {
        val by = afterAll.indexOfFirst { it.n in setOf("pelo", "pela", "pelos", "pelas") }
        objToks = agentToks
        agentToks = if (by >= 0) afterAll.drop(by + 1).take(5) else emptyList()
        agentText = agentToks.joinToString(" ") { it.lit }
    }
    val negated = (maxOf(0, a0 - 3) until a0).any { t[it].n in NEG_NORM } ||
        cls == "confirmar" && (t[a0].n.startsWith("neg") || t[a0].n.startsWith("desment"))
    val actionText = clean.substring(t[a0].start, t[a1 - 1].start + t[a1 - 1].lit.length)
    val agentNames = agentToks.map { it.n }.filter { it in names }.toSet()
    // Rótulo curto de quem agiu: as palavras de nome (literais) ou as primeiras palavras do trecho.
    val namedLit = agentToks.filter { it.n in names }.map { it.lit }.distinct()
    agentText = agentToks.filter { it.n !in STOP_NORM && it.n !in LIGHT && it.n !in GENERIC }.map { it.lit }.distinct().take(4)
        .joinToString(" ").ifBlank { namedLit.joinToString(" ") }
    val agentWords = agentToks.map { it.n }.filter { it.length >= 3 && it !in STOP_NORM && it !in LIGHT && it !in NEG_NORM && it !in GENERIC }
        .map { topicKey(it) }.toSet()
    val actionWords = t.subList(a0, a1).map { it.n }.toSet()
    // Alvo da ação (ex.: o tribunal acionado) não é o objeto: nomes logo depois da ação.
    val target = afterAll.take(2).map { it.n }.filter { it in names }.toSet() + actionWords.filter { it in names }
    val phraseTail = contentKeys(t.subList(a0 + 1, a1), names) - COURT_WORDS
    val obj = contentKeys(objToks, names + actionWords) + phraseTail
    return EventFrame(sentence, agentText, agentNames, agentWords, cls, actionText, obj, names, negated,
        numbersWithUnit(clean), actStage(clean), target, nounKeys(objToks, names + actionWords) + phraseTail,
        attributionIn(clean), nounKeys(agentToks, names))
}

private val DETERMINERS = setOf("o", "a", "os", "as", "um", "uma", "da", "do", "das", "dos", "de", "no", "na", "nos", "nas",
    "ao", "aos", "contra", "sobre", "com", "pela", "pelo", "sem", "em", "num", "numa")

/** Palavras em posição de substantivo (depois de artigo ou preposição): distinguem assunto de verbos soltos. */
private fun nounKeys(t: List<Tok>, exclude: Set<String>): Set<String> =
    // Infinitivo depois de preposição ("tentativa de derrubar") é verbo, não substantivo.
    contentKeys(t.filterIndexed { i, tok -> i > 0 && t[i - 1].n in DETERMINERS && !(tok.n.length > 4 && tok.n.matches(Regex(".*(ar|er|ir)"))) }, exclude)

// ------------------------------------------------------------------------------------------------
// Datas do fato

private val UTC = TimeZone.getTimeZone("UTC")
private val WEEKDAY_NUM = mapOf("domingo" to Calendar.SUNDAY, "segunda" to Calendar.MONDAY, "terca" to Calendar.TUESDAY,
    "quarta" to Calendar.WEDNESDAY, "quinta" to Calendar.THURSDAY, "sexta" to Calendar.FRIDAY, "sabado" to Calendar.SATURDAY)
private val D_M = Regex("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?(?![\\d/])")
private val WEEKDAY_RE = Regex("(?iu)(?:nest[ae]|dest[ae]|na|no|na última|no último|na ultima|no ultimo|nesta última|neste último)?\\s*(segunda|terça|terca|quarta|quinta|sexta|sábado|sabado|domingo)(?:-feira)?(?:\\s*\\((\\d{1,2})(?:/(\\d{1,2}))?\\))?")
private val MONTH_YEAR = Regex("(?iu)(?:em\\s+)?(${MONTHS.keys.joinToString("|")})\\s+de\\s+(\\d{4})")
private val MONTH_ONLY = Regex("(?iu)(?<![\\p{L}])(?:em|de)\\s+(${MONTHS.keys.joinToString("|")})(?![\\p{L}])(?!\\s+de\\s+\\d{4})")
private val IN_YEAR = Regex("(?iu)\\bem\\s+(19\\d\\d|20\\d\\d)\\b")

private fun cal(ms: Long) = Calendar.getInstance(UTC).apply { timeInMillis = ms }
private fun dayStart(ms: Long): Long = cal(ms).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

/** Data do fato citada no texto, resolvida pela data de publicação ("nesta terça-feira (29)", "hoje à noite", "29/9"). */
fun factDateIn(text: String, publishedMs: Long?, coarse: Boolean = false): FactDate? {
    val pub = publishedMs?.let { dayStart(it) }
    D_M.find(text)?.let { m ->
        val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
        if (d in 1..31 && mo in 1..12) {
            val c = cal(pub ?: return@let)
            var y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it } ?: c.get(Calendar.YEAR)
            if (m.groupValues[3].isEmpty() && mo > c.get(Calendar.MONTH) + 2) y -= 1
            val ms = Calendar.getInstance(UTC).apply { clear(); set(y, mo - 1, d) }.timeInMillis
            return FactDate(ms, "dia", m.value, true)
        }
    }
    if (pub != null) {
        WEEKDAY_RE.findAll(text).firstOrNull { it.groupValues[1].isNotEmpty() }?.let { m ->
            val c = cal(pub)
            val day = m.groupValues[2].toIntOrNull()
            if (day != null && day in 1..31) {
                if (day > c.get(Calendar.DAY_OF_MONTH) + 1) c.add(Calendar.MONTH, -1)
                c.set(Calendar.DAY_OF_MONTH, day)
                return FactDate(c.timeInMillis, "dia", m.value.trim(), true)
            }
            val wd = WEEKDAY_NUM[norm(m.groupValues[1])] ?: return@let
            while (c.get(Calendar.DAY_OF_WEEK) != wd) c.add(Calendar.DAY_OF_MONTH, -1)
            return FactDate(c.timeInMillis, "dia", m.value.trim(), true)
        }
        val n = " ${norm(text)} "
        if (" anteontem " in n) return FactDate(pub - 2 * Dates.DAY_MS, "dia", "anteontem", true)
        if (" ontem " in n) return FactDate(pub - Dates.DAY_MS, "dia", "ontem", true)
        if (" hoje " in n || " nesta noite " in n || " nesta manha " in n || " nesta tarde " in n) return FactDate(pub, "dia", "hoje", true)
    }
    MONTH_YEAR.find(text)?.let { m ->
        val mo = MONTHS[m.groupValues[1].lowercase()] ?: return@let
        val ms = Calendar.getInstance(UTC).apply { clear(); set(m.groupValues[2].toInt(), mo - 1, 1) }.timeInMillis
        return FactDate(ms, "mes", m.value.trim(), true)
    }
    if (coarse && publishedMs != null) MONTH_ONLY.find(text)?.let { m ->
        val mo = MONTHS[m.groupValues[1].lowercase()] ?: return@let
        val c = cal(publishedMs)
        var y = c.get(Calendar.YEAR)
        if (mo > c.get(Calendar.MONTH) + 1) y -= 1
        val ms = Calendar.getInstance(UTC).apply { clear(); set(y, mo - 1, 1) }.timeInMillis
        return FactDate(ms, "mes", m.value.trim(), true)
    }
    if (coarse) IN_YEAR.find(text)?.let { m ->
        val ms = Calendar.getInstance(UTC).apply { clear(); set(m.groupValues[1].toInt(), 0, 1) }.timeInMillis
        return FactDate(ms, "ano", m.value.trim(), true)
    }
    return null
}

/** Diferença entre duas datas do fato, respeitando a precisão ("janeiro de 2024" x dia). null = compatíveis. */
private fun dateGap(a: FactDate, b: FactDate): String? {
    val ca = cal(a.ms); val cb = cal(b.ms)
    val coarse = setOf(a.granularity, b.granularity)
    return when {
        "ano" in coarse -> if (ca.get(Calendar.YEAR) != cb.get(Calendar.YEAR)) "ano" else null
        "mes" in coarse -> if (ca.get(Calendar.YEAR) != cb.get(Calendar.YEAR) || ca.get(Calendar.MONTH) != cb.get(Calendar.MONTH)) "mes" else null
        else -> if (Math.abs(dayStart(a.ms) - dayStart(b.ms)) > Dates.DAY_MS) "dia" else null
    }
}

// ------------------------------------------------------------------------------------------------
// Matéria importada

private val SENT = Regex("(?<=[.!?])\\s+(?=[\\p{Lu}\"“])|\\n+")

fun sentencesIn(text: String): List<String> = text.split(SENT).map { it.trim() }.filter { it.length >= 12 }

/**
 * Acontecimento da matéria importada: o quadro da afirmação (título ou frase escolhida), complementado pelo
 * subtítulo e pelas frases da abertura que falam do mesmo agente ou da mesma ação.
 */
fun originalEvent(claim: String, context: ArticleContext?, nowMs: Long): OriginalEvent {
    // "Hoje", "ontem": na captura, relativos à data da matéria (quando lida); num texto digitado, ao momento da pesquisa.
    val refDate = context?.publishedAtMs ?: if (context == null) nowMs else null
    val ctxText = listOfNotNull(claim, context?.title, context?.subtitle, context?.body).joinToString("\n")
    var frame = eventFrame(claim, ctxText)
    if (frame.action == null && context?.title != null && context.title != claim) {
        eventFrame(context.title, ctxText).takeIf { it.action != null }?.let { frame = it }
    }
    val evidence = mutableListOf(EventEvidence("captura", claim))
    val obj = frame.objectKeys.toMutableSet()
    val nouns = frame.nouns.toMutableSet()
    val agentKeys = frame.agentNames + (frame.agentWords - GENERIC_HEADS)
    fun absorb(f: EventFrame) {
        // Do trecho de quem agiu, só os substantivos ("coro das casas de apostas"), não os adjetivos que descrevem
        // o próprio agente ("o clube carioca").
        obj += f.objectKeys + (f.agentNouns - agentKeys - GENERIC_HEADS)
        nouns += f.nouns + (f.agentNouns - agentKeys - GENERIC_HEADS)
    }
    // O subtítulo descreve o mesmo acontecimento da manchete.
    context?.subtitle?.let { sub -> sentencesIn(sub).forEach { absorb(eventFrame(it, ctxText)) } }
    var date: FactDate? = null
    for (s in sentencesIn(context?.body?.take(900).orEmpty()).take(4)) {
        val f = eventFrame(s, ctxText)
        val sameAgent = ((f.names + f.agentWords + f.objectKeys) intersect agentKeys).isNotEmpty()
        // Mesma ação em qualquer oração da frase ("a medida provisória que proíbe…") já basta para absorver o assunto.
        val sameAction = frame.action != null && (f.action == frame.action || frame.action in actionClassesIn(s))
        if (sameAgent || sameAction) {
            absorb(f)
            if (sameAction) evidence.add(EventEvidence("captura", s))
            if (date == null && sameAction) date = factDateIn(s, refDate)
        }
    }
    // "Em agosto" na própria afirmação é a referência do fato (ex.: indicador do mês).
    if (date == null) date = factDateIn(claim, refDate, coarse = true)
    if (date == null) context?.publishedAtMs?.let { date = FactDate(it, "dia", "data de publicação da matéria", false) }
    val aliases = if (context != null) resolveAliases(claim, context.fullText) else emptyMap()
    // Outras orações coordenadas da afirmação, com o mesmo sujeito: "MP proíbe bets e determina encerramento…".
    val otherActions = claim.split(Regex("\\s+e\\s+|;\\s*")).drop(1).mapNotNull { part -> findAction(toks(part))?.third }.toSet() - setOfNotNull(frame.action)
    val attribution = (listOf(claim) + sentencesIn(listOfNotNull(context?.subtitle, context?.body?.take(900)).joinToString("\n")).take(3))
        .flatMap { attributionIn(it) + Regex("(?iu)(?:segundo|conforme|de acordo com|dados d[oa])\\s+(?:o |a )?(\\p{Lu}[\\p{L}]+(?:\\s+\\p{Lu}[\\p{L}]+){0,2})").findAll(it).flatMap { m -> tokens(m.groupValues[1]) } }
        .filter { it.length >= 2 && it !in STOP_NORM }.toSet()
    return OriginalEvent(frame, obj - frame.names - agentKeys, frame.agentNames, frame.agentWords, frame.names, date,
        evidence.distinct(), ctxText, aliases, nouns - frame.names - agentKeys, otherActions, attribution)
}

// ------------------------------------------------------------------------------------------------
// Comparação

private fun expandAliases(keys: Set<String>, aliases: Map<String, List<String>>): Set<String> {
    val out = keys.toMutableSet()
    aliases.forEach { (k, vs) ->
        val kt = tokens(k).toSet()
        if ((kt intersect keys).isNotEmpty()) vs.forEach { v -> out += tokens(v).filter { it !in CONNECTORS } }
        vs.forEach { v -> if (((tokens(v).toSet() - CONNECTORS) intersect keys).isNotEmpty()) out += kt }
    }
    return out - GENERIC_HEADS
}

private enum class Agent { MATCH, CONFLICT, UNKNOWN }

private fun compareAgent(o: OriginalEvent, f: EventFrame): Agent {
    val oNames = expandAliases(o.agentNames, o.aliases)
    val oWords = o.agentWords - GENERIC_HEADS
    val candAll = f.agentNames + f.agentWords
    // Papéis invertidos: quem agiu na matéria importada aparece só como alvo ou objeto na fonte.
    val objectNames = f.names - f.agentNames
    if (oNames.isNotEmpty() && (objectNames intersect oNames).isNotEmpty() && (candAll intersect oNames).isEmpty() &&
        (candAll - GENERIC_HEADS).isNotEmpty()) return Agent.CONFLICT
    if (candAll.isEmpty()) return Agent.UNKNOWN
    if (oNames.isNotEmpty() && (candAll intersect oNames).isNotEmpty()) return Agent.MATCH
    if (oNames.isEmpty() && oWords.isNotEmpty() && (f.agentWords intersect oWords).isNotEmpty()) return Agent.MATCH
    // Só núcleo genérico ("clubes", "governo") de um lado: não dá para dizer que é outro agente.
    val candDistinct = candAll - GENERIC_HEADS
    if (oNames.isEmpty() && oWords.isEmpty()) return Agent.UNKNOWN
    // Núcleo genérico sozinho ("clubes", "governo") não identifica; uma descrição ("sindicato dos clubes") identifica outro.
    if (candDistinct.isEmpty() && candAll.size <= 1) return Agent.UNKNOWN
    return Agent.CONFLICT
}

private fun fmt(ms: Long) = Dates.format(ms)

/**
 * Compara a matéria importada com uma fonte. Lê o título; quando ele não basta, a abertura do resumo e da página
 * (primeiras frases que retomam o título). Frases soltas do meio da página não definem o foco da fonte.
 *
 * `semantic`: similaridade (0–1) entre a afirmação e o título da fonte, calculada por um modelo local quando
 * disponível. Só decide se o ASSUNTO é equivalente quando quem agiu e a ação já coincidem pelo texto;
 * nunca cria agente, ação, data ou trecho.
 */
fun compareEvent(o: OriginalEvent, cand: Candidate, nowMs: Long, semantic: Double? = null): EventComparison {
    val base = when {
        cand.pageText.isNotBlank() -> "pagina"
        cand.snippet.isNotBlank() -> "trecho"
        else -> "titulo"
    }
    val ctx = listOf(o.contextText, cand.title, cand.snippet, cand.pageText.take(3000)).joinToString("\n")
    val titleFrame = eventFrame(cand.title, ctx)
    val titleStems = contentKeys(toks(cand.title), emptySet()) + titleFrame.names
    // Abertura: frases do resumo e as primeiras frases da página que retomam o título (pula menus e legendas).
    val leadSentences = buildList {
        sentencesIn(cand.snippet).take(2).forEach { add("resumo" to it) }
        sentencesIn(cand.pageText.take(4000)).filter { s ->
            ((contentKeys(toks(s), emptySet()) + nameTokens(s, ctx)) intersect titleStems).size >= 2
        }.take(3).forEach { add("pagina" to it) }
    }
    val leadFrames = leadSentences.map { (w, s) -> Triple(w, s, eventFrame(s, ctx)) }
    val alerts = mutableListOf<String>()
    if (base == "titulo") alerts.add("Lemos apenas o título; abra a fonte para conferir o conteúdo.")
    else if (base == "trecho") alerts.add("Lemos apenas o resumo do buscador; a página não foi lida.")
    val oF = o.frame
    val objAll = expandAliases(o.objectKeys, o.aliases)
    val oNamesAll = expandAliases(o.names, o.aliases)

    // Nomes do lado da fonte ("MP das Bets") também contam como assunto quando são palavras do assunto da matéria importada.
    fun objectHits(f: EventFrame) = (f.objectKeys + f.nouns + (f.agentWords - f.agentNames - GENERIC_HEADS) +
        (f.names - f.agentNames - f.target).map { topicKey(it) }).filter { it in objAll }.toSet() -
        // Palavras de processo ("corte", "ação", "pedido") aparecem em qualquer caso judicial: não definem o assunto.
        COURT_WORDS

    data class Verdict(val kind: String, val resumo: String, val motivos: List<String>, val evidence: List<EventEvidence>, val frame: EventFrame)

    fun judge(f: EventFrame, where: String, text: String): Verdict {
        val agent = compareAgent(o, f)
        val hits = objectHits(f)
        // Quando quem agiu é outro (ou não se sabe), o assunto em comum precisa ser um substantivo da matéria, não um verbo solto.
        val objMatch = if (agent == Agent.MATCH) hits.isNotEmpty() else (hits intersect (o.nouns + o.nouns.map { topicKey(it) })).isNotEmpty()
        val sameAction = sameClass(f.action, oF.action) || f.action != null && f.action in o.otherActions
        val m = mutableListOf<String>()
        m += if (f.action == null) "Quem: não dá para saber — o texto lido não diz o que aconteceu" else when (agent) {
            Agent.MATCH -> "Quem: ${f.agentText.ifBlank { "o mesmo" }} — o mesmo da matéria importada"
            Agent.CONFLICT -> "Quem: ${f.agentText.ifBlank { "outro" }} — não é quem agiu na matéria importada (${oF.agentText.ifBlank { "não identificado" }})"
            Agent.UNKNOWN -> "Quem: não dá para saber pelo texto lido"
        }
        m += when {
            f.action == null -> "Ação: o texto lido não diz o que aconteceu"
            sameAction -> "Ação: “${f.actionText}” — equivale a “${oF.actionText}”"
            else -> "Ação: “${f.actionText}” — diferente de “${oF.actionText}”"
        }
        m += if (objMatch) "Sobre o quê: ${literalWords(text, hits).ifEmpty { hits.toList() }.take(4).joinToString(", ")} — em comum"
            else "Sobre o quê: sem palavras do assunto em comum"
        val ev = listOf(EventEvidence(where, text))
        // Nomes totalmente diferentes (outro lugar, outra pessoa) quando quem agiu não é um nome reconhecido nos dois.
        val namedAgentMatch = agent == Agent.MATCH && o.agentNames.isNotEmpty()
        // Quem é citado como origem do dado ("diz Instituto X") não é participante do fato: fica fora desta comparação.
        val oPart = oNamesAll - o.attribution
        val fPart = f.names - f.attribution
        if (!namedAgentMatch && oPart.isNotEmpty() && fPart.isNotEmpty() && (oPart intersect fPart).isEmpty()) {
            return Verdict(EventRelation.OUTRO, "Trata de outras pessoas ou de outro lugar",
                m + "Nomes citados: ${f.names.joinToString(", ")} — nenhum aparece na matéria importada", ev, f)
        }
        if (f.action == null) {
            val sharedNames = (oNamesAll intersect f.names).size
            return when {
                agent != Agent.CONFLICT && (objMatch || sharedNames >= 2) ->
                    Verdict(EventRelation.INCERTA, "Trata do mesmo assunto, mas o que foi lido não diz o que aconteceu", m, ev, f)
                objMatch -> Verdict(EventRelation.CONTEXTO, "Trata do mesmo assunto, sem relatar este acontecimento", m, ev, f)
                else -> Verdict(EventRelation.OUTRO, "Outro assunto com palavras parecidas", m, ev, f)
            }
        }
        if (!sameAction) {
            val stageNote = when {
                oF.action in DECISION_CLASSES && f.action in PLAN_CLASSES -> "Outra etapa: fala de proposta ou anúncio, não da decisão"
                oF.action in PLAN_CLASSES && f.action in DECISION_CLASSES -> "Outra etapa: fala de decisão; a matéria importada, de proposta ou anúncio"
                else -> null
            }
            return when {
                objMatch && agent == Agent.MATCH -> Verdict(EventRelation.CONTEXTO, stageNote ?: "Mesmo envolvido, outra ação sobre o mesmo assunto", m, ev, f)
                objMatch -> Verdict(EventRelation.CONTEXTO, stageNote ?: "Outro fato sobre o mesmo assunto", m, ev, f)
                else -> Verdict(EventRelation.OUTRO, if (agent == Agent.MATCH) "Mesmo envolvido, outro assunto" else "Outro acontecimento com nomes ou palavras em comum", m, ev, f)
            }
        }
        return when (agent) {
            Agent.CONFLICT -> if (objMatch) Verdict(EventRelation.CONTEXTO, "Outra pessoa ou instituição fez algo parecido sobre o mesmo assunto", m, ev, f)
                else Verdict(EventRelation.OUTRO, "Mesma ação, mas por outro envolvido e sobre outro assunto", m, ev, f)
            Agent.UNKNOWN -> if (objMatch) Verdict(EventRelation.INCERTA, "Parece o mesmo acontecimento, mas o texto lido não diz quem agiu", m, ev, f)
                else Verdict(EventRelation.OUTRO, "Mesma ação sobre outro assunto", m, ev, f)
            Agent.MATCH -> {
                // Sem assunto além de quem agiu (ex.: "Desemprego cai em agosto"): nada a comparar, nada que contradiga.
                if (objMatch || f.objectKeys.isEmpty() && objAll.isEmpty()) return Verdict(EventRelation.MESMO, "Relata o mesmo acontecimento", m, ev, f)
                // O título diz quem e o quê; a abertura (mesma ação, sem outro agente) pode dizer sobre o quê.
                val lead = leadFrames.firstOrNull { (_, _, lf) -> lf.action == oF.action && compareAgent(o, lf) != Agent.CONFLICT && objectHits(lf).isNotEmpty() }
                if (lead != null) return Verdict(EventRelation.MESMO, "Relata o mesmo acontecimento",
                    m.dropLast(1) + "Sobre o quê: ${literalWords(lead.second, objectHits(lead.third)).ifEmpty { objectHits(lead.third).toList() }.take(4).joinToString(", ")} — em comum (na abertura do texto)",
                    ev + EventEvidence(lead.first, lead.second), f)
                when {
                    semantic != null && semantic >= SEMANTIC_SAME -> Verdict(EventRelation.MESMO, "Relata o mesmo acontecimento",
                        m.dropLast(1) + "Sobre o quê: palavras diferentes, consideradas equivalentes pela comparação de sentido (IA local)", ev, f)
                    semantic != null && semantic < SEMANTIC_OTHER -> Verdict(EventRelation.OUTRO, "Mesmo envolvido e mesma ação, mas sobre outro assunto", m, ev, f)
                    else -> Verdict(EventRelation.INCERTA, "Mesmo envolvido e mesma ação, mas não dá para confirmar que é o mesmo assunto", m, ev, f)
                }
            }
        }
    }

    // O título define o foco; a abertura só substitui o título quando ele não diz o que aconteceu ou quem agiu.
    var v = judge(titleFrame, "titulo", cand.title)
    val titleAgent = compareAgent(o, titleFrame)
    val namesDiffer = v.resumo == "Trata de outras pessoas ou de outro lugar"
    // A abertura só substitui o título quando o título não identifica outro envolvido e não diz, com ação reconhecida,
    // algo diferente (ex.: título na voz passiva, com apelido ou verbo fora do vocabulário).
    val leadAllowed = titleAgent != Agent.CONFLICT && !namesDiffer && v.kind != EventRelation.MESMO &&
        (v.kind == EventRelation.INCERTA || titleFrame.action == null || titleAgent == Agent.UNKNOWN && titleFrame.action == oF.action)
    if (leadAllowed) {
        leadFrames.map { (w, s, f) -> judge(f, w, s) }.firstOrNull { it.kind == EventRelation.MESMO }
            ?.let { v = it.copy(evidence = listOf(EventEvidence("titulo", cand.title)) + it.evidence) }
    } else if (v.kind == EventRelation.CONTEXTO && titleAgent == Agent.CONFLICT && titleFrame.action == oF.action) {
        // Apelido no título ("Rubro-Negro"): a primeira frase da abertura costuma trazer o nome.
        leadFrames.firstOrNull()?.let { (w, s, f) -> judge(f, w, s).takeIf { it.kind == EventRelation.MESMO } }
            ?.let { v = it.copy(motivos = it.motivos + "O título nomeia quem agiu de outra forma; a abertura do texto confirma quem foi",
                evidence = listOf(EventEvidence("titulo", cand.title)) + it.evidence) }
    }
    val motivos = v.motivos.toMutableList()
    val evidence = v.evidence.toMutableList()
    var kind = v.kind
    var resumo = v.resumo
    var anterior = false

    if (kind == EventRelation.INCERTA) {
        val od = o.date
        val pub = cand.published
        if (od != null && od.explicit && od.granularity == "dia" && pub != null && dayStart(pub) < dayStart(od.ms) - Dates.DAY_MS) {
            kind = EventRelation.OUTRO
            resumo = "Publicado em ${fmt(pub)}, antes do fato relatado na matéria importada (${fmt(od.ms)}): não pode relatá-lo"
            motivos += "Quando: publicado em ${fmt(pub)}, antes de ${fmt(od.ms)}"
        }
    }
    if (kind == EventRelation.MESMO) {
        val support = leadFrames.firstOrNull { (_, _, f) -> f.action == oF.action && compareAgent(o, f) != Agent.CONFLICT }
        support?.let { (w, s, _) -> if (evidence.none { it.texto == s }) evidence.add(EventEvidence(w, s)) }
        // Mês ou ano soltos ("em outubro", "em 2011") só valem como data do fato quando a matéria importada também
        // se refere a um mês/ano; senão costumam ser prazo futuro ou histórico.
        val coarse = o.date?.granularity in setOf("mes", "ano")
        val candDate = (listOf(cand.title) + listOfNotNull(support?.second) + leadSentences.map { it.second })
            .firstNotNullOfOrNull { factDateIn(it, cand.published, coarse) }
            ?: cand.published?.let { FactDate(it, "dia", "data de publicação", false) }
        val od = o.date
        if (od != null && candDate != null) {
            val gap = dateGap(od, candDate)
            val cd = dayStart(candDate.ms); val odd = dayStart(od.ms)
            val candLabel = if (candDate.explicit) "“${candDate.text}” (${fmt(candDate.ms)})" else "publicado em ${fmt(candDate.ms)}"
            val origLabel = if (od.explicit) fmt(od.ms) else "${fmt(od.ms)} (data de publicação)"
            // Com a data do fato nos dois lados, a diferença é real; só com a data de publicação da matéria importada,
            // o fato pode ter ocorrido até alguns dias antes dela.
            val verdict = when {
                candDate.explicit && od.explicit -> if (gap == null) null else if (cd < odd) "antes" else "depois"
                candDate.explicit -> when { cd < odd - 7 * Dates.DAY_MS -> "antes"; cd > odd + Dates.DAY_MS -> "depois"; else -> null }
                od.explicit -> {
                    // Fato de um mês ("em agosto") costuma ser noticiado até semanas depois; de um dia, até uma semana.
                    val end = when (od.granularity) {
                        "mes" -> cal(odd).apply { add(Calendar.MONTH, 1) }.timeInMillis + 45 * Dates.DAY_MS
                        "ano" -> cal(odd).apply { add(Calendar.YEAR, 1) }.timeInMillis + 90 * Dates.DAY_MS
                        else -> odd + 7 * Dates.DAY_MS
                    }
                    when { cd < odd - Dates.DAY_MS -> "antes"; cd > end -> "tarde"; else -> null }
                }
                else -> when { cd < odd - 7 * Dates.DAY_MS -> "antes"; cd > odd + 7 * Dates.DAY_MS -> "tarde"; else -> null }
            }
            when (verdict) {
                "antes" -> {
                    kind = EventRelation.CONTEXTO; anterior = true
                    resumo = "Fato parecido em data anterior ($candLabel); não mostra que ocorreu agora"
                    motivos += "Quando: $candLabel — antes de $origLabel"
                }
                "depois" -> {
                    kind = EventRelation.OUTRO; resumo = "Fato parecido em outra data ($candLabel)"
                    motivos += "Quando: $candLabel — depois de $origLabel"
                }
                "tarde" -> {
                    kind = EventRelation.INCERTA; resumo = "Pode ser outro episódio: publicado em ${fmt(candDate.ms)}, sem dizer a data do fato"
                    motivos += "Quando: publicado em ${fmt(candDate.ms)}; a matéria importada é de $origLabel"
                }
                else -> motivos += "Quando: $candLabel — compatível com $origLabel"
            }
        } else if (od == null && candDate != null && nowMs - candDate.ms > RECENT_DAYS * Dates.DAY_MS) {
            kind = EventRelation.INCERTA
            resumo = "A matéria importada não mostra a data; esta publicação é de ${fmt(candDate.ms)} e pode ser de outro episódio"
            motivos += "Quando: data da matéria importada não identificada"
        } else if (od == null) {
            motivos += "Quando: a matéria importada não mostra a data; comparado pela data de publicação da fonte" + (candDate?.let { " (${fmt(it.ms)})" } ?: "")
        }
    }
    if (kind == EventRelation.MESMO) {
        // Negação e números sobre o mesmo ato: mesmo acontecimento com informação diferente.
        val frames = listOf(v.frame) + leadFrames.map { it.third }.filter { (sameClass(it.action, oF.action) || it.action in o.otherActions) && compareAgent(o, it) != Agent.CONFLICT }
        val candNeg = v.frame.negated
        val candAttr = v.frame.attribution + leadFrames.flatMap { it.third.attribution }
        if (oF.action in OPPOSITE && candAttr.isNotEmpty()) {
            // Indicador: cada instituição tem o seu levantamento. Origem diferente = outro dado; sem origem na matéria = incerto.
            if (o.attribution.isNotEmpty() && (candAttr intersect o.attribution).isEmpty()) {
                kind = EventRelation.CONTEXTO
                resumo = "Outro levantamento sobre o mesmo tema (dado de ${candAttr.joinToString(" ").uppercase()})"
                motivos += "Origem do dado: a fonte cita ${candAttr.joinToString(" ")}; a matéria importada, ${o.attribution.joinToString(" ")}"
            } else if (o.attribution.isEmpty()) {
                // A afirmação, como está, não diz qual levantamento: a relação fica, com o aviso para conferir.
                motivos += "Origem do dado: a fonte cita ${candAttr.joinToString(" ").uppercase()}; a matéria importada não diz de onde vem o dado"
                alerts.add("A fonte cita dados de ${candAttr.joinToString(" ").uppercase()}; a matéria importada não diz de onde vem o dado. Confira se é o mesmo levantamento.")
            } else motivos += "Origem do dado: ${candAttr.joinToString(" ").uppercase()} — a mesma da matéria importada"
        }
        if (kind == EventRelation.MESMO && setOf(oF.action, v.frame.action) == OPPOSITE) {
            kind = EventRelation.DIVERGENTE
            resumo = "Trata do mesmo indicador, mas na direção oposta (“${v.frame.actionText}” × “${oF.actionText}”)"
            motivos += "Direção: a fonte diz “${v.frame.actionText}”; a matéria importada, “${oF.actionText}”"
        }
        if (kind == EventRelation.MESMO && candNeg != oF.negated) {
            kind = EventRelation.DIVERGENTE
            resumo = if (candNeg) "Trata do mesmo fato, mas o nega" else "Trata do mesmo fato sem a negação da matéria importada"
            motivos += "Negação: a fonte ${if (candNeg) "nega" else "não nega"}; a matéria importada ${if (oF.negated) "nega" else "afirma"}"
        }
        if (kind == EventRelation.MESMO || kind == EventRelation.DIVERGENTE) for ((value, unit) in oF.numbers) {
            val same = frames.firstOrNull { f -> (value to unit) in f.numbers }
            val other = frames.firstNotNullOfOrNull { f -> f.numbers.firstOrNull { it.second == unit && it.first != value }?.let { f to it } }
            when {
                same != null -> {
                    motivos += "Detalhe: cita ${literalNumber(same.sentence, value, unit)}"
                    if (evidence.none { it.texto == same.sentence }) evidence.add(EventEvidence(if (same === v.frame) v.evidence.first().onde else "pagina", same.sentence))
                }
                other != null -> {
                    kind = EventRelation.DIVERGENTE
                    val theirs = literalNumber(other.first.sentence, other.second.first, other.second.second)
                    val ours = literalNumber(oF.sentence, value, unit)
                    resumo = "Relata o mesmo acontecimento com outro número: $theirs (a matéria importada diz $ours)"
                    motivos += "Detalhe: $theirs × $ours"
                    if (evidence.none { it.texto == other.first.sentence }) evidence.add(EventEvidence(v.evidence.first().onde, other.first.sentence))
                }
                else -> motivos += "Detalhe: ${literalNumber(oF.sentence, value, unit)} não aparece no que foi lido"
            }
        }
    }
    if (kind == EventRelation.MESMO && base == "titulo") resumo = "Pelo título, relata o mesmo acontecimento"
    val all = listOf(o.evidence.first()) + evidence
    return EventComparison(kind, resumo, motivos, all.distinct().map { it.copy(texto = it.texto.take(400)) }, base, alerts, anterior)
}

/** Limiares da similaridade de sentido (modelo local), calibrados nos pares difíceis em português. */
const val SEMANTIC_SAME = 0.72
const val SEMANTIC_OTHER = 0.55

/**
 * Consulta montada pela estrutura do acontecimento: quem agiu, a quem a ação se dirige e o assunto
 * (substantivos da afirmação), sem verbos secundários. Ex.: "Flamengo STF bets proibição".
 */
fun eventQuery(o: OriginalEvent, claim: String): String? {
    val f = o.frame
    if (f.action == null) return null
    val words = WORD_RE.findAll(stripQuotes(claim)).map { it.value }.toList()
    fun literal(keys: Set<String>) = words.filter { w -> tokens(w).any { it in keys || topicKey(it) in keys } }
    val agent = literal(f.agentNames).ifEmpty { literal((f.agentWords - GENERIC_HEADS)).take(2) }
    val target = literal(f.target)
    val subject = (literal(f.nouns) + literal(f.objectKeys)).distinct().filter { it !in agent && it !in target }.take(2)
    if (agent.isEmpty() || subject.isEmpty()) return null
    return (agent + target + subject).distinct().joinToString(" ")
}
