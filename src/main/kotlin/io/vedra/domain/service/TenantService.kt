package io.vedra.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.Tenant
import io.vedra.domain.repository.tenant.TenantRepository
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class TenantService(
    private val tenantRepository: TenantRepository,
) {
    fun resolveByExternalId(
        externalId: String?,
        workspaceName: String?,
    ): Tenant {
        if (externalId.isNullOrBlank()) {
            throw TenantExternalIdMissingException()
        }

        return tenantRepository.findByExternalId(externalId)
            ?: createTenant(externalId, workspaceName)
    }

    private fun createTenant(
        externalId: String,
        workspaceName: String?,
    ): Tenant {
        val name = workspaceName ?: externalId
        logger.info { "Creating new tenant for externalId=$externalId name=$name" }
        return tenantRepository.save(Tenant(name = name, externalId = externalId))
    }
}

class TenantExternalIdMissingException : RuntimeException("Missing external ID for tenant resolution")
