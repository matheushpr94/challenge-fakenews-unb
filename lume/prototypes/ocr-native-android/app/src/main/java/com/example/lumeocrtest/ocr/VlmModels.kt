package com.example.lumeocrtest.ocr

data class VlmConfidence(
    val platform_or_source: Float?,
    val author_or_account: Float?,
    val title: Float?,
    val main_content: Float?
)

data class VlmResponse(
    val content_type: String?,
    val platform_or_source: String?,
    val author_or_account: String?,
    val published_at: String?,
    val title: String?,
    val subtitle: String?,
    val main_content: String?,
    val claims: List<String>?,
    val discarded_text: List<String>?,
    val confidence: VlmConfidence?
)
