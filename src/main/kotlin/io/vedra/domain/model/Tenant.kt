package io.vedra.domain.model

import java.time.Instant
import java.util.UUID

data class Tenant(
    val id: UUID? = null,
    val name: String,
    val slackWorkspaceId: String? = null,
    val active: Boolean = true,
    val createdAt: Instant = Instant.now(),
)
