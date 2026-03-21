package io.vedra.infrastructure.database.project

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataTenantProjectRepository : JpaRepository<TenantProject, UUID> {
    fun findByTenantId(tenantId: UUID): List<TenantProject>

    fun findByTenantIdAndIsGeneralTrue(tenantId: UUID): TenantProject?

    fun findByTenantIdAndName(
        tenantId: UUID,
        name: String,
    ): TenantProject?
}
