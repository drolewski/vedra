package io.vedra.infrastructure.database.tenant

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataTenantRepository : JpaRepository<Tenant, UUID> {
    fun findBySlackWorkspaceId(slackWorkspaceId: String): Tenant?

    fun findByActiveTrue(): List<Tenant>
}
