package app.lume.mvp

// Fictional fixtures. These weights are a product hypothesis, not a calibrated truth probability.
data class DemoCriterion(val title: String, val weight: Int, val level: Int, val reason: String) {
    val points: Int get() = weight * level / 2
}

data class DemoScenario(
    val name: String, val title: String, val excerpt: String, val claim: String,
    val explanation: String, val nextStep: String, val sources: String,
    val criteria: List<DemoCriterion> = emptyList()
) {
    val score: Int? get() {
        if (criteria.size != 4 || criteria.sumOf { it.weight } != 100) return null
        if (criteria.any { it.weight <= 0 || it.level !in 0..2 || it.reason.isBlank() }) return null
        if (claim.isBlank() || criteria[0].level == 0 || criteria[1].level == 0) return null
        return criteria.sumOf { it.points }
    }
    val verdict: String get() = when (score) {
        null -> "Sem base para avaliar"
        in 80..100 -> "Evidências consistentes"
        in 50..79 -> "Há lacunas importantes"
        else -> "Base de evidências limitada"
    }
}

object DemoData {
    val scenarios = listOf(
        DemoScenario(
            name = "Notícia com lacunas",
            title = "Mais árvores, ruas mais frescas?",
            excerpt = "Um boletim relata queda de 2 °C em ruas que receberam árvores. A medição ocorreu em uma tarde; faltam detalhes sobre o clima e a comparação.",
            claim = "A temperatura caiu 2 °C nas ruas do teste após o plantio.",
            explanation = "Uma medição isolada não mostra se as árvores causaram a mudança. O cenário tem informações úteis, mas ainda deixa lacunas.",
            nextStep = "Comparar horários, condições do tempo e ruas com e sem plantio.",
            sources = "Este exemplo supõe um boletim do projeto. O documento é fictício; nenhuma fonte foi consultada.",
            criteria = listOf(
                DemoCriterion("Apoio à afirmação", 40, 1, "O boletim descreve a diferença, mas a comparação é incompleta."),
                DemoCriterion("Fonte original", 20, 2, "O cenário identifica o boletim original."),
                DemoCriterion("Confirmação independente", 20, 0, "Não há outro levantamento independente neste exemplo."),
                DemoCriterion("Data e contexto", 20, 1, "O local é conhecido; clima e período estão incompletos.")
            )
        ),
        DemoScenario(
            name = "Post sem fonte",
            title = "“Esse estudo muda tudo!”",
            excerpt = "Um post anuncia uma descoberta, mas não informa qual é a pesquisa, o resultado ou a fonte original.",
            claim = "O post não apresenta uma afirmação verificável suficientemente clara.",
            explanation = "Faltam informações para conferir a alegação. Isso não permite concluir se o post é verdadeiro ou falso.",
            nextStep = "Encontrar a pesquisa original e identificar exatamente qual resultado está sendo anunciado.",
            sources = "Nenhuma fonte é identificada neste cenário fictício. Sem material suficiente, não atribuímos um percentual."
        ),
        DemoScenario(
            name = "Notícia com evidências",
            title = "Mais sombra nas ruas do teste",
            excerpt = "O relatório registra queda de 2 °C, com dados, datas e método. Outra equipe encontra resultados compatíveis em parte dos trechos.",
            claim = "Nos trechos monitorados, a temperatura caiu 2 °C em condições comparáveis.",
            explanation = "Os dados deste cenário sustentam o resultado nos locais medidos. Ele não deve ser generalizado para toda a cidade.",
            nextStep = "Ampliar a confirmação independente e acompanhar outros períodos do ano.",
            sources = "O cenário supõe um relatório original e um segundo levantamento. Ambos são fictícios; nenhuma fonte foi consultada.",
            criteria = listOf(
                DemoCriterion("Apoio à afirmação", 40, 2, "Os dados sustentam a afirmação, limitada aos trechos medidos."),
                DemoCriterion("Fonte original", 20, 2, "O relatório original inclui dados e método."),
                DemoCriterion("Confirmação independente", 20, 1, "O segundo levantamento confirma apenas parte dos trechos."),
                DemoCriterion("Data e contexto", 20, 2, "Datas, condições e limites do resultado estão explícitos.")
            )
        )
    )
}
