package io.vedra.infrastructure.database.tenant

import io.vedra.domain.model.Tenant as DomainTenant

fun Tenant.toDomain(): DomainTenant =
    DomainTenant(
        id = this.id,
        name = this.name,
        slackWorkspaceId = this.slackWorkspaceId,
        active = this.active,
        createdAt = this.createdAt,
    )

fun DomainTenant.toEntity(): Tenant =
    Tenant(
        id = this.id,
        name = this.name,
        slackWorkspaceId = this.slackWorkspaceId,
        active = this.active,
    )
