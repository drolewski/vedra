package io.vedra.domain.model

import java.time.Instant
import java.util.UUID

data class TenantProject(
    val id: UUID? = null,
    val tenantId: UUID,
    val name: String,
    val description: String? = null,
    val industry: String? = null,
    val isGeneral: Boolean = false,
    val createdAt: Instant = Instant.now(),
)
