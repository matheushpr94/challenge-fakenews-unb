package com.example.lumeocrtest.ocr

import com.google.mlkit.vision.text.Text

/** Converte a saída do ML Kit mantendo a posição e a confiança de cada linha (usado pelo app e pelos testes). */
fun Text.toOcrBlocks(): List<OcrBlock> = textBlocks.map { block ->
    OcrBlock(
        text = block.text,
        boundingBox = block.boundingBox?.let { OcrRect(it.left, it.top, it.right, it.bottom) },
        lines = block.lines.map { it.text },
        lineDetails = block.lines.map { line ->
            OcrLine(line.text, line.boundingBox?.let { OcrRect(it.left, it.top, it.right, it.bottom) }, line.confidence)
        },
    )
}
