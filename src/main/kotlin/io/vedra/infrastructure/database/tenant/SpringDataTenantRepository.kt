package io.vedra.infrastructure.database.tenant

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataTenantRepository : JpaRepository<Tenant, UUID> {
    fun findByExternalId(externalId: String): Tenant?

    fun findByActiveTrue(): List<Tenant>
}
