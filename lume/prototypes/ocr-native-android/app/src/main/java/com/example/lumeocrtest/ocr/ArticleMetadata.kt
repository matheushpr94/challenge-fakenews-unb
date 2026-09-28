package com.example.lumeocrtest.ocr

enum class MetadataOrigin {
    EXPLICIT_LABEL,
    DOMAIN,
    HEADER_CANDIDATE,
    INFERRED_NEAR_DATE
}

data class ArticleMetadata(
    val source: String? = null,
    val sourceOrigin: MetadataOrigin? = null,
    val author: String? = null,
    val publishedAt: String? = null,
    val url: String? = null
)

data class MetadataExtractionResult(
    val metadata: ArticleMetadata,
    val consumedBlockIndexes: Set<Int>
)
