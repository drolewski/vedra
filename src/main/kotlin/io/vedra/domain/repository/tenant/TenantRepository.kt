package io.vedra.domain.repository.tenant

import io.vedra.infrastructure.database.tenant.Tenant
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TenantRepository : JpaRepository<Tenant, UUID> {
    fun findBySlackWorkspaceId(slackWorkspaceId: String): Tenant?

    fun findByActiveTrue(): List<Tenant>
}
