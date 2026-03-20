package io.vedra.infrastructure.slack.api.commands

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.TenantProjectService
import io.vedra.domain.service.TenantProjectService.LinkResult
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/slack/commands")
class SlackSlashCommandController(
    private val tenantProjectService: TenantProjectService,
) {
    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun handleCommand(
        @RequestParam("command") command: String,
        @RequestParam("text") text: String,
        @RequestParam("channel_id") channelId: String,
        @RequestParam("team_id") teamId: String,
    ): ResponseEntity<Map<String, String>> {
        logger.info { "Received slash command: $command $text in channel=$channelId" }

        if (command != "/vedra") {
            return ResponseEntity.ok(mapOf("text" to "Nieznana komenda"))
        }

        val parts = text.trim().split("\\s+".toRegex(), limit = 2)
        val action = parts.getOrNull(0)?.lowercase() ?: "help"
        val argument = parts.getOrNull(1)

        val response =
            when (action) {
                "create" -> handleCreate(teamId, channelId, argument)
                "link" -> handleLink(teamId, channelId, argument)
                "unlink" -> handleUnlink(channelId)
                "list" -> handleList(teamId)
                "status" -> handleStatus(channelId)
                else -> helpMessage()
            }

        return ResponseEntity.ok(mapOf("response_type" to "ephemeral", "text" to response))
    }

    private fun handleCreate(
        teamId: String,
        channelId: String,
        projectName: String?,
    ): String {
        if (projectName.isNullOrBlank()) {
            return "Podaj nazwę projektu: `/vedra link <nazwa-projektu>`"
        }

        logger.info { "utwórz projekt" }
        return ""
    }

    private fun handleLink(
        teamId: String,
        channelId: String,
        projectName: String?,
    ): String {
        if (projectName.isNullOrBlank()) {
            return "Podaj nazwę projektu: `/vedra link <nazwa-projektu>`"
        }

        val tenantId = UUID.nameUUIDFromBytes(teamId.toByteArray())

        return when (tenantProjectService.linkChannelToProject(tenantId, channelId, projectName)) {
            LinkResult.SUCCESS -> "Kanał powiązany z projektem *$projectName*"
            LinkResult.PROJECT_NOT_FOUND -> "Projekt *$projectName* nie istnieje. Użyj `/vedra list` aby zobaczyć dostępne projekty."
            LinkResult.CANNOT_LINK_GENERAL -> "Nie można powiązać kanału z projektem ogólnym."
        }
    }

    private fun handleUnlink(channelId: String): String {
        tenantProjectService.unlinkChannel(channelId)
        return "Kanał odłączony od projektu. Teraz używa bazy ogólnej."
    }

    private fun handleList(teamId: String): String {
        val tenantId = UUID.nameUUIDFromBytes(teamId.toByteArray())
        val projects = tenantProjectService.listProjects(tenantId)

        if (projects.isEmpty()) {
            return "Brak skonfigurowanych projektów."
        }

        val lines =
            projects.map { project ->
                val label = if (project.isGeneral) "(ogólna)" else ""
                "• *${project.name}* $label"
            }

        return "Dostępne projekty:\n${lines.joinToString("\n")}"
    }

    private fun handleStatus(channelId: String): String {
        val status = tenantProjectService.getChannelStatus(channelId)

        return if (status.isLinked) {
            "Ten kanał jest powiązany z projektem *${status.projectName}*.\n" +
                "Odpowiedzi będą szukane w bazie projektu + baza ogólna."
        } else {
            "Ten kanał nie jest powiązany z żadnym projektem.\n" +
                "Odpowiedzi będą szukane tylko w bazie ogólnej.\n" +
                "Użyj `/vedra link <nazwa-projektu>` aby powiązać."
        }
    }

    private fun helpMessage(): String =
        """
        *Komendy Vedra:*
        • `/vedra link <projekt>` - Powiąż kanał z projektem
        • `/vedra unlink` - Odłącz kanał od projektu
        • `/vedra list` - Pokaż dostępne projekty
        • `/vedra status` - Pokaż aktualny kontekst kanału
        """.trimIndent()
}
