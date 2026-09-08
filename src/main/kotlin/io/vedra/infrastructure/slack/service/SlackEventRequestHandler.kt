package io.vedra.infrastructure.slack.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.repository.tenant.TenantRepository
import io.vedra.domain.service.SlackInstallationService
import io.vedra.infrastructure.slack.model.SlackEvent
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SlackEventRequestHandler(
    private val eventProcessor: SlackEventProcessor,
    private val installationService: SlackInstallationService,
    private val tenantRepository: TenantRepository,
) {
    fun handle(body: Map<String, Any>): ResponseEntity<Any> {
        if (handleUninstall(body)) return ResponseEntity.ok().build()
        val event =
            try {
                SlackEvent.fromRawBody(body)
            } catch (e: IllegalArgumentException) {
                logger.warn(e) { "Invalid Slack event payload" }
                return ResponseEntity.ok().build()
            }

        logger.info { "Processing Slack event: type=${event.type} eventId=${event.eventId}" }
        eventProcessor.process(event)
        return ResponseEntity.ok().build()
    }

    private fun handleUninstall(body: Map<String, Any>): Boolean {
        if (body["type"] != "event_callback") return false
        val inner = body["event"] as? Map<*, *> ?: return false
        val innerType = inner["type"] as? String ?: return false
        if (innerType != "app_uninstalled" && innerType != "tokens_revoked") return false
        val teamId = body["team_id"] as? String ?: return true
        installationService.markRevoked(teamId)
        tenantRepository.findByExternalId(teamId)?.let {
            if (it.active) tenantRepository.save(it.copy(active = false))
        }
        logger.info { "[INSTALL] Uninstalled team=$teamId type=$innerType" }
        return true
    }
}
