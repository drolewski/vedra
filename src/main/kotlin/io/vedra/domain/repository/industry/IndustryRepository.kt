package io.vedra.domain.repository.industry

import io.vedra.infrastructure.database.industry.Industry
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IndustryRepository : JpaRepository<Industry, UUID> {
    fun findByTenantId(tenantId: UUID): List<Industry>

    fun findByTenantIdAndName(
        tenantId: UUID,
        name: String,
    ): Industry?
}
