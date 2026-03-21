package io.vedra.domain.repository.industry

import io.vedra.domain.model.Industry
import java.util.UUID

interface IndustryRepository {
    fun findByTenantId(tenantId: UUID): List<Industry>

    fun findByTenantIdAndName(
        tenantId: UUID,
        name: String,
    ): Industry?

    fun findById(id: UUID): Industry?

    fun save(industry: Industry): Industry
}
