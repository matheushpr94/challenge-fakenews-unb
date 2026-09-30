package com.example.lumeocrtest.ocr

import java.text.BreakIterator
import java.util.Locale

class ClaimExtractor {
    fun extractClaims(text: String): ExtractedClaims {
        if (text.isBlank()) return ExtractedClaims(null, emptyList())

        val paragraphs = text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) return ExtractedClaims(null, emptyList())

        // The main claim is the first substantial paragraph. We might combine the first few if they are short (e.g. title + subtitle)
        var mainClaimText = paragraphs[0]
        var pIndex = 1
        // Manchetes jornalísticas frequentemente não têm ponto final. Isso não autoriza
        // concatenar o corpo da matéria (ou texto de outra coluna) à afirmação pesquisada.
        while (pIndex < paragraphs.size && mainClaimText.length < 40) {
            // Título sem pontuação final + subtítulo: mantém a fronteira da frase para a pesquisa avaliar cada uma.
            val sep = if (mainClaimText.trimEnd().matches(Regex(".*[.?!:;]$"))) " " else ". "
            mainClaimText += sep + paragraphs[pIndex]
            pIndex++
        }

        val otherClaimsList = mutableListOf<String>()
        val iterator = BreakIterator.getSentenceInstance(Locale.Builder().setLanguage("pt").setRegion("BR").build())

        for (i in pIndex until paragraphs.size) {
            val paragraph = paragraphs[i]
            iterator.setText(paragraph)
            
            var start = iterator.first()
            var end = iterator.next()
            
            while (end != BreakIterator.DONE) {
                val sentence = paragraph.substring(start, end).trim()
                if (sentence.isNotEmpty() && isPossibleClaim(sentence)) {
                    otherClaimsList.add(cleanSentence(sentence))
                }
                start = end
                end = iterator.next()
            }
        }

        return ExtractedClaims(
            mainClaim = if (isPossibleClaim(mainClaimText)) cleanSentence(mainClaimText) else null,
            otherClaims = otherClaimsList.distinct()
        )
    }

    private fun isPossibleClaim(sentence: String): Boolean {
        // Frase precisa ter um tamanho razoável
        if (sentence.length < 20) return false

        // Deve conter letras (não ser apenas números ou símbolos)
        if (!sentence.any { it.isLetter() }) return false

        val lower = sentence.lowercase()

        // Ignorar se for apenas URL
        if (lower.startsWith("http") || lower.startsWith("www.") || 
            (lower.contains(".com") && !lower.contains(" "))) return false
            
        // Ignorar textos muito curtos que parecem botões ou links soltos
        if (sentence.length < 40 && !sentence.contains(" ")) return false

        // Considerar como afirmação se tiver verbos, nomes, números (sinal de data/valor), 
        // ou pelo menos algumas palavras e terminar com pontuação
        val words = sentence.split("\\s+".toRegex())
        if (words.size < 4) return false
        
        // Verifica se termina em pontuação (afirmação completa) ou se parece um título de notícia
        if (sentence.matches(Regex(".*[.!?]$")) || words.size >= 5) {
            return true
        }

        return false
    }
    
    private fun cleanSentence(sentence: String): String {
        return sentence.replace("\\s+".toRegex(), " ").trim()
    }
}
