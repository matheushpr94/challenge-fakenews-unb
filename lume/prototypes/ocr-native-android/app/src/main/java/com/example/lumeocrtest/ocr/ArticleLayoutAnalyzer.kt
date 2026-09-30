package com.example.lumeocrtest.ocr

import kotlin.math.abs

/** Geometry-based reading region. Unknown fields remain absent instead of being guessed. */
data class ArticleLayout(
    val mainIndexes: Set<Int>,
    val headlineIndexes: List<Int>,
    val title: String?,
    val subtitle: String?,
    val metadataIndexes: Set<Int> = mainIndexes,
)

class ArticleLayoutAnalyzer {
    fun analyze(blocks: List<OcrBlock>, width: Int, height: Int): ArticleLayout {
        if (blocks.isEmpty()) return ArticleLayout(emptySet(), emptyList(), null, null)
        val positioned = blocks.withIndex().filter { it.value.boundingBox != null }
        if (width <= 0 || height <= 0 || positioned.size < 2) {
            return ArticleLayout(blocks.indices.toSet(), emptyList(), null, null)
        }
        val medianLine = positioned.map { lineHeight(it.value) }.sorted().let { it[it.size / 2] }
        val candidates = positioned.filter { (_, b) ->
            val r = b.boundingBox!!
            r.top < height * 0.48 && r.right - r.left >= width * 0.25 &&
                b.text.trim().length >= 18 && b.text.split(Regex("\\s+")).size >= 4 &&
                !isChrome(b.text) && !looksLikeMetadata(b.text)
        }
        val anchor = candidates.maxByOrNull { (_, b) ->
            val r = b.boundingBox!!
            val size = (lineHeight(b) / medianLine.coerceAtLeast(1.0)).coerceAtMost(4.0)
            val supportingParagraphs = positioned.count { (_, below) ->
                val bb = below.boundingBox!!
                bb.top > r.bottom &&
                    inColumn(bb, r, width) && below.text.length >= 65 &&
                    lineHeight(below) < lineHeight(b) * 0.85
            }.coerceAtMost(3)
            size * 3 + supportingParagraphs * 2 + (r.right - r.left).toDouble() / width - r.top.toDouble() / height
        } ?: positioned.filter { (_, b) ->
            b.boundingBox!!.top < height * 0.5 && b.text.length >= 30 && !isChrome(b.text) && !looksLikeMetadata(b.text)
        }.maxByOrNull { (_, b) -> b.text.length } ?: return ArticleLayout(blocks.indices.toSet(), emptyList(), null, null)

        val anchorBox = anchor.value.boundingBox!!
        val fullWidth = anchorBox.right - anchorBox.left >= width * 0.72
        val main = positioned.filter { (_, b) ->
            val r = b.boundingBox!!
            fullWidth || inColumn(r, anchorBox, width)
        }.map { it.index }.toSet() + blocks.indices.filter { blocks[it].boundingBox == null }

        val titleIndexes = mutableListOf(anchor.index)
        var previous = anchor.value
        repeat(3) {
            val prev = previous.boundingBox!!
            val next = positioned.filter { (i, b) ->
                val r = b.boundingBox!!
                i in main && i !in titleIndexes && r.top >= prev.bottom &&
                    r.top - prev.bottom <= height * 0.035 &&
                    abs(r.left - anchorBox.left) <= width * 0.055 &&
                    lineHeight(b) >= lineHeight(anchor.value) * 0.78 &&
                    b.text.length < 180 && !looksLikeMetadata(b.text)
            }.minByOrNull { it.value.boundingBox!!.top } ?: return@repeat
            titleIndexes += next.index
            previous = next.value
        }
        val title = titleIndexes.sortedBy { blocks[it].boundingBox!!.top }
            .joinToString(" ") { blocks[it].text.replace(Regex("\\s+"), " ").trim() }
        val titleBottom = titleIndexes.maxOf { blocks[it].boundingBox!!.bottom }
        val subtitle = positioned.filter { (i, b) ->
            val r = b.boundingBox!!
            i in main && i !in titleIndexes && r.top >= titleBottom &&
                r.top - titleBottom <= height * 0.065 && b.text.length >= 25 &&
                lineHeight(b) >= medianLine * 0.85 && lineHeight(b) < lineHeight(anchor.value) * 0.8 &&
                !looksLikeMetadata(b.text)
        }.minByOrNull { it.value.boundingBox!!.top }?.value?.text?.replace(Regex("\\s+"), " ")?.trim()
        val firstBody = positioned.filter { (i, b) ->
            val r = b.boundingBox!!
            i in main && r.top > titleBottom && b.text.length >= 70 && b.lines.size >= 2 &&
                lineHeight(b) < lineHeight(anchor.value) * 0.8
        }.minOfOrNull { it.value.boundingBox!!.top }
        val metadataIndexes = if (firstBody == null) main else main.filter { i ->
            blocks[i].boundingBox?.top?.let { it < firstBody } ?: true
        }.toSet()
        return ArticleLayout(main, titleIndexes, title, subtitle, metadataIndexes)
    }

    private fun inColumn(r: OcrRect, title: OcrRect, width: Int): Boolean {
        val pad = width * 0.035
        val overlap = (minOf(r.right, title.right + pad.toInt()) - maxOf(r.left, title.left - pad.toInt())).coerceAtLeast(0)
        return overlap >= (r.right - r.left) * 0.55 ||
            (abs(r.left - title.left) <= width * 0.055 && r.left < title.right)
    }

    private fun lineHeight(b: OcrBlock): Double {
        val r = b.boundingBox ?: return 0.0
        return (r.bottom - r.top).toDouble() / maxOf(1, b.lines.size)
    }

    private fun isChrome(text: String): Boolean = Regex(
        "(?iu)^(?:leia mais|principais notícias|mais lidas|compartilhe|assine|entrar|buscar|menu|publicidade|anúncio)(?:\\b|$)"
    ).containsMatchIn(text.trim())

    private fun looksLikeMetadata(text: String): Boolean = Regex(
        "(?iu)^(?:por(?!\\s+que\\b)|autor|autoria|fonte|veículo|publicad[oa]|atualizad[oa]|\\d{1,2}\\s+(?:de\\s+)?(?:janeiro|fevereiro|março|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro))\\b"
    ).containsMatchIn(text.trim())
}
