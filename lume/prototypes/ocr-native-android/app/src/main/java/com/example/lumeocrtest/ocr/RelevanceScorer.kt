package com.example.lumeocrtest.ocr

enum class RelevanceLevel {
    DIRECT, CONTEXT, UNRELATED
}

data class ScoredResult(
    val isRelevant: Boolean,
    val level: RelevanceLevel,
    val relationText: String
)

class RelevanceScorer {
    private fun getStem(word: String): String {
        var clean = word.lowercase()
        if (clean.length > 3 && (clean.endsWith("os") || clean.endsWith("as") || clean.endsWith("es"))) {
            clean = clean.substring(0, clean.length - 2)
        }
        if (clean.length > 3 && (clean.endsWith("o") || clean.endsWith("a") || clean.endsWith("e") || clean.endsWith("s"))) {
            clean = clean.substring(0, clean.length - 1)
        }
        return clean
    }

    fun score(query: AnalyzedQuery, title: String, snippet: String, isWiki: Boolean = false): ScoredResult {
        val queryTermsLower = query.searchTerms.map { it.lowercase() }
        val titleLower = title.lowercase()
        val snippetLower = snippet.lowercase()
        val combined = "$titleLower $snippetLower"
        
        if (query.searchTerms.isEmpty()) return ScoredResult(false, RelevanceLevel.UNRELATED, "")

        // Subscription and publication identity pages do not describe an event.
        val publicationTitle = Regex("(?i)^(jornal|revista|portal)\\b")
        val subscriptionText = Regex("(?i)\\b(assine|assinatura|receba as|cadastre-se)\\b")
        if (publicationTitle.containsMatchIn(title) && subscriptionText.containsMatchIn(snippet) &&
            queryTermsLower.none { it in setOf("assinatura", "assinar", "jornal", "revista", "portal") }) {
            return ScoredResult(false, RelevanceLevel.UNRELATED, "")
        }

        // Check Entities (crucial)
        var entityMatches = 0
        for (entity in query.mainEntities) {
            if (combined.contains(entity.lowercase())) {
                entityMatches++
            }
        }
        
        val hasCoreEntities = if (query.mainEntities.isNotEmpty()) {
            entityMatches.toFloat() / query.mainEntities.size >= 0.5f
        } else true
        
        if (query.mainEntities.isNotEmpty() && entityMatches == 0) {
            return ScoredResult(false, RelevanceLevel.UNRELATED, "")
        }

        // Action/Context match (using stem)
        var actionMatches = 0
        for (ctx in query.actionOrContext) {
            val stem = getStem(ctx)
            if (combined.contains(stem)) actionMatches++
        }
        
        // Numbers match
        var hasAllNumbers = true
        if (query.numbers.isNotEmpty()) {
            hasAllNumbers = query.numbers.all { combined.contains(it) }
        }
        
        // Negations
        var hasAllNegations = true
        if (query.negations.isNotEmpty()) {
            hasAllNegations = query.negations.all { combined.contains(it.lowercase()) }
        }

        val totalImportantTerms = query.mainEntities.size + query.actionOrContext.size
        val matchedImportantTerms = entityMatches + actionMatches
        val matchRatio = if (totalImportantTerms > 0) matchedImportantTerms.toFloat() / totalImportantTerms else 1f

        // Se a consulta é muito curta e não combina com tudo
        if (totalImportantTerms <= 2 && matchRatio < 1.0f && query.searchTerms.size <= 2) {
            return ScoredResult(false, RelevanceLevel.UNRELATED, "")
        }
        
        // Contexto mínimo exigido
        if (matchRatio < 0.3f) {
            return ScoredResult(false, RelevanceLevel.UNRELATED, "")
        }

        if (isWiki) {
            val titleHasTerm = query.searchTerms.any { titleLower.contains(it.lowercase()) }
            if (!titleHasTerm) return ScoredResult(false, RelevanceLevel.UNRELATED, "")
        }
        
        // Evitar falsos positivos como "O sol é frio" batendo em "Cabo Frio" "Pôr do sol"
        // Para consultas curtas, exigir que os termos apareçam próximos
        if (query.searchTerms.size <= 3) {
            var minDistance = Int.MAX_VALUE
            var hasPairMatch = false
            for (i in 0 until queryTermsLower.size) {
                for (j in i + 1 until queryTermsLower.size) {
                    val pos1 = combined.indexOf(queryTermsLower[i])
                    val pos2 = combined.indexOf(queryTermsLower[j])
                    if (pos1 != -1 && pos2 != -1) {
                        hasPairMatch = true
                        val dist = Math.abs(pos1 - pos2)
                        if (dist < minDistance) minDistance = dist
                    }
                }
            }
            if (hasPairMatch && minDistance > 250) return ScoredResult(false, RelevanceLevel.UNRELATED, "")
        }
        
        // Regra para evitar que modificadores de clima/tempo (ex: "manhãs frias", "dias frios", "frente fria", "Cabo Frio")
        // sejam confundidos com afirmações científicas sobre a temperatura de um objeto/astro.
        val weatherNouns = Regex("(?i)\\b(manhã|manhãs|dia|dias|noite|noites|tempo|clima|frente|onda|cabo)\\s+(frio|fria|frios|frias)\\b|\\b(frio|fria|frios|frias)\\s+de\\s+(sol|verão|inverno)\\b")
        if (queryTermsLower.any { it.startsWith("fri") } && weatherNouns.containsMatchIn(combined) && !queryTermsLower.contains("clima") && !queryTermsLower.contains("previsão")) {
            return ScoredResult(false, RelevanceLevel.UNRELATED, "")
        }

        if (hasCoreEntities && actionMatches > 0 && hasAllNumbers && hasAllNegations && matchRatio >= 0.8f) {
            return ScoredResult(true, RelevanceLevel.DIRECT, "Possivelmente relacionado ao fato; comparação por palavras.")
        }
        
        val missingText = mutableListOf<String>()
        if (query.actionOrContext.isNotEmpty() && actionMatches == 0) missingText.add("o acontecimento")
        if (!hasAllNumbers) missingText.add("dados ou datas")
        if (!hasAllNegations) missingText.add("a negação")
        
        val relationText = if (missingText.isNotEmpty()) {
            "Oferece contexto, mas não detalha ${missingText.joinToString(", ")}."
        } else {
            "Oferece contexto relacionado."
        }
        
        return ScoredResult(true, RelevanceLevel.CONTEXT, relationText)
    }
    
    // Maintain old method signature for compatibility during refactoring
    fun isRelevant(query: AnalyzedQuery, title: String, snippet: String, isWiki: Boolean = false): Boolean {
        return score(query, title, snippet, isWiki).isRelevant
    }
}
