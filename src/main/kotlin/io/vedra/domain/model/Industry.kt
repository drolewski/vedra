package io.vedra.domain.model

import java.time.Instant
import java.util.UUID

data class Industry(
    val id: UUID? = null,
    val tenantId: UUID,
    val name: String,
    val createdAt: Instant = Instant.now(),
)
