package io.vedra.domain.model

import java.util.UUID

data class NormalizedQuestion(
    val original: String,
    val normalized: String,
    val hash: String,
    val projectId: UUID?,
)
