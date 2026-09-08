package io.vedra.domain.service

import io.vedra.domain.model.Tenant
import org.springframework.stereotype.Service

@Service
class TenantService(
    private val provisioningService: TenantProvisioningService,
) {
    fun resolveByExternalId(
        externalId: String?,
        workspaceName: String?,
        installerUserId: String? = null,
    ): Tenant {
        if (externalId.isNullOrBlank()) {
            throw TenantExternalIdMissingException()
        }

        return provisioningService.provisionTenant(externalId, workspaceName, installerUserId).tenant
    }
}

class TenantExternalIdMissingException : RuntimeException("Missing external ID for tenant resolution")
