package io.vedra.domain.repository.tenant

import io.vedra.domain.model.Tenant
import java.util.UUID

interface TenantRepository {
    fun findBySlackWorkspaceId(workspaceId: String): Tenant?

    fun findByActiveTrue(): List<Tenant>

    fun findById(id: UUID): Tenant?

    fun save(tenant: Tenant): Tenant
}
