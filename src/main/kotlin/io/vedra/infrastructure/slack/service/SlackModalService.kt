package io.vedra.infrastructure.slack.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.slack.api.methods.request.views.ViewsOpenRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.IndustryService
import io.vedra.infrastructure.slack.port.SlackClientPort
import org.springframework.stereotype.Service
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class SlackModalService(
    private val slackClient: SlackClientPort,
    private val industryService: IndustryService,
) {
    private val objectMapper = jacksonObjectMapper()

    fun openCreateProjectModal(
        triggerId: String,
        channelId: String,
        tenantId: UUID,
    ) {
        logger.info { "Opening create project modal for triggerId=$triggerId channelId=$channelId tenantId=$tenantId" }
        openProjectModal(
            triggerId = triggerId,
            channelId = channelId,
            tenantId = tenantId,
            callbackId = CALLBACK_CREATE_PROJECT,
            title = "Utwórz projekt",
            submit = "Utwórz",
            projectName = null,
            description = null,
            industry = null,
        )
    }

    fun openConfigModal(
        triggerId: String,
        channelId: String,
        tenantId: UUID,
        projectName: String,
        description: String?,
        industry: String?,
    ) {
        logger.info { "Opening config modal for triggerId=$triggerId channelId=$channelId tenantId=$tenantId" }
        openProjectModal(
            triggerId = triggerId,
            channelId = channelId,
            tenantId = tenantId,
            callbackId = CALLBACK_CONFIG_PROJECT,
            title = "Konfiguracja przestrzeni",
            submit = "Zapisz",
            projectName = projectName,
            description = description,
            industry = industry,
        )
    }

    private fun openProjectModal(
        triggerId: String,
        channelId: String,
        tenantId: UUID,
        callbackId: String,
        title: String,
        submit: String,
        projectName: String?,
        description: String?,
        industry: String?,
    ) {
        val industries = industryService.getIndustriesForTenant(tenantId)
        logger.debug { "Found ${industries.size} industries for tenant=$tenantId: ${industries.map { it.name }}" }

        val industryOptions =
            industries.map { item ->
                mapOf(
                    "text" to mapOf("type" to "plain_text", "text" to item.name),
                    "value" to item.id.toString(),
                )
            } +
                listOf(
                    mapOf(
                        "text" to mapOf("type" to "plain_text", "text" to "+ Dodaj nową"),
                        "value" to "new",
                    ),
                )

        logger.debug { "Built ${industryOptions.size} industry options for modal" }

        val initialIndustryOption =
            industry?.let { current ->
                industries.find { it.name == current }?.let { match ->
                    mapOf(
                        "text" to mapOf("type" to "plain_text", "text" to match.name),
                        "value" to match.id.toString(),
                    )
                }
            }

        val viewJson =
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to "modal",
                    "callback_id" to callbackId,
                    "private_metadata" to channelId,
                    "title" to mapOf("type" to "plain_text", "text" to title),
                    "submit" to mapOf("type" to "plain_text", "text" to submit),
                    "close" to mapOf("type" to "plain_text", "text" to "Anuluj"),
                    "blocks" to
                        listOf(
                            mapOf(
                                "type" to "input",
                                "block_id" to "project_name_block",
                                "label" to mapOf("type" to "plain_text", "text" to "Nazwa projektu"),
                                "element" to
                                    buildMap<String, Any> {
                                        put("type", "plain_text_input")
                                        put("action_id", "project_name_input")
                                        put(
                                            "placeholder",
                                            mapOf(
                                                "type" to "plain_text",
                                                "text" to "np. budowa-osiedla",
                                            ),
                                        )
                                        if (projectName != null) put("initial_value", projectName)
                                    },
                            ),
                            mapOf(
                                "type" to "input",
                                "block_id" to "project_description_block",
                                "label" to mapOf("type" to "plain_text", "text" to "Opis projektu"),
                                "optional" to true,
                                "element" to
                                    buildMap<String, Any> {
                                        put("type", "plain_text_input")
                                        put("action_id", "project_description_input")
                                        put("multiline", true)
                                        put(
                                            "placeholder",
                                            mapOf(
                                                "type" to "plain_text",
                                                "text" to "Krótki opis projektu...",
                                            ),
                                        )
                                        if (description != null) put("initial_value", description)
                                    },
                            ),
                            mapOf(
                                "type" to "input",
                                "block_id" to "project_industry_block",
                                "label" to mapOf("type" to "plain_text", "text" to "Branża"),
                                "optional" to true,
                                "element" to
                                    buildMap<String, Any> {
                                        put("type", "static_select")
                                        put("action_id", "project_industry_select")
                                        put(
                                            "placeholder",
                                            mapOf(
                                                "type" to "plain_text",
                                                "text" to "Wybierz branżę",
                                            ),
                                        )
                                        put("options", industryOptions)
                                        if (initialIndustryOption != null) put("initial_option", initialIndustryOption)
                                    },
                            ),
                            mapOf(
                                "type" to "input",
                                "block_id" to "custom_industry_block",
                                "label" to mapOf("type" to "plain_text", "text" to "Nazwa nowej branży"),
                                "optional" to true,
                                "element" to
                                    mapOf(
                                        "type" to "plain_text_input",
                                        "action_id" to "custom_industry_input",
                                        "placeholder" to
                                            mapOf(
                                                "type" to "plain_text",
                                                "text" to "Wpisz nazwę branży...",
                                            ),
                                    ),
                            ),
                        ),
                ),
            )

        val request =
            ViewsOpenRequest
                .builder()
                .triggerId(triggerId)
                .viewAsString(viewJson)
                .build()

        slackClient.openModal(request)
        logger.info { "Modal opened successfully for tenant=$tenantId" }
    }

    companion object {
        const val CALLBACK_CREATE_PROJECT = "create_project_modal"
        const val CALLBACK_CONFIG_PROJECT = "config_project_modal"
    }
}
