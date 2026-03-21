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

        val industries = industryService.getIndustriesForTenant(tenantId)
        logger.debug { "Found ${industries.size} industries for tenant=$tenantId: ${industries.map { it.name }}" }

        val industryOptions =
            industries.map { industry ->
                mapOf(
                    "text" to mapOf("type" to "plain_text", "text" to industry.name),
                    "value" to industry.id.toString(),
                )
            } +
                listOf(
                    mapOf(
                        "text" to mapOf("type" to "plain_text", "text" to "+ Dodaj nową"),
                        "value" to "new",
                    ),
                )

        logger.debug { "Built ${industryOptions.size} industry options for modal" }

        val viewJson =
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to "modal",
                    "callback_id" to CALLBACK_CREATE_PROJECT,
                    "private_metadata" to channelId,
                    "title" to mapOf("type" to "plain_text", "text" to "Utwórz projekt"),
                    "submit" to mapOf("type" to "plain_text", "text" to "Utwórz"),
                    "close" to mapOf("type" to "plain_text", "text" to "Anuluj"),
                    "blocks" to
                        listOf(
                            mapOf(
                                "type" to "input",
                                "block_id" to "project_name_block",
                                "label" to mapOf("type" to "plain_text", "text" to "Nazwa projektu"),
                                "element" to
                                    mapOf(
                                        "type" to "plain_text_input",
                                        "action_id" to "project_name_input",
                                        "placeholder" to
                                            mapOf(
                                                "type" to "plain_text",
                                                "text" to "np. budowa-osiedla",
                                            ),
                                    ),
                            ),
                            mapOf(
                                "type" to "input",
                                "block_id" to "project_description_block",
                                "label" to mapOf("type" to "plain_text", "text" to "Opis projektu"),
                                "optional" to true,
                                "element" to
                                    mapOf(
                                        "type" to "plain_text_input",
                                        "action_id" to "project_description_input",
                                        "multiline" to true,
                                        "placeholder" to
                                            mapOf(
                                                "type" to "plain_text",
                                                "text" to "Krótki opis projektu...",
                                            ),
                                    ),
                            ),
                            mapOf(
                                "type" to "input",
                                "block_id" to "project_industry_block",
                                "label" to mapOf("type" to "plain_text", "text" to "Branża"),
                                "optional" to true,
                                "element" to
                                    mapOf(
                                        "type" to "static_select",
                                        "action_id" to "project_industry_select",
                                        "placeholder" to
                                            mapOf(
                                                "type" to "plain_text",
                                                "text" to "Wybierz branżę",
                                            ),
                                        "options" to industryOptions,
                                    ),
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
    }
}
