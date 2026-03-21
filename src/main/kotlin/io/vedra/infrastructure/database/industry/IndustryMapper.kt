package io.vedra.infrastructure.database.industry

import io.vedra.domain.model.Industry as DomainIndustry

fun Industry.toDomain(): DomainIndustry =
    DomainIndustry(
        id = this.id,
        tenantId = this.tenantId,
        name = this.name,
        createdAt = this.createdAt,
    )

fun DomainIndustry.toEntity(): Industry =
    Industry(
        id = this.id,
        tenantId = this.tenantId,
        name = this.name,
    )
