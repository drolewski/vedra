package io.vedra.infrastructure.slack.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.Tenant
import io.vedra.domain.service.TenantExternalIdMissingException
import io.vedra.domain.service.TenantService
import io.vedra.infrastructure.slack.port.SlackClientPort
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SlackTenantResolver(
    private val tenantService: TenantService,
    private val slackClientPort: SlackClientPort,
) {
    fun withTenant(
        externalId: String,
        block: (Tenant) -> String,
    ): String =
        try {
            val workspaceName = slackClientPort.getTeamName(externalId)
            val tenant = tenantService.resolveByExternalId(externalId, workspaceName)
            block(tenant)
        } catch (e: TenantExternalIdMissingException) {
            logger.error(e) { "Cannot resolve tenant: missing externalId" }
            "Błąd: Nie można zidentyfikować workspace."
        }

    fun <T> withTenantResult(
        externalId: String,
        block: (Tenant) -> T,
    ): T? =
        try {
            val workspaceName = slackClientPort.getTeamName(externalId)
            val tenant = tenantService.resolveByExternalId(externalId, workspaceName)
            block(tenant)
        } catch (e: TenantExternalIdMissingException) {
            logger.error(e) { "Cannot resolve tenant: missing externalId" }
            null
        }
}
