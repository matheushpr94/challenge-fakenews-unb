package com.example.lumeocrtest.ocr

data class ContextResult(
    val title: String,
    val snippet: String,
    val url: String,
    val relationText: String? = null
)

data class NewsResult(
    val title: String,
    val source: String,
    val date: String,
    val url: String,
    val relationText: String? = null
)
