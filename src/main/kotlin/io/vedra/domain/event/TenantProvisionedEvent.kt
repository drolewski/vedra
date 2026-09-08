package io.vedra.domain.event

import java.util.UUID

data class TenantProvisionedEvent(
    val tenantId: UUID,
    val externalId: String,
    val workspaceName: String,
    val installerUserId: String?,
    val plan: String = "Free",
)
