package io.vedra.infrastructure.slack.service.command.list

import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.slack.service.SlackTenantResolver
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class ListProjectsCommandHandler(
    private val tenantProjectService: TenantProjectService,
    private val tenantResolver: SlackTenantResolver,
) : CommandHandler<ListProjectsCommand> {
    override fun supports(command: Any): Boolean = command is ListProjectsCommand

    override fun handle(command: ListProjectsCommand): String =
        tenantResolver.withTenant(command.workspaceId) { tenant ->
            val projects = tenantProjectService.listProjects(tenant.id!!)

            if (projects.isEmpty()) {
                return@withTenant "Brak skonfigurowanych projektów."
            }

            val lines =
                projects.map { project ->
                    val tags = mutableListOf<String>()
                    if (project.isGeneral) tags.add("ogólna")
                    project.industry?.let { tags.add("branża: $it") }
                    val label = if (tags.isNotEmpty()) "(${tags.joinToString(", ")})" else ""
                    "• *${project.name}* $label"
                }

            "Dostępne projekty:\n${lines.joinToString("\n")}"
        }
}
