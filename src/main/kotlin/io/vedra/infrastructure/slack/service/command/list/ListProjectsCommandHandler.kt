package io.vedra.infrastructure.slack.service.command.list

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.TenantExternalIdMissingException
import io.vedra.domain.service.TenantProjectService
import io.vedra.domain.service.TenantService
import io.vedra.infrastructure.slack.port.SlackClientPort
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class ListProjectsCommandHandler(
    private val tenantProjectService: TenantProjectService,
    private val tenantService: TenantService,
    private val slackClient: SlackClientPort,
) : CommandHandler<ListProjectsCommand> {
    override fun supports(command: Any): Boolean = command is ListProjectsCommand

    override fun handle(command: ListProjectsCommand): String {
        val tenant =
            try {
                val teamName = slackClient.getTeamName(command.workspaceId)
                tenantService.resolveByExternalId(command.workspaceId, teamName)
            } catch (e: TenantExternalIdMissingException) {
                logger.error(e) { "Cannot resolve tenant: missing workspaceId" }
                return "Błąd: Nie można zidentyfikować workspace."
            }

        val projects = tenantProjectService.listProjects(tenant.id!!)

        if (projects.isEmpty()) {
            return "Brak skonfigurowanych projektów."
        }

        val lines =
            projects.map { project ->
                val tags = mutableListOf<String>()
                if (project.isGeneral) tags.add("ogólna")
                project.industry?.let { tags.add("branża: $it") }
                val label = if (tags.isNotEmpty()) "(${tags.joinToString(", ")})" else ""
                "• *${project.name}* $label"
            }

        return "Dostępne projekty:\n${lines.joinToString("\n")}"
    }
}
