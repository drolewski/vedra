package io.vedra.infrastructure.database.industry

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataIndustryRepository : JpaRepository<Industry, UUID> {
    fun findByTenantId(tenantId: UUID): List<Industry>

    fun findByTenantIdAndName(
        tenantId: UUID,
        name: String,
    ): Industry?
}
