package io.vedra.domain.repository.project

import io.vedra.infrastructure.database.project.TenantProject
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TenantProjectRepository : JpaRepository<TenantProject, UUID> {
    fun findByTenantId(tenantId: UUID): List<TenantProject>

    fun findByTenantIdAndIsGeneralTrue(tenantId: UUID): TenantProject?

    fun findByTenantIdAndName(
        tenantId: UUID,
        name: String,
    ): TenantProject?
}
