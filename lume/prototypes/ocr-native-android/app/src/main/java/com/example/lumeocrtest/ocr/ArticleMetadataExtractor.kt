package com.example.lumeocrtest.ocr

class ArticleMetadataExtractor {
    private val urlRegex = Regex("(?i)^(?:https?://)?(?:www\\.)?[a-z0-9.-]+\\.[a-z]{2,}(?:/\\S*)?$")
    private val sourceRegex = Regex("(?i)^(?:fonte|veículo)\\s*:\\s*(.+)$")
    private val authorRegex = Regex("(?i)^(?:conteúdo postado por|publicado por|escrito por|autoria|autor|por)\\s*:?\\s*(.*)$")
    private val dateRegex = Regex("(?iu)(?:publicad[oa]|publicação|atualizad[oa]|última atualização)(?:\\s+em)?\\s*:?\\s*(?=\\d)")
    private val dateLabelRegex = Regex("(?iu)^(?:publicad[oa]|publicação|atualizad[oa]|última atualização)(?:\\s+em)?\\s*:?$")
    private val categoryRegex = Regex("(?i)^(política|economia|brasília|mundo|esportes|entretenimento|notícias|geral)$")
    private val newsroomByline = Regex("""(?iu)^(?:da|do|de)\s+(.{4,55}?)\s+em\s+[\p{L}\s-]{3,40}$""")
    private val standaloneDate = Regex("(?iu)^\\d{1,2}\\s+(?:de\\s+)?(?:janeiro|fevereiro|março|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\\s+(?:de\\s+)?\\d{4}(?:[,\\s].*)?$")

    fun extractMetadata(blocks: List<OcrBlock>, allowedIndexes: Set<Int> = blocks.indices.toSet(),
                        headlineIndexes: List<Int> = emptyList()): MetadataExtractionResult {
        val texts = blocks.map { it.text.replace(Regex("\\s+"), " ").trim() }
        val consumed = mutableSetOf<Int>()
        val authorLabels = mutableListOf<Int>()
        var source: String? = null
        var origin: MetadataOrigin? = null
        var author: String? = null
        var publishedAt: String? = null
        var url: String? = null
        var authorEvidence: String? = null
        var dateEvidence: String? = null

        for (i in texts.indices) {
            if (i !in allowedIndexes) continue
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
            if (source == null && text.length <= 80) newsroomByline.matchEntire(text)?.let { match ->
                val newsroom = match.groupValues[1].trim()
                val previousIndex = allowedIndexes.filter { it != i && blocksNear(blocks[it], blocks[i]) &&
                    (blocks[it].boundingBox?.top ?: it) < (blocks[i].boundingBox?.top ?: i) }
                    .maxByOrNull { blocks[it].boundingBox?.top ?: it }
                val previous = previousIndex?.let { texts[it].trimEnd('>', '›', '»', '•', ' ').trim() }
                if (newsroom.split(Regex("\\s+")).size in 2..5 && newsroom.first().isUpperCase() &&
                    previous != null && isValidName(previous)) {
                    source = newsroom
                    origin = MetadataOrigin.EXPLICIT_LABEL
                    consumed.add(i)
                    if (author == null) {
                        author = previous
                        authorEvidence = "assinatura junto ao veículo"
                        previousIndex?.let(consumed::add)
                    }
                }
            }
            if (publishedAt == null && standaloneDate.matches(text) && (author != null || source != null)) {
                val previous = (i - 1 downTo maxOf(0, i - 3)).firstOrNull { it in consumed && blocksNear(blocks[it], blocks[i]) }
                if (previous != null) {
                    publishedAt = text
                    dateEvidence = "data junto à assinatura"
                    consumed.add(i)
                }
            }

            val dateMatch = dateRegex.find(text)?.takeIf { it.range.first < 60 && text.length <= 140 }
            if (dateMatch != null) {
                if (publishedAt == null) {
                    publishedAt = text.substring(dateMatch.range.last + 1).trim().trimStart(':').trim()
                    dateEvidence = "data indicada na página"
                }
                consumed.add(i)
                val beforeDate = text.substring(0, dateMatch.range.first).trim().trimEnd('·', '•', '|', '-', ':').trim()
                if (author == null && isValidName(beforeDate)) {
                    author = beforeDate
                    authorEvidence = "assinatura junto à data"
                }
            } else if (dateLabelRegex.matches(text) && i + 1 in allowedIndexes &&
                texts[i + 1].firstOrNull()?.isDigit() == true) {
                if (publishedAt == null) { publishedAt = texts[i + 1]; dateEvidence = "rótulo de publicação" }
                consumed.addAll(listOf(i, i + 1))
            }

            authorRegex.matchEntire(text)?.let {
                val candidate = beforeDate(it.groupValues[1])
                if (isValidName(candidate, explicit = true)) {
                    author = candidate
                    authorEvidence = "rótulo de autoria"
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
                    it in allowedIndexes && isValidName(beforeDate(texts[it])) && blocksNear(blocks[label], blocks[it])
                }
                if (index != null) {
                    author = beforeDate(texts[index])
                    authorEvidence = "rótulo de autoria"
                    consumed.add(index)
                    break
                }
            }
        }
        if (source == null && headlineIndexes.isNotEmpty()) {
            val headlineTop = headlineIndexes.mapNotNull { blocks[it].boundingBox?.top }.minOrNull()
            val masthead = allowedIndexes.filter { i ->
                val b = blocks[i]
                val t = texts[i]
                val beforeTitle = headlineTop != null && b.boundingBox != null && b.boundingBox.top < headlineTop
                val mediaWord = Regex("(?iu)^(?:jornal|portal|agência|radio|rádio|tv|rede|news)\\b").containsMatchIn(t)
                val acronym = t.matches(Regex("[A-Z0-9]{2,6}")) && t !in setOf("MENU", "HOME")
                beforeTitle && t.length in 3..35 && t.split(Regex("\\s+")).size <= 4 &&
                    !categoryRegex.matches(t) && (mediaWord || acronym)
            }.minByOrNull { blocks[it].boundingBox?.top ?: Int.MAX_VALUE }
            if (masthead != null) {
                source = texts[masthead]
                origin = MetadataOrigin.HEADER_CANDIDATE
                consumed.add(masthead)
            }
        }
        return MetadataExtractionResult(ArticleMetadata(source, origin, author, publishedAt, url,
            authorEvidence, dateEvidence), consumed)
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
        val properWords = words.filter { it.lowercase() !in setOf("da", "de", "do", "das", "dos", "e") }
        return properWords.isNotEmpty() && properWords.all { it.first().isUpperCase() }
    }
}
