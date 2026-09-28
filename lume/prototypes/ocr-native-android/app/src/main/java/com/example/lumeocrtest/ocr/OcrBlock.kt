package com.example.lumeocrtest.ocr

data class OcrRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class OcrBlock(
    val text: String,
    val boundingBox: OcrRect?,
    val lines: List<String>
)