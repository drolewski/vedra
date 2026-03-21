package io.vedra.infrastructure.database.project

import io.vedra.domain.model.TenantProject as DomainTenantProject

fun TenantProject.toDomain(): DomainTenantProject =
    DomainTenantProject(
        id = this.id,
        tenantId = this.tenantId,
        name = this.name,
        description = this.description,
        industry = this.industry,
        isGeneral = this.isGeneral,
        createdAt = this.createdAt,
    )

fun DomainTenantProject.toEntity(): TenantProject =
    TenantProject(
        id = this.id,
        tenantId = this.tenantId,
        name = this.name,
        description = this.description,
        industry = this.industry,
        isGeneral = this.isGeneral,
    )
