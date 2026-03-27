package io.vedra.infrastructure.slack.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.IndustryService
import io.vedra.domain.service.TenantExternalIdMissingException
import io.vedra.domain.service.TenantProjectService
import io.vedra.domain.service.TenantService
import io.vedra.infrastructure.outbox.service.OutboxService
import io.vedra.infrastructure.slack.exception.SlackApiException
import io.vedra.infrastructure.slack.exception.SlackPayloadException
import io.vedra.infrastructure.slack.model.dto.SlackInteractionPayload
import io.vedra.infrastructure.slack.service.outbox.SlackFeedbackHandler.Companion.EVENT_TYPE
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class SlackInteractiveRequestHandler(
    private val outboxService: OutboxService,
    private val slackMessageService: SlackMessageService,
    private val tenantProjectService: TenantProjectService,
    private val industryService: IndustryService,
    private val tenantService: TenantService,
    private val slackClient: io.vedra.infrastructure.slack.port.SlackClientPort,
) {
    fun handle(payload: Map<String, Any>): ResponseEntity<Any> {
        val type = payload["type"] as? String

        return when (type) {
            "view_submission" -> handleViewSubmission(payload)
            else -> handleButtonInteraction(payload)
        }
    }

    private fun handleViewSubmission(payload: Map<String, Any>): ResponseEntity<Any> {
        val view = payload["view"] as? Map<*, *> ?: return ResponseEntity.ok().build()
        val team = payload["team"] as? Map<*, *>
        val teamId = team?.get("id") as? String ?: return ResponseEntity.ok().build()
        val callbackId = view["callback_id"] as? String

        if (callbackId != SlackModalService.CALLBACK_CREATE_PROJECT) {
            return ResponseEntity.ok().build()
        }

        return processCreateProjectSubmission(view, teamId)
    }

    private fun processCreateProjectSubmission(
        view: Map<*, *>,
        teamId: String,
    ): ResponseEntity<Any> {
        val state = view["state"] as? Map<*, *>
        val values = state?.get("values") as? Map<*, *>

        if (values == null) {
            return buildErrorResponse("Nie udało się odczytać formularza.")
        }

        val projectName =
            (values["project_name_block"] as? Map<*, *>)
                ?.let { (it["project_name_input"] as? Map<*, *>)?.get("value") as? String }
                ?.trim()
                ?: ""

        val description =
            (values["project_description_block"] as? Map<*, *>)
                ?.let { (it["project_description_input"] as? Map<*, *>)?.get("value") as? String }
                ?.trim()
                ?.ifBlank { null }

        val industrySelectId =
            (values["project_industry_block"] as? Map<*, *>)
                ?.let { (it["project_industry_select"] as? Map<*, *>)?.get("selected_option") as? Map<*, *> }
                ?.let { it["value"] as? String }

        val customIndustryName =
            (values["custom_industry_block"] as? Map<*, *>)
                ?.let { (it["custom_industry_input"] as? Map<*, *>)?.get("value") as? String }
                ?.trim()
                ?.ifBlank { null }

        if (projectName.isBlank()) {
            return buildErrorResponse("Nazwa projektu jest wymagana.")
        }

        val tenant =
            try {
                val teamName = slackClient.getTeamName(teamId)
                tenantService.resolveByExternalId(teamId, teamName)
            } catch (e: TenantExternalIdMissingException) {
                logger.error(e) { "Cannot resolve tenant: missing teamId" }
                return buildErrorResponse("Błąd: Nie można zidentyfikować workspace.")
            }

        val industryName = resolveIndustryName(tenant.id!!, industrySelectId, customIndustryName)

        logger.info { "Creating project from modal: name=$projectName industry=$industryName tenant=${tenant.id}" }

        val result =
            tenantProjectService.createProject(
                tenantId = tenant.id,
                name = projectName,
                description = description,
                industry = industryName,
            )

        return when (result) {
            is TenantProjectService.CreateResult.Success -> {
                logger.info { "Project created: ${result.project.name}" }
                buildSuccessResponse(
                    projectName = result.project.name,
                    description = description,
                    industryName = industryName,
                )
            }

            is TenantProjectService.CreateResult.AlreadyExists -> {
                logger.info { "Project already exists: ${result.name}" }
                buildErrorResponse("Projekt *${result.name}* już istnieje.")
            }
        }
    }

    private fun resolveIndustryName(
        tenantId: java.util.UUID,
        industrySelectId: String?,
        customIndustryName: String?,
    ): String? {
        if (customIndustryName != null) {
            val industry = industryService.getOrCreateIndustry(tenantId, customIndustryName)
            return industry.name
        }

        if (industrySelectId == "new") {
            return null
        }

        if (industrySelectId != null) {
            return try {
                industryService
                    .getIndustriesForTenant(tenantId)
                    .find { it.id.toString() == industrySelectId }
                    ?.name
            } catch (_: Exception) {
                null
            }
        }

        return null
    }

    private fun buildSuccessResponse(
        projectName: String,
        description: String?,
        industryName: String?,
    ): ResponseEntity<Any> {
        val industryInfo = industryName?.let { "Branża: $it" } ?: "Branża: nie wybrano"
        val descriptionInfo = description?.let { "Opis: $it" } ?: ""

        val response =
            mapOf(
                "response_action" to "update",
                "view" to
                    mapOf(
                        "type" to "modal",
                        "title" to mapOf("type" to "plain_text", "text" to "Sukces"),
                        "close" to mapOf("type" to "plain_text", "text" to "Zamknij"),
                        "blocks" to
                            listOf(
                                mapOf(
                                    "type" to "section",
                                    "text" to
                                        mapOf(
                                            "type" to "mrkdwn",
                                            "text" to
                                                ":white_check_mark: *Projekt utworzony*\n\n*$projectName*\n$industryInfo\n$descriptionInfo",
                                        ),
                                ),
                            ),
                    ),
            )

        return ResponseEntity.ok(response)
    }

    private fun buildErrorResponse(message: String): ResponseEntity<Any> {
        val response =
            mapOf(
                "response_action" to "errors",
                "errors" to mapOf("project_name_block" to message),
            )

        return ResponseEntity.ok(response)
    }

    private fun handleButtonInteraction(payload: Map<String, Any>): ResponseEntity<Any> {
        val interaction = parsePayload(payload) ?: return ResponseEntity.ok().build()
        saveFeedbackEvent(interaction)
        updateMessageButtonsIfNeeded(interaction)
        return ResponseEntity.ok().build()
    }

    private fun parsePayload(payload: Map<String, Any>): SlackInteractionPayload? =
        try {
            SlackInteractionPayload.fromRawPayload(payload).also {
                logger.info {
                    "Processing feedback interaction: feedbackId=${it.feedbackId} " +
                        "userId=${it.userId} vote=${it.vote}"
                }
            }
        } catch (e: SlackPayloadException) {
            logger.warn(e) { "Invalid Slack interactive payload structure" }
            null
        }

    private fun saveFeedbackEvent(interaction: SlackInteractionPayload) {
        outboxService.saveEvent(
            eventType = EVENT_TYPE,
            aggregateId = "${interaction.feedbackId}_${interaction.userId}",
            payload =
                mapOf(
                    "feedbackId" to interaction.feedbackId,
                    "userId" to interaction.userId,
                    "vote" to interaction.vote,
                ),
        )
    }

    private fun updateMessageButtonsIfNeeded(interaction: SlackInteractionPayload) {
        if (!interaction.hasMessageContext) return
        updateMessageButtons(interaction)
    }

    private fun updateMessageButtons(interaction: SlackInteractionPayload) {
        try {
            slackMessageService.replaceFeedbackButtons(
                channel = interaction.channelId!!,
                messageTs = interaction.messageTs!!,
                originalBlocks = interaction.originalBlocks!!,
                vote = interaction.vote,
            )
        } catch (e: SlackApiException) {
            logger.error(e) {
                "Failed to update feedback buttons for message=${interaction.messageTs} " +
                    "in channel=${interaction.channelId}"
            }
        }
    }
}
