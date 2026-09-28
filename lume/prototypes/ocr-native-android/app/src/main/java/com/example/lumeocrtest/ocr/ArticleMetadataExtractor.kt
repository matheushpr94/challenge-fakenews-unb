package com.example.lumeocrtest.ocr

class ArticleMetadataExtractor {
    private val urlRegex = Regex("(?i)^(?:https?://)?(?:www\\.)?[a-z0-9.-]+\\.[a-z]{2,}(?:/\\S*)?$")
    private val sourceRegex = Regex("(?i)^(?:fonte|veículo)\\s*:\\s*(.+)$")
    private val authorRegex = Regex("(?i)^(?:conteúdo postado por|publicado por|escrito por|autoria|autor|por)\\s*:?\\s*(.*)$")
    private val dateRegex = Regex("(?iu)(?:publicad[oa]|publicação|atualizad[oa]|última atualização)(?:\\s+em)?\\s*:?\\s*(?=\\d)")
    private val dateLabelRegex = Regex("(?iu)^(?:publicad[oa]|publicação|atualizad[oa]|última atualização)(?:\\s+em)?\\s*:?$")
    private val categoryRegex = Regex("(?i)^(política|economia|brasília|mundo|esportes|entretenimento|notícias|geral)$")

    fun extractMetadata(blocks: List<OcrBlock>): MetadataExtractionResult {
        val texts = blocks.map { it.text.replace(Regex("\\s+"), " ").trim() }
        val consumed = mutableSetOf<Int>()
        val authorLabels = mutableListOf<Int>()
        val dateIndexes = mutableListOf<Int>()
        var source: String? = null
        var origin: MetadataOrigin? = null
        var author: String? = null
        var publishedAt: String? = null
        var url: String? = null

        for (i in texts.indices) {
            val text = texts[i]
            if (urlRegex.matches(text)) {
                url = text
                if (source == null) {
                    source = text.removePrefix("https://").removePrefix("http://").substringBefore('/')
                    origin = MetadataOrigin.DOMAIN
                }
                consumed.add(i)
            }
            sourceRegex.matchEntire(text)?.let {
                source = it.groupValues[1].trim()
                origin = MetadataOrigin.EXPLICIT_LABEL
                consumed.add(i)
            }

            val dateMatch = dateRegex.find(text)
            if (dateMatch != null) {
                publishedAt = text.substring(dateMatch.range.last + 1).trim().trimStart(':').trim()
                dateIndexes.add(i)
                consumed.add(i)
                val beforeDate = text.substring(0, dateMatch.range.first).trim().trimEnd('·', '•', '|', '-', ':').trim()
                if (author == null && isValidName(beforeDate)) author = beforeDate
            } else if (dateLabelRegex.matches(text) && i + 1 < texts.size && texts[i + 1].firstOrNull()?.isDigit() == true) {
                publishedAt = texts[i + 1]
                dateIndexes.add(i + 1)
                consumed.addAll(listOf(i, i + 1))
            }

            authorRegex.matchEntire(text)?.let {
                val candidate = beforeDate(it.groupValues[1])
                if (isValidName(candidate, explicit = true)) {
                    author = candidate
                    consumed.add(i)
                } else if (candidate.isEmpty()) {
                    authorLabels.add(i)
                    consumed.add(i)
                }
            }
        }

        // OCR can read a promotion between the label and the real byline.
        if (author == null) {
            for (label in authorLabels) {
                val index = ((label + 1)..minOf(label + 5, texts.lastIndex)).firstOrNull {
                    isValidName(beforeDate(texts[it])) && blocksNear(blocks[label], blocks[it])
                }
                if (index != null) {
                    author = beforeDate(texts[index])
                    consumed.add(index)
                    break
                }
            }
        }
        if (author == null) {
            for (index in dateIndexes) {
                val candidate = beforeDate(texts[index])
                if (isValidName(candidate)) {
                    author = candidate
                    consumed.add(index)
                    break
                }
                val previous = (index - 1 downTo maxOf(index - 2, 0)).firstOrNull {
                    isValidName(texts[it]) && blocksNear(blocks[it], blocks[index])
                }
                if (previous != null) {
                    author = texts[previous]
                    consumed.add(previous)
                    break
                }
            }
        }
        return MetadataExtractionResult(ArticleMetadata(source, origin, author, publishedAt, url), consumed)
    }

    private fun beforeDate(text: String) = text.substringBefore(dateRegex.find(text)?.value ?: "\u0000")
        .trim().trimEnd('·', '•', '|', '-', ':').trim()

    private fun blocksNear(first: OcrBlock, second: OcrBlock): Boolean {
        val a = first.boundingBox ?: return true
        val b = second.boundingBox ?: return true
        return b.top >= a.top - 30 && b.top - a.bottom < 180
    }

    private fun isValidName(text: String, explicit: Boolean = false): Boolean {
        if (text.length !in 3..70 || text.any { it.isDigit() } || categoryRegex.matches(text)) return false
        if (Regex("(?i)\\b(apoie|assine|inscreva|compartilhe|clique|publicado|atualizado|conteúdo)\\b").containsMatchIn(text)) return false
        val words = text.split(Regex("\\s+"))
        if (words.size !in 2..5 && !(explicit && words.size == 1)) return false
        if (!words.all { word -> word.all { it.isLetter() || it == '-' || it == '\'' } }) return false
        return explicit || words.count { it.first().isUpperCase() } >= 2
    }
}
