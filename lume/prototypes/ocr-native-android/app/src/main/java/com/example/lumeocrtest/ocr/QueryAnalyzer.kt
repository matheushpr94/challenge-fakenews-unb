package com.example.lumeocrtest.ocr

data class AnalyzedQuery(
    val original: String,
    val mainEntities: List<String>,
    val actionOrContext: List<String>,
    val searchTerms: List<String>,
    val numbers: List<String>,
    val isAmbiguous: Boolean,
    val ambiguityReason: String?,
    val negations: List<String>,
    val searchQueries: List<String>
)

class QueryAnalyzer {
    private val stopWords = setOf(
        "o", "a", "os", "as", "um", "uma", "uns", "umas", 
        "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas", 
        "por", "para", "com", "que", "se", "é", "são", "foi", "foram", 
        "vai", "vão", "como", "mas", "ou", "e", "sobre", "sua", "seu", 
        "suas", "seus", "ao", "aos", "à", "às", "pelo", "pela", "pelos", "pelas"
    )

    private val commonNouns = setOf(
        "mensagens", "diálogos", "notícias", "matéria", "revela", "revelam", 
        "mostra", "mostram", "segundo", "tentativa", "governo", "justiça", 
        "mídia", "vídeo", "foto", "imagem", "post", "notícia", "reportagem"
    )

    private val negationWords = setOf("não", "nunca", "jamais", "ninguém", "nada", "tampouco")

    fun analyze(text: String): AnalyzedQuery {
        val cleanText = text.replace(Regex("[^a-zA-Z0-9áéíóúâêôãõçÁÉÍÓÚÂÊÔÃÕÇ ]"), " ").trim()
        val words = cleanText.split(Regex("\\s+")).filter { it.isNotEmpty() }
        
        val compoundEntities = extractCompoundEntities(words)
        val contextWords = mutableListOf<String>()
        val numbers = mutableListOf<String>()
        val negations = mutableListOf<String>()
        
        for (word in words) {
            val lower = word.lowercase()
            if (negationWords.contains(lower)) {
                negations.add(lower)
                continue
            }
            if (word.matches(Regex("\\d+"))) {
                numbers.add(word)
                continue
            }
            if (!stopWords.contains(lower) && !compoundEntities.any { it.lowercase().contains(lower) }) {
                contextWords.add(lower)
            }
        }
        
        val searchTerms = words.filter { !stopWords.contains(it.lowercase()) }
        
        val isSingleWord = searchTerms.size <= 1
        
        val personalEventVerbs = setOf("morreu", "faleceu", "casou", "preso", "presa", "matou", "nasceu", "doente", "internado", "internada", "separou")
        val hasPersonalEvent = contextWords.any { personalEventVerbs.contains(it) }
        
        val isShortPersonalEvent = compoundEntities.size <= 1 && hasPersonalEvent && searchTerms.size <= 3
        val isExtremelyShortGeneric = searchTerms.size == 2 && compoundEntities.isEmpty() && numbers.isEmpty()

        val isAmbiguous = isSingleWord || isShortPersonalEvent || isExtremelyShortGeneric
        val reason = if (isSingleWord) {
            "A busca é muito curta. Adicione mais contexto."
        } else if (isShortPersonalEvent) {
            "A busca parece referir-se a uma pessoa de forma genérica (ex: apenas um nome). Adicione o sobrenome ou mais detalhes."
        } else if (isExtremelyShortGeneric) {
            "A afirmação é muito curta e genérica, o que pode trazer resultados não relacionados. Adicione mais contexto."
        } else null
        
        val queries = mutableListOf<String>()
        val specificQuery = searchTerms.take(8).joinToString(" ")
        queries.add(specificQuery)
        
        if (searchTerms.size > 4) {
            val entitiesStr = compoundEntities.joinToString(" ")
            val verbsOrActions = contextWords.filter { it.length > 3 }.take(3).joinToString(" ")
            val lessRestrictive = "$entitiesStr $verbsOrActions".trim()
            if (lessRestrictive.isNotBlank() && lessRestrictive != specificQuery) {
                queries.add(lessRestrictive)
            }
        }
        
        if (compoundEntities.isNotEmpty() && searchTerms.size > 2) {
            val justEntities = compoundEntities.joinToString(" ")
            if (justEntities.isNotBlank() && justEntities != specificQuery && !queries.contains(justEntities)) {
                queries.add(justEntities)
            }
        }

        return AnalyzedQuery(
            original = text,
            mainEntities = compoundEntities,
            actionOrContext = contextWords,
            searchTerms = searchTerms,
            numbers = numbers,
            isAmbiguous = isAmbiguous,
            ambiguityReason = reason,
            negations = negations,
            searchQueries = queries.distinct()
        )
    }

    private val nameParticles = setOf("de", "da", "do", "das", "dos")

    private fun extractCompoundEntities(words: List<String>): List<String> {
        val entities = mutableListOf<String>()
        val currentEntity = mutableListOf<String>()

        for (i in words.indices) {
            val word = words[i]
            val lower = word.lowercase()

            val isCapitalized = word.first().isUpperCase() && word.length > 2
            val isFirstWord = (i == 0)
            val nextWordCapitalized = words.getOrNull(i + 1)?.first()?.isUpperCase() == true

            val belongsToName = if (isFirstWord) nextWordCapitalized && !commonNouns.contains(lower) else isCapitalized

            if (belongsToName && !stopWords.contains(lower) && !commonNouns.contains(lower)) {
                currentEntity.add(word)
            } else if (nameParticles.contains(lower) && currentEntity.isNotEmpty() && nextWordCapitalized) {
                currentEntity.add(word)
            } else {
                if (currentEntity.isNotEmpty()) {
                    val fullEntity = currentEntity.joinToString(" ").trim()
                    if (currentEntity.size >= 2) {
                        entities.add(fullEntity)
                    } else if (i > 0 && currentEntity[0].length >= 4 && !commonNouns.contains(currentEntity[0].lowercase())) {
                        entities.add(fullEntity)
                    }
                    currentEntity.clear()
                }
            }
        }
        if (currentEntity.isNotEmpty()) {
            val fullEntity = currentEntity.joinToString(" ").trim()
            if (currentEntity.size >= 2) {
                entities.add(fullEntity)
            } else if (currentEntity[0].length >= 4 && !commonNouns.contains(currentEntity[0].lowercase())) {
                entities.add(fullEntity)
            }
        }
        return entities.distinct()
    }
}
