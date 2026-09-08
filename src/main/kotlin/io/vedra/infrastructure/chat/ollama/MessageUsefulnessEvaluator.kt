package io.vedra.infrastructure.chat.ollama

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.MessageUsefulnessResult
import io.vedra.domain.model.TenantProject
import io.vedra.domain.port.MessageUsefulnessPort
import io.vedra.domain.prompt.Prompts
import io.vedra.infrastructure.chat.LLMPort
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class MessageUsefulnessEvaluator(
    private val llmPort: LLMPort,
    private val objectMapper: ObjectMapper,
) : MessageUsefulnessPort {
    override fun evaluate(
        content: String,
        project: TenantProject,
        userId: String,
    ): MessageUsefulnessResult? {
        val userPrompt =
            Prompts.MessageUsefulness.userPrompt(
                content = content,
                projectName = project.name,
                industry = project.industry,
                userId = userId,
            )

        val response = llmPort.generate(Prompts.MessageUsefulness.system, userPrompt)

        if (response == null) {
            logger.warn { "LLM returned null for message usefulness evaluation" }
            return null
        }

        return try {
            val json = extractJson(response)
            objectMapper.readValue(json, MessageUsefulnessResult::class.java)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse LLM response as MessageUsefulnessResult: $response" }
            null
        }
    }

    private fun extractJson(response: String): String {
        val trimmed = response.trim()
        if (trimmed.startsWith("{")) return trimmed

        val jsonStart = trimmed.indexOf('{')
        val jsonEnd = trimmed.lastIndexOf('}')
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1)
        }

        return trimmed
    }
}
