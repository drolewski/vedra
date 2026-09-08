package io.vedra.domain.model

import java.time.Instant
import java.util.UUID

data class TenantProject(
    val id: UUID? = null,
    val tenantId: UUID,
    val name: String,
    val description: String? = null,
    val industry: String? = null,
    val visibility: ProjectVisibility = ProjectVisibility.PRIVATE,
    val createdAt: Instant = Instant.now(),
)
