package io.vedra.infrastructure.database.project

import io.vedra.domain.model.ProjectVisibility
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataTenantProjectRepository : JpaRepository<TenantProject, UUID> {
    fun findByTenantId(tenantId: UUID): List<TenantProject>

    fun findByTenantIdAndVisibility(
        tenantId: UUID,
        visibility: ProjectVisibility,
    ): TenantProject?

    fun findByTenantIdAndId(
        tenantId: UUID,
        id: UUID,
    ): TenantProject?

    fun findByTenantIdAndName(
        tenantId: UUID,
        name: String,
    ): TenantProject?
}
