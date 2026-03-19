package io.vedra.infrastructure.chat

interface LLMPort {
    fun generate(
        systemPrompt: String,
        userPrompt: String,
    ): String?
}
