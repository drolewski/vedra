package io.vedra.infrastructure.chat.ollama

import io.vedra.infrastructure.chat.LLMPort
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component

@Component
class OllamaLLMAdapter(
    private val chatModel: ChatModel,
) : LLMPort {
    override fun generate(
        systemPrompt: String,
        userPrompt: String,
    ): String? {
        val prompt =
            Prompt
                .builder()
                .messages(
                    SystemMessage(systemPrompt),
                    UserMessage(userPrompt),
                ).build()
        val generation = chatModel.call(prompt)
        return generation.result?.output?.text
    }
}
