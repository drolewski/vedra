package io.vedra.domain.service.outbox.project.create

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.outbox.handler.OutboxEventHandler
import org.springframework.stereotype.Component
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Component
class CreateProjectOutboxEventHandler(
    private val tenantProjectService: TenantProjectService,
) : OutboxEventHandler {
    override fun supports(eventType: String): Boolean = eventType == EVENT_TYPE

    override fun handle(event: OutboxEvent) {
        val teamId = event.payload["teamId"] as? String
        val projectName = event.payload["project"] as? String

        if (teamId == null || projectName.isNullOrBlank()) {
            logger.warn { "Missing required payload fields in CREATE_PROJECT event: ${event.id}" }
            return
        }

        val tenantId = UUID.nameUUIDFromBytes(teamId.toByteArray())

        logger.debug { "Creating project=$projectName for tenant=$tenantId" }

        val project =
            tenantProjectService.createProject(
                tenantId = tenantId,
                name = projectName,
                description = null,
                isGeneral = false,
            )

        logger.info { "Created project: ${project.name} (id=${project.id})" }
    }

    companion object {
        const val EVENT_TYPE = "CREATE_PROJECT"
    }
}
