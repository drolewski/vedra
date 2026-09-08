package io.vedra.domain.model

data class MessageUsefulnessResult(
    val useful: Boolean,
    val summary: String,
    val reason: String,
)
