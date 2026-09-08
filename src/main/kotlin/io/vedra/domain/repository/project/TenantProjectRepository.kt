package io.vedra.domain.repository.project

import io.vedra.domain.model.TenantProject
import java.util.UUID

interface TenantProjectRepository {
    fun findByTenantId(tenantId: UUID): List<TenantProject>

    fun findPublicByTenantId(tenantId: UUID): TenantProject?

    fun findByTenantIdAndId(
        tenantId: UUID,
        id: String,
    ): TenantProject?

    fun findByTenantIdAndName(
        tenantId: UUID,
        name: String,
    ): TenantProject?

    fun save(project: TenantProject): TenantProject
}
