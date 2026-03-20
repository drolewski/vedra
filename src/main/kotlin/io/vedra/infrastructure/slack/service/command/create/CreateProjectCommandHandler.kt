package io.vedra.infrastructure.slack.service.command.create

import io.vedra.domain.service.outbox.project.create.CreateProjectOutboxEventHandler
import io.vedra.infrastructure.outbox.service.OutboxService
import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class CreateProjectCommandHandler(
    private val outboxService: OutboxService,
) : CommandHandler<CreateProjectCommand> {
    override fun supports(command: Any): Boolean = command is CreateProjectCommand

    override fun handle(command: CreateProjectCommand): String {
        val project = command.projectName
        val teamId = command.teamId
        val channelId = command.channelId

        outboxService.saveEvent(
            eventType = CreateProjectOutboxEventHandler.EVENT_TYPE,
            aggregateId = "$teamId-$channelId-$project",
            payload =
                mapOf<String, Any>(
                    "teamId" to teamId,
                    "channelId" to channelId,
                    "project" to project,
                ),
        )

        return "Tworzę projekt *$project*..."
    }
}
