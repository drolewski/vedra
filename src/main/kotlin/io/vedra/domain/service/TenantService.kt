package io.vedra.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.Tenant
import io.vedra.domain.repository.tenant.TenantRepository
import org.springframework.stereotype.Service
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class TenantService(
    private val tenantRepository: TenantRepository,
) {
    fun resolveTenantByWorkspace(workspaceId: String): Tenant =
        tenantRepository.findBySlackWorkspaceId(workspaceId)
            ?: throw TenantNotFoundException(workspaceId)

    fun getDefaultTenant(): Tenant =
        tenantRepository.findByActiveTrue().firstOrNull()
            ?: throw IllegalStateException("No active tenants found")

    fun getTenantById(tenantId: UUID): Tenant =
        tenantRepository.findById(tenantId)
            ?: throw TenantNotFoundException(tenantId.toString())

    fun createTenant(
        name: String,
        slackWorkspaceId: String? = null,
    ): Tenant {
        val tenant =
            Tenant(
                name = name,
                slackWorkspaceId = slackWorkspaceId,
            )
        return tenantRepository.save(tenant)
    }
}

class TenantNotFoundException(
    val identifier: String,
) : RuntimeException("Tenant not found: $identifier")
