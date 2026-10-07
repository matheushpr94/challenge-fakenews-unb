package com.example.lumeocrtest.research

/*
 * Sinal de LINGUAGEM do texto da notícia (fato, citação, trecho enviesado), a partir do rótulo de cada frase.
 * Não diz se a notícia é verdadeira ou falsa: "enviesada" descreve o estilo da frase e "factual" só diz que a frase
 * relata algo. Os limites abaixo vêm de ml/factnews/reports/calibracao_dev.md (validação por história, sem o teste lacrado).
 */

object LanguageState {
    const val SINAL_DE_VIES = "sinal_de_vies"
    const val SEM_SINAL = "sem_sinal"
    const val INCONCLUSIVO = "inconclusivo"
    const val SEM_TEXTO = "sem_texto"
    const val INDISPONIVEL = "indisponivel"
}

data class SentenceSignal(val texto: String, val rotulo: String, val confianca: Double)

data class LanguageAnalysis(
    val estado: String,
    /** Frase curta para a tela. */
    val resumo: String,
    /** Por que o estado não é mais firme (pouco texto, confiança baixa, título × corpo…). */
    val motivos: List<String> = emptyList(),
    /** captura | pagina | digitado | nenhuma */
    val fonteTexto: String = "nenhuma",
    val fonteDescricao: String? = null,
    val titulo: SentenceSignal? = null,
    /** Indício no título: o modelo marcou "enviesada" com confiança moderada. Nunca conclui sozinho. */
    val indicioNoTitulo: Boolean = false,
    val frasesAnalisadas: Int = 0,
    /** Trechos curtos, repetidos ou sem cara de frase, descartados antes de classificar. */
    val frasesIgnoradas: Int = 0,
    /** Frases do texto que ficaram acima do limite de leitura e não foram classificadas. */
    val frasesNaoLidas: Int = 0,
    val contagem: Map<String, Int> = emptyMap(),
    val destaques: List<SentenceSignal> = emptyList(),
    val totalDestaques: Int = 0,
    /** Texto acima do que foi medido na calibração. Nota técnica: NÃO é mostrada ao usuário. */
    val alemDaCalibracao: Boolean = false,
)

/**
 * Mesmo título, tolerando acréscimos como o nome do veículo ("Título - Veículo"): quase todas as palavras do título buscado
 * aparecem no outro, sem muitas palavras a mais e com os mesmos números. Serve para achar, entre os resultados, a matéria
 * cujo título foi lido ou digitado; não comprova que o texto é idêntico.
 */
fun sameHeadline(wanted: String, other: String): Boolean {
    val a = titleKey(wanted); val b = titleKey(other)
    if (a.size < 3 || b.size < 3 || numbersIn(wanted) != numbersIn(other)) return false
    return (a intersect b).size.toDouble() / a.size >= 0.85 && jaccard(a, b) >= 0.5 && b.size <= a.size + 3
}

object LanguageRules {
    /** Frase do corpo destacada: rótulo "enviesada" com confiança >= este valor (precisão medida ~0,69; cobertura ~0,42). */
    const val HIGHLIGHT = 0.80
    /**
     * Título enviesado com confiança >= este valor vira só indício. A precisão medida em manchetes é de ~0,55 e NÃO sobe com
     * a confiança (0,49 em 0,85), por isso o título nunca conclui sozinho.
     */
    const val TITLE_HINT = 0.70
    /** A calibração por matéria foi feita com 8 frases ou mais no corpo. */
    const val MIN_BODY = 8
    /** Frases com a maior probabilidade abaixo disto contam como "em dúvida". Em matérias limpas ~3% (p90: 10%). */
    const val DOUBT = 0.70
    const val MAX_DOUBT_SHARE = 0.25
    /**
     * "Sinal de viés" pede 2 ou mais frases destacadas E pelo menos 10% do texto. Em matérias de 8 a 69 frases isso deu
     * 94% com ao menos uma frase enviesada de verdade e 87% com duas ou mais (só a contagem: 92% e 83%).
     */
    const val MIN_HIGHLIGHTS = 2
    const val MIN_SHARE = 0.10
    /** Maior matéria da base de medida (69 frases). Acima de cerca de 70, o resultado não foi calibrado. */
    const val MAX_MEASURED = 70
    const val MAX_SHOWN = 8
    const val MAX_SENTENCES = 200

    private const val ENDS = "[.!?…]"
    private val SPLIT = Regex("(?<=$ENDS)[\"”')\\]]*\\s+(?=[\"“'(\\[]?[A-ZÀ-ÝÇ0-9])")

    /** Divide em frases e descarta fragmentos (menus, legendas, linhas curtas). Devolve as frases e quantas foram ignoradas. */
    fun sentencesOf(text: String): Pair<List<String>, Int> {
        var ignored = 0
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (raw in text.split(Regex("\\n+")).flatMap { it.split(SPLIT) }) {
            val s = raw.replace(Regex("\\s+"), " ").trim()
            if (s.isEmpty()) continue
            val words = s.split(' ').size
            val letters = s.count { it.isLetter() }
            if (s.length !in 20..700 || words < 4 || letters < s.length * 0.6) { ignored++; continue }
            if (!seen.add(s.lowercase())) { ignored++; continue }
            out.add(s)
        }
        return out to ignored
    }

    /**
     * O que a confiança bruta do modelo realmente vale: precisão medida dos trechos previstos como "enviesada" por faixa de confiança
     * (validação por história, 5 sementes, reports/analises_oof_dev.md e calibracao_dev.md). A confiança bruta é alta demais
     * (0,98 bruto ≈ 78% de acerto), então a tela mostra esta estimativa em vez do número do modelo.
     */
    fun estimatedPrecision(confidence: Double): Double = when {
        confidence >= 0.95 -> 0.78
        confidence >= 0.90 -> 0.65
        confidence >= 0.80 -> 0.55
        else -> 0.47
    }

    fun unavailable(note: String = "A análise de linguagem não está disponível agora: o servidor local do modelo não respondeu (ligue-o com ml/factnews/scripts/start-factnews-server).") =
        LanguageAnalysis(LanguageState.INDISPONIVEL, note)

    /**
     * @param title título (da captura ou frase informada) e o rótulo dele, quando houver
     * @param body frases do corpo, na mesma ordem dos rótulos
     */
    fun assess(
        title: SentenceSignal?, body: List<SentenceSignal>, ignored: Int, source: String, sourceNote: String?, unread: Int = 0,
    ): LanguageAnalysis {
        val hint = title != null && title.rotulo == SentenceRole.ENVIESADA && title.confianca >= TITLE_HINT
        val counts = body.groupingBy { it.rotulo }.eachCount()
        val highlights = body.filter { it.rotulo == SentenceRole.ENVIESADA && it.confianca >= HIGHLIGHT }.sortedByDescending { it.confianca }
        val beyond = body.size + unread > MAX_MEASURED
        fun result(state: String, summary: String, reasons: List<String> = emptyList()) = LanguageAnalysis(
            state, summary, reasons, source, sourceNote, title, hint, body.size, ignored, unread, counts,
            highlights.take(MAX_SHOWN), highlights.size, beyond,
        )
        val titleNote = if (hint) "O título tem linguagem possivelmente enviesada (indício fraco: erra cerca de metade das vezes)." else null

        if (body.isEmpty()) {
            return if (hint) result(LanguageState.INCONCLUSIVO, "Só o título foi lido, e ele não basta para concluir.",
                listOfNotNull(titleNote, "Não foi possível ler o corpo da matéria."))
            else result(LanguageState.SEM_TEXTO, "Não há texto de matéria para analisar.",
                listOf("Só o título ou uma frase foi lido; sem o corpo não dá para avaliar a linguagem do texto."))
        }
        if (body.size < MIN_BODY) {
            return result(LanguageState.INCONCLUSIVO, "O texto lido é curto demais para concluir.",
                listOfNotNull("Foram lidas ${body.size} frases; a medida de confiança vale para textos com $MIN_BODY ou mais.", titleNote))
        }
        val doubt = body.count { it.confianca < DOUBT }.toDouble() / body.size
        if (doubt > MAX_DOUBT_SHARE) {
            return result(LanguageState.INCONCLUSIVO, "O modelo ficou em dúvida em muitas frases.",
                listOfNotNull("Em ${(doubt * 100).toInt()}% das frases a confiança foi baixa (o normal é cerca de 3%); isso é comum quando o texto veio com ruído.", titleNote))
        }
        val share = highlights.size.toDouble() / body.size
        return when {
            highlights.size >= MIN_HIGHLIGHTS && share >= MIN_SHARE -> result(LanguageState.SINAL_DE_VIES,
                "${highlights.size} de ${body.size} frases (${Math.round(share * 100)}%) têm sinal de linguagem enviesada.",
                listOfNotNull(titleNote))
            highlights.isNotEmpty() -> result(LanguageState.INCONCLUSIVO,
                if (highlights.size == 1) "Só 1 de ${body.size} frases tem sinal de viés." else "${highlights.size} de ${body.size} frases têm sinal de viés, pouco para o tamanho do texto.",
                listOfNotNull(if (highlights.size == 1) "Um trecho isolado não basta para dizer que o texto é enviesado."
                    else "Para indicar viés, o sinal precisa aparecer em pelo menos ${Math.round(MIN_SHARE * 100)}% das frases.", titleNote))
            hint -> result(LanguageState.INCONCLUSIVO, "O título e o corpo não apontam para o mesmo lado.",
                listOfNotNull(titleNote, "Nenhuma frase do corpo teve sinal de viés com confiança alta."))
            else -> result(LanguageState.SEM_SINAL, "Não encontramos sinal de viés nas ${body.size} frases lidas.",
                listOf("Isso não prova que o texto seja neutro: o modelo deixa passar cerca de metade dos trechos enviesados."))
        }
    }
}
