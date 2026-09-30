package com.example.lumeocrtest.ocr

enum class MetadataOrigin {
    EXPLICIT_LABEL,
    DOMAIN,
    HEADER_CANDIDATE,
    INFERRED_NEAR_DATE,
    /** Linha de assinatura da matéria ("Da Agência X | data", "Por Nome, Veículo"). */
    BYLINE,
}

data class ArticleMetadata(
    val source: String? = null,
    val sourceOrigin: MetadataOrigin? = null,
    val author: String? = null,
    val publishedAt: String? = null,
    val url: String? = null,
    val authorEvidence: String? = null,
    val dateEvidence: String? = null,
    /** Organização que assina a matéria sem autor individual ("Da Agência Senado"). */
    val institutionalByline: String? = null,
    /** Texto do OCR que sustenta o veículo. */
    val sourceEvidence: String? = null,
    val place: String? = null,
    val updated: String? = null,
    /** Créditos de foto/imagem: nunca são autoria da matéria. */
    val imageCredits: List<String> = emptyList(),
    val captions: List<String> = emptyList(),
    /** Data de publicação em ms (UTC, meia-noite) quando reconhecida com segurança. */
    val publishedAtMs: Long? = null,
)

data class MetadataExtractionResult(
    val metadata: ArticleMetadata,
    val consumedBlockIndexes: Set<Int>
)
