package com.example.lumeocrtest.ocr

data class OcrRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Linha do OCR com posição e confiança (quando o reconhecedor informa). */
data class OcrLine(val text: String, val box: OcrRect?, val confidence: Float? = null)

data class OcrBlock(
    val text: String,
    val boundingBox: OcrRect?,
    val lines: List<String>,
    /** Geometria por linha; vazio quando a origem só fornece o texto (testes antigos, texto colado). */
    val lineDetails: List<OcrLine> = emptyList(),
)
