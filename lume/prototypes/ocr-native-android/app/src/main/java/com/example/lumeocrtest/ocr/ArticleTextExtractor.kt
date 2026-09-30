package com.example.lumeocrtest.ocr

class ArticleTextExtractor {
    companion object {
        private const val MIN_LINES_FOR_PARAGRAPH = 2
        private const val MIN_LENGTH_FOR_PARAGRAPH = 50
        
        private val NEGATIVE_KEYWORDS = setOf(
            "saiba mais", "compartilhar", "remover anúncio", "menu", "voltar", "início", "entrar",
            "assine", "login", "cadastre-se", "buscar", "pesquisar"
        )
        private val END_SECTION = Regex("(?iu)^(?:leia também|leia mais|mais lidas|mais notícias|notícias relacionadas|recomendadas|você também pode gostar|veja também)\\s*:?$")
    }

    fun extractArticle(blocks: List<OcrBlock>, imageWidth: Int, imageHeight: Int, consumedIndexes: Set<Int>,
                       layout: ArticleLayout = ArticleLayoutAnalyzer().analyze(blocks, imageWidth, imageHeight)): String {
        if (blocks.isEmpty()) return ""
        val firstTitleTop = layout.headlineIndexes.mapNotNull { blocks[it].boundingBox?.top }.minOrNull()
        val titleBottom = layout.headlineIndexes.mapNotNull { blocks[it].boundingBox?.bottom }.maxOrNull() ?: -1
        val orderedMain = blocks.withIndex().filter { (i, b) -> i in layout.mainIndexes && b.boundingBox != null }
            .sortedBy { it.value.boundingBox!!.top }
        val endY = orderedMain.firstOrNull { (_, b) ->
            val top = b.boundingBox!!.top
            top > titleBottom && END_SECTION.matches(b.text.trim()) &&
                orderedMain.any { (_, before) -> before.boundingBox!!.top in (titleBottom + 1) until top && before.text.length >= 50 }
        }?.value?.boundingBox?.top
        val candidateBlocks = blocks.withIndex().filter { (i, b) ->
            i in layout.mainIndexes && i !in consumedIndexes && i !in layout.headlineIndexes &&
                (firstTitleTop == null || b.boundingBox == null || b.boundingBox.top >= firstTitleTop) &&
                (endY == null || b.boundingBox == null || b.boundingBox.top < endY)
        }.map { it.value }
        val title = if (layout.title != null && layout.headlineIndexes.none { it in consumedIndexes }) {
            val rects = layout.headlineIndexes.mapNotNull { blocks[it].boundingBox }
            val box = OcrRect(rects.minOf { it.left }, rects.minOf { it.top },
                rects.maxOf { it.right }, rects.maxOf { it.bottom })
            OcrBlock(layout.title, box, listOf(layout.title))
        } else null
        val lineSizes = candidateBlocks.mapNotNull { b -> b.boundingBox?.let {
            (it.bottom - it.top).toDouble() / maxOf(1, b.lines.size)
        } }.sorted()
        val typicalLine = lineSizes.getOrNull(lineSizes.size / 2) ?: 0.0
        val validBlocks = candidateBlocks.filter { b ->
            val box = b.boundingBox
            val captionSize = box != null && typicalLine > 0 &&
                (box.bottom - box.top).toDouble() / maxOf(1, b.lines.size) < typicalLine * 0.68 && b.text.length < 160
            !captionSize && isValidArticleBlock(b, imageWidth, imageHeight)
        }

        val blocksToUse = (if (validBlocks.isEmpty()) {
            candidateBlocks.sortedWith(visualOrderComparator()).filter { it.text.length > 20 || it.lines.size > 1 }.ifEmpty { candidateBlocks }
        } else {
            validBlocks.sortedWith(visualOrderComparator())
        })

        return mergeBlocks(listOfNotNull(title) + blocksToUse)
    }

    /** Junta blocos já escolhidos e ordenados, unindo linhas quebradas e separando parágrafos. */
    fun mergeInOrder(blocks: List<OcrBlock>): String = mergeBlocks(blocks)

    private fun mergeBlocks(blocks: List<OcrBlock>): String {
        if (blocks.isEmpty()) return ""
        val builder = StringBuilder()
        
        var prevBlock: OcrBlock? = null
        
        for (block in blocks) {
            val currentText = if (block.lines.isEmpty()) block.text.trim() else mergeLinesInBlock(block.lines)
            
            if (prevBlock != null) {
                val prevText = builder.toString().trimEnd()
                val endsWithPunctuation = prevText.matches(Regex(".*[.?!:]$"))
                val startsWithLowerCase = currentText.isNotEmpty() && currentText.first().isLowerCase()
                
                val prevBox = prevBlock.boundingBox
                val currBox = block.boundingBox
                
                var isCloseAndAligned = false
                if (prevBox != null && currBox != null) {
                    val prevHeight = prevBox.bottom - prevBox.top
                    val currHeight = currBox.bottom - currBox.top
                    val verticalDistance = currBox.top - prevBox.bottom
                    val alignedLeft = Math.abs(currBox.left - prevBox.left) < 50
                    
                    val similarHeight = Math.abs(prevHeight - currHeight) < Math.max(prevHeight, currHeight) * 0.5
                    // Altura por linha ~ tamanho da fonte: manchete e corpo têm níveis tipográficos diferentes.
                    val prevLineHeight = prevHeight.toDouble() / Math.max(1, prevBlock.lines.size)
                    val currLineHeight = currHeight.toDouble() / Math.max(1, block.lines.size)
                    val sameTypeSize = Math.abs(prevLineHeight - currLineHeight) < Math.max(prevLineHeight, currLineHeight) * 0.25
                    
                    isCloseAndAligned = verticalDistance > -20 && verticalDistance < Math.max(prevHeight, 40) * 1.5 && alignedLeft && similarHeight && sameTypeSize
                }

                if (isCloseAndAligned && (!endsWithPunctuation || startsWithLowerCase)) {
                    builder.append(" ").append(currentText)
                } else {
                    builder.append("\n\n").append(currentText)
                }
            } else {
                builder.append(currentText)
            }
            prevBlock = block
        }
        
        return builder.toString().trim()
    }

    private fun mergeLinesInBlock(lines: List<String>): String {
        if (lines.isEmpty()) return ""
        val builder = StringBuilder()
        builder.append(lines.first().trim())
        
        for (i in 1 until lines.size) {
            val prev = lines[i - 1].trim()
            val curr = lines[i].trim()
            
            val endsWithPunctuation = prev.matches(Regex(".*[.?!:]$"))
            val startsWithLowerCase = curr.isNotEmpty() && curr.first().isLowerCase()
            
            if (!endsWithPunctuation || startsWithLowerCase) {
                builder.append(" ").append(curr)
            } else {
                builder.append("\n").append(curr)
            }
        }
        return builder.toString()
    }

    private fun isValidArticleBlock(block: OcrBlock, imageWidth: Int, imageHeight: Int): Boolean {
        val textLower = block.text.lowercase().trim()
        if (textLower.isEmpty()) return false

        if (block.text.length < 15 && block.lines.size == 1) return false
        
        if (textLower.startsWith("http") || textLower.startsWith("www.") || 
            (textLower.contains(".com") && !textLower.contains(" "))) {
            return false
        }
        
        if (block.text.length < 50 && NEGATIVE_KEYWORDS.any { textLower.contains(it) }) return false
        
        val box = block.boundingBox
        if (box != null && imageHeight > 0 && imageWidth > 0) {
            val isAtTop = box.top < imageHeight * 0.1
            val isAtBottom = box.bottom > imageHeight * 0.9
            val isSmall = (box.bottom - box.top) < imageHeight * 0.05
            if ((isAtTop || isAtBottom) && isSmall && block.text.length < 40) return false
        }

        if (block.lines.size >= MIN_LINES_FOR_PARAGRAPH) return true
        if (block.text.length >= MIN_LENGTH_FOR_PARAGRAPH) return true
        if (textLower.matches(Regex(".*[.?!]$"))) return true

        return false
    }

    private fun visualOrderComparator(): Comparator<OcrBlock> {
        return Comparator { b1, b2 ->
            val box1 = b1.boundingBox
            val box2 = b2.boundingBox
            
            if (box1 == null && box2 == null) return@Comparator 0
            if (box1 == null) return@Comparator -1
            if (box2 == null) return@Comparator 1

            if (Math.abs(box1.top - box2.top) < 20) {
                box1.left.compareTo(box2.left)
            } else {
                box1.top.compareTo(box2.top)
            }
        }
    }
}
