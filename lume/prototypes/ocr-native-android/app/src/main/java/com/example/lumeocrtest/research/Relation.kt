package com.example.lumeocrtest.research

/*
 * Relação de cada fonte com a afirmação pesquisada. Não diz se a afirmação é verdadeira: diz se a fonte
 * trata do mesmo acontecimento e o que ela mostra sobre o detalhe (números, prazos, negação, etapa do ato).
 * Só usa texto efetivamente recebido (título, trecho do buscador ou página lida) e registra qual foi.
 */

object RelationKind {
    const val MESMO_FATO = "mesmo_fato"          // mesmo acontecimento/afirmação
    const val DIFERENTE = "diferente"            // mesmo acontecimento, informação diferente sobre o detalhe
    const val CONTEXTO = "contexto"              // útil, mas não confirma o detalhe específico
    const val ANTERIOR = "anterior"              // publicação anterior sobre tema parecido
    const val OUTRO = "outro"                    // outra pessoa/instituição/evento com termos semelhantes
    const val INDEFINIDO = "indefinido"          // evidência insuficiente para decidir
}

object DetailStatus {
    const val CITA = "cita_detalhe"
    const val DIFERENTE = "detalhe_diferente"
    const val NAO_CITA = "nao_cita_detalhe"
    const val SEM_DETALHE = "sem_detalhe"         // a afirmação não tem número/prazo a comparar
}

data class SourceRelation(
    val tipo: String,
    val detalhe: String,
    /** Frase curta para a pessoa: "Cita o prazo de 30 dias", "Traz outro número: 10 dias". */
    val resumo: String,
    /** Trecho exato do texto recebido que sustenta o resumo (nunca inventado). */
    val trecho: String?,
    /** De onde veio o texto avaliado: "pagina", "trecho" (buscador) ou "titulo". */
    val base: String,
    val alertas: List<String> = emptyList(),
)

private val SENT_SPLIT = Regex("(?<=[.!?;])\\s+|\\n+")

/** Números da afirmação com a palavra que os acompanha ("30 dias", "5%"). */
private fun claimNumbers(interp: Interpretation): List<Pair<String, String>> =
    interp.numeros.map { n ->
        val after = Regex("${Regex.escape(n.texto)}\\s*(%|[\\p{L}]+)?").find(interp.assunto)?.groupValues?.getOrNull(1).orEmpty()
        n.chave to norm(after)
    }

fun relate(interp: Interpretation, cand: Candidate, a: Assessment, semantic: String? = null): SourceRelation {
    val base = when {
        cand.contentKind == "completo" -> "pagina"
        cand.snippet.isNotBlank() -> "trecho"
        else -> "titulo"
    }
    val texts = buildList {
        add(cand.title)
        if (cand.snippet.isNotBlank()) addAll(cand.snippet.split(SENT_SPLIT))
        if (cand.pageText.isNotBlank()) addAll(cand.pageText.split(SENT_SPLIT))
    }.map { it.trim() }.filter { it.length >= 12 }
    val alerts = mutableListOf<String>()
    val kind = when (a.category) {
        "descartado" -> RelationKind.OUTRO
        "anterior" -> RelationKind.ANTERIOR
        "contexto" -> RelationKind.CONTEXTO
        "direto" -> RelationKind.MESMO_FATO
        else -> RelationKind.INDEFINIDO
    }
    if (semantic == "outro" && kind != RelationKind.OUTRO) alerts.add("A comparação de contexto indicou outro acontecimento; confira.")
    if (kind != RelationKind.MESMO_FATO) {
        val resumo = when (kind) {
            RelationKind.ANTERIOR -> "Publicação anterior sobre tema parecido"
            RelationKind.CONTEXTO -> "Trata do tema, mas não confirma o detalhe pesquisado"
            RelationKind.OUTRO -> "Outro assunto com palavras parecidas"
            else -> "Não foi possível decidir a relação com o que foi lido"
        }
        return SourceRelation(kind, DetailStatus.SEM_DETALHE, resumo, null, base, alerts)
    }

    // Mesmo acontecimento: o que a fonte mostra sobre o detalhe numérico/prazo.
    val nums = claimNumbers(interp)
    val claimNeg = interp.negacoes.isNotEmpty()
    val claimStage = interp.etapa
    var tipo = RelationKind.MESMO_FATO
    val status: String
    val resumo: String
    var trecho: String? = null
    if (nums.isEmpty() && interp.partes.size >= 2) {
        // Afirmação com partes: diz quais partes aparecem no que foi lido.
        val all = texts.joinToString(" ")
        val stems = stemsOf(all)
        val covered = interp.partes.map { p -> p.all { stem(it) in stems } }
        val missing = interp.partes.withIndex().filter { !covered[it.index] }.map { it.value.joinToString(" ") }
        status = if (missing.isEmpty()) DetailStatus.CITA else DetailStatus.NAO_CITA
        trecho = texts.filter { it != cand.title }.maxByOrNull { s -> (stemsOf(s) intersect interp.termos.map { stem(it) }.toSet()).size }
            ?.takeIf { s -> (stemsOf(s) intersect interp.termos.map { stem(it) }.toSet()).size >= 2 }
        resumo = if (missing.isEmpty()) "Relata todas as partes da afirmação"
            else "Relata o acontecimento; não menciona “${missing.joinToString("”, “")}” no que foi lido"
    } else if (nums.isEmpty()) {
        status = DetailStatus.SEM_DETALHE
        val best = texts.maxByOrNull { s -> (stemsOf(s) intersect interp.termos.map { stem(it) }.toSet()).size }
        trecho = best?.takeIf { it != cand.title }
        resumo = "Relata o mesmo acontecimento"
    } else {
        val (key, unit) = nums.first()
        // Palavras que dizem A QUE o número se refere (as que o antecedem na afirmação e, no texto da matéria,
        // as frases com o mesmo número), não o tema geral.
        val anchor = (interp.prazos + interp.numeros.map { it.texto }).first()
        val specific = termsNear(interp.assunto, anchor, interp.termos).map { stem(it) }.toSet() +
            (interp.detalheStems - interp.termos.map { stem(it) }.toSet() - stemsOf(interp.entidades.joinToString(" ")))
        val topic = interp.termos.map { stem(it) }.toSet()
        fun about(s: String) = (stemsOf(s) intersect specific).isNotEmpty() || (stemsOf(s) intersect topic).size >= 2
        val same = texts.firstOrNull { s -> key in numbersIn(s) && (unit.isEmpty() || stem(unit) in stemsOf(s) || unit == "%") && about(s) }
            ?: texts.firstOrNull { s -> key in numbersIn(s) && (unit.isEmpty() || stem(unit) in stemsOf(s) || unit == "%") &&
                windowAround(s, listOf(key), 6).map { stem(it) }.any { it in specific } }
        // "Diferente" só quando a fonte NÃO cita o número da afirmação e dá outro valor ao mesmo detalhe.
        val other = if (same != null || texts.any { key in numbersIn(it) }) null else texts.firstOrNull { s ->
            val sn = numbersIn(s).filter { it != key }
            unit.isNotEmpty() && sn.isNotEmpty() && Regex("(?<![\\d,.])(${sn.joinToString("|") { Regex.escape(it) }})\\s*${Regex.escape(unit)}")
                .containsMatchIn(norm(s)) && windowAround(s, sn, 5).map { stem(it) }.any { it in specific }
        }
        when {
            same != null -> {
                trecho = same; status = DetailStatus.CITA
                resumo = "Cita o mesmo detalhe (${interp.prazos.firstOrNull() ?: interp.numeros.first().texto})"
                if (hasNegation(same) != claimNeg) {
                    tipo = RelationKind.DIFERENTE
                    alerts.add("O trecho tem sentido de negação diferente da afirmação.")
                }
            }
            other != null -> {
                trecho = other; status = DetailStatus.DIFERENTE; tipo = RelationKind.DIFERENTE
                resumo = "Traz informação diferente sobre este detalhe"
            }
            else -> {
                status = DetailStatus.NAO_CITA
                resumo = if (base == "pagina") "Relata o acontecimento, mas não menciona o detalhe pesquisado"
                    else "Relata o acontecimento; o detalhe não aparece no que foi lido"
            }
        }
    }
    // Etapa do ato: proposta ≠ decisão ≠ vigência ≠ revogação.
    val sourceStage = actStage(listOfNotNull(cand.title, trecho).joinToString(". "))
    if (claimStage != null && sourceStage != null && claimStage != sourceStage &&
        setOf(claimStage, sourceStage).any { it in setOf("proposto", "revogado", "negado") }) {
        alerts.add("A fonte descreve outra etapa (${stageLabel(sourceStage)}); a afirmação fala em ${stageLabel(claimStage)}.")
    }
    if (base == "titulo") alerts.add("Lemos apenas o título; abra a fonte para conferir o conteúdo.")
    else if (base == "trecho") alerts.add("Lemos apenas o resumo do buscador; a página não foi lida.")
    return SourceRelation(tipo, status, resumo, trecho?.take(400), base, alerts)
}

fun stageLabel(stage: String) = when (stage) {
    "proposto" -> "proposta"; "aprovado" -> "aprovação"; "decidido" -> "decisão já tomada"; "vigente" -> "vigência"
    "revogado" -> "suspensão ou revogação"; "negado" -> "rejeição"; "previsto" -> "previsão"; else -> stage
}
