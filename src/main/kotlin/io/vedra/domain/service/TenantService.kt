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
    fun resolveByExternalId(externalId: String): Tenant =
        tenantRepository.findByExternalId(externalId)
            ?: createDefaultTenant(externalId)

    private fun createDefaultTenant(externalId: String): Tenant {
        logger.info { "Creating new tenant for externalId=$externalId" }
        return tenantRepository.save(Tenant(name = "Nowy klient"))
    }

    fun getDefaultTenant(): Tenant =
        tenantRepository.findByActiveTrue().firstOrNull()
            ?: throw IllegalStateException("No active tenants found")

    fun getTenantById(tenantId: UUID): Tenant =
        tenantRepository.findById(tenantId)
            ?: throw TenantNotFoundException(tenantId.toString())

    fun createTenant(
        name: String,
        externalId: String? = null,
    ): Tenant {
        val tenant =
            Tenant(
                name = name,
                externalId = externalId,
            )
        return tenantRepository.save(tenant)
    }
}

class TenantNotFoundException(
    val identifier: String,
) : RuntimeException("Tenant not found: $identifier")
