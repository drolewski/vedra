package io.vedra.infrastructure.slack.service.command.create

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Component
class CreateProjectCommandHandler(
    private val tenantProjectService: TenantProjectService,
) : CommandHandler<CreateProjectCommand> {
    override fun supports(command: Any): Boolean = command is CreateProjectCommand

    override fun handle(command: CreateProjectCommand): String {
        if (command.projectName.isBlank()) {
            return "Podaj nazwę projektu: `/vedra create <nazwa-projektu>`"
        }

        val tenantId = UUID.nameUUIDFromBytes(command.teamId.toByteArray())
        logger.debug { "Creating project '${command.projectName}' for tenant=$tenantId" }

        return try {
            when (val result = tenantProjectService.createProject(tenantId, command.projectName)) {
                is TenantProjectService.CreateResult.Success ->
                    "Projekt *${result.project.name}* został utworzony."

                is TenantProjectService.CreateResult.AlreadyExists ->
                    "Projekt *${result.name}* już istnieje."
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create project '${command.projectName}'" }
            "Wystąpił błąd podczas tworzenia projektu."
        }
    }
}
