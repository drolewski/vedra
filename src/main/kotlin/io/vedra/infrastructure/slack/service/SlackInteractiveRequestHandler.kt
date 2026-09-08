package io.vedra.infrastructure.slack.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.IndustryService
import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.outbox.service.OutboxService
import io.vedra.infrastructure.slack.exception.SlackApiException
import io.vedra.infrastructure.slack.exception.SlackPayloadException
import io.vedra.infrastructure.slack.model.dto.SlackInteractionPayload
import io.vedra.infrastructure.slack.service.outbox.SlackFeedbackHandler.Companion.EVENT_TYPE
import io.vedra.infrastructure.slack.service.outbox.SlackPromoteToPublicHandler
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Component
class SlackInteractiveRequestHandler(
    private val outboxService: OutboxService,
    private val slackMessageService: SlackMessageService,
    private val tenantProjectService: TenantProjectService,
    private val industryService: IndustryService,
    private val tenantResolver: SlackTenantResolver,
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

        val channelId = (view["private_metadata"] as? String)?.takeIf { it.isNotBlank() }
        return when (callbackId) {
            SlackModalService.CALLBACK_CREATE_PROJECT -> processCreateProjectSubmission(view, teamId, channelId)
            SlackModalService.CALLBACK_CONFIG_PROJECT -> processConfigProjectSubmission(view, teamId, channelId)
            else -> ResponseEntity.ok().build()
        }
    }

    private fun processCreateProjectSubmission(
        view: Map<*, *>,
        teamId: String,
        channelId: String?,
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

        val tenantResult =
            tenantResolver.withTenantResult(teamId) { tenant ->
                if (channelId != null && tenantProjectService.getChannelStatus(channelId, tenant.id!!).isLinked) {
                    val linked = tenantProjectService.getLinkedProject(channelId, tenant.id!!)
                    logger.info { "Create on already linked channel=$channelId, redirecting to config" }
                    return@withTenantResult buildInfoResponse(
                        "Ten kanał ma już przestrzeń *${linked?.name}*. Zmień szczegóły przez `/vedra config`.",
                    )
                }
                val industryName = resolveIndustryName(tenant.id!!, industrySelectId, customIndustryName)
                logger.info { "Creating project from modal: name=$projectName industry=$industryName tenant=${tenant.id}" }

                val result =
                    if (channelId != null) {
                        tenantProjectService.createPrivateAndLink(
                            tenantId = tenant.id,
                            channelId = channelId,
                            name = projectName,
                            description = description,
                            industry = industryName,
                        )
                    } else {
                        logger.warn { "Missing private_metadata channelId, creating project without link" }
                        tenantProjectService.createProject(
                            tenantId = tenant.id,
                            name = projectName,
                            description = description,
                            industry = industryName,
                        )
                    }

                when (result) {
                    is TenantProjectService.CreateResult.Success -> {
                        logger.info { "Project created: ${result.project.name}" }
                        buildSuccessResponse(
                            projectName = result.project.name,
                            description = description,
                            industryName = industryName,
                            linked = channelId != null,
                        )
                    }

                    is TenantProjectService.CreateResult.AlreadyExists -> {
                        logger.info { "Project already exists: ${result.name}" }
                        buildErrorResponse("Projekt *${result.name}* już istnieje.")
                    }

                    is TenantProjectService.CreateResult.PublicAlreadyExists -> {
                        logger.info { "Public project already exists: ${result.name}" }
                        buildErrorResponse("Publiczny projekt *${result.name}* już istnieje.")
                    }
                }
            }

        return tenantResult ?: buildErrorResponse("Błąd: Nie można zidentyfikować workspace.")
    }

    private fun processConfigProjectSubmission(
        view: Map<*, *>,
        teamId: String,
        channelId: String?,
    ): ResponseEntity<Any> {
        if (channelId == null) {
            logger.warn { "Missing private_metadata channelId in config submission" }
            return buildErrorResponse("Nie udało się ustalić kanału.")
        }
        val form = parseProjectForm(view) ?: return buildErrorResponse("Nie udało się odczytać formularza.")
        if (form.name.isBlank()) {
            return buildErrorResponse("Nazwa projektu jest wymagana.")
        }

        val tenantResult =
            tenantResolver.withTenantResult(teamId) { tenant ->
                val industryName = resolveIndustryName(tenant.id!!, form.industrySelectId, form.customIndustryName)
                logger.info { "Updating space from config modal: name=${form.name} industry=$industryName tenant=${tenant.id}" }

                when (
                    val result =
                        tenantProjectService.updateLinkedProject(
                            tenant.id,
                            channelId,
                            form.name,
                            form.description,
                            industryName,
                        )
                ) {
                    is TenantProjectService.UpdateResult.Updated -> {
                        buildConfigSuccessResponse(result.project.name, form.description, industryName)
                    }
                    is TenantProjectService.UpdateResult.NameTaken -> {
                        logger.info { "Space name taken: ${result.name}" }
                        buildErrorResponse("Projekt *${result.name}* już istnieje.")
                    }
                    TenantProjectService.UpdateResult.PublicNotEditable -> {
                        buildInfoResponse("Przestrzeni publicznej nie można edytować.")
                    }
                    TenantProjectService.UpdateResult.NoLinkedSpace -> {
                        when (val space = tenantProjectService.ensureChannelSpace(tenant.id, channelId, null)) {
                            is TenantProjectService.ChannelSpaceResult.Created ->
                                when (
                                    val retry =
                                        tenantProjectService.updateLinkedProject(
                                            tenant.id,
                                            channelId,
                                            form.name,
                                            form.description,
                                            industryName,
                                        )
                                ) {
                                    is TenantProjectService.UpdateResult.Updated ->
                                        buildConfigSuccessResponse(retry.project.name, form.description, industryName)
                                    else -> buildErrorResponse("Nie udało się zapisać konfiguracji.")
                                }
                            is TenantProjectService.ChannelSpaceResult.AlreadyLinked ->
                                buildInfoResponse("Przestrzeni publicznej nie można edytować.")
                        }
                    }
                }
            }

        return tenantResult ?: buildErrorResponse("Błąd: Nie można zidentyfikować workspace.")
    }

    private data class ProjectForm(
        val name: String,
        val description: String?,
        val industrySelectId: String?,
        val customIndustryName: String?,
    )

    private fun parseProjectForm(view: Map<*, *>): ProjectForm? {
        val state = view["state"] as? Map<*, *>
        val values = state?.get("values") as? Map<*, *> ?: return null
        val name =
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
        return ProjectForm(name, description, industrySelectId, customIndustryName)
    }

    private fun resolveIndustryName(
        tenantId: UUID,
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
        linked: Boolean = false,
    ): ResponseEntity<Any> {
        val industryInfo = industryName?.let { "Branża: $it" } ?: "Branża: nie wybrano"
        val descriptionInfo = description?.let { "Opis: $it" } ?: ""
        val linkInfo = if (linked) "\nPowiązano z tym kanałem." else ""

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
                                                ":white_check_mark: *Projekt utworzony*\n\n*$projectName*\n$industryInfo\n$descriptionInfo$linkInfo",
                                        ),
                                ),
                            ),
                    ),
            )

        return ResponseEntity.ok(response)
    }

    private fun buildConfigSuccessResponse(
        projectName: String,
        description: String?,
        industryName: String?,
    ): ResponseEntity<Any> {
        val industryInfo = industryName?.let { "Branża: $it" } ?: "Branża: nie wybrano"
        val descriptionInfo = description?.let { "\nOpis: $it" } ?: ""
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
                                                ":white_check_mark: *Zapisano konfigurację*\n\n*$projectName*\n$industryInfo$descriptionInfo",
                                        ),
                                ),
                            ),
                    ),
            )

        return ResponseEntity.ok(response)
    }

    private fun buildInfoResponse(message: String): ResponseEntity<Any> {
        val response =
            mapOf(
                "response_action" to "update",
                "view" to
                    mapOf(
                        "type" to "modal",
                        "title" to mapOf("type" to "plain_text", "text" to "Informacja"),
                        "close" to mapOf("type" to "plain_text", "text" to "Zamknij"),
                        "blocks" to
                            listOf(
                                mapOf(
                                    "type" to "section",
                                    "text" to mapOf("type" to "mrkdwn", "text" to message),
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
        if (isPromoteAction(payload)) {
            return handlePromoteInteraction(payload)
        }
        val interaction = parsePayload(payload) ?: return ResponseEntity.ok().build()
        saveFeedbackEvent(interaction)
        updateMessageButtonsIfNeeded(interaction)
        return ResponseEntity.ok().build()
    }

    private fun isPromoteAction(payload: Map<String, Any>): Boolean {
        val actions = payload["actions"] as? List<*> ?: return false
        val action = actions.firstOrNull() as? Map<*, *> ?: return false
        return action["action_id"] as? String == ACTION_PROMOTE
    }

    private fun handlePromoteInteraction(payload: Map<String, Any>): ResponseEntity<Any> {
        val actions = (payload["actions"] as? List<*>)?.firstOrNull() as? Map<*, *>
        val feedbackId = actions?.get("value") as? String
        val user = payload["user"] as? Map<*, *>
        val userId = user?.get("id") as? String
        val team = payload["team"] as? Map<*, *>
        val teamId = team?.get("id") as? String
        val container = payload["container"] as? Map<*, *>
        val message = payload["message"] as? Map<*, *>
        val channelId =
            container?.get("channel_id") as? String
                ?: message?.get("channel") as? String

        if (feedbackId == null || userId == null || teamId == null) {
            logger.warn { "Missing fields in promote interaction" }
            return ResponseEntity.ok().build()
        }
        val tenantId =
            tenantResolver.withTenantResult(teamId) { tenant -> tenant.id.toString() }
        if (tenantId == null) {
            logger.warn { "Cannot resolve tenant for promote interaction teamId=$teamId" }
            return ResponseEntity.ok().build()
        }
        outboxService.saveEvent(
            eventType = SlackPromoteToPublicHandler.EVENT_TYPE,
            aggregateId = "promote_${feedbackId}_$userId",
            payload =
                mapOf(
                    "feedbackId" to feedbackId,
                    "userId" to userId,
                    "tenantId" to tenantId,
                    "channelId" to (channelId ?: ""),
                ),
        )
        logger.info { "Promoted entry $feedbackId → public by $userId" }
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

    companion object {
        const val ACTION_PROMOTE = SlackInteractionPayload.ACTION_PROMOTE
    }
}
