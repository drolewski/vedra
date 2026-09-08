package io.vedra.domain.service

import io.vedra.domain.model.NormalizedQuestion
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.UUID

@Component
class QuestionNormalizer {
    fun normalize(
        question: String,
        projectId: UUID?,
    ): NormalizedQuestion {
        var result = question.trim()
        result = removeBotMentions(result)
        result = removeTrailingPunctuation(result)
        result = normalizeWhitespace(result)
        val lowercased = result.lowercase()
        val hashInput = lowercased + (projectId?.toString() ?: "")
        val hash = sha256(hashInput)
        return NormalizedQuestion(
            original = question,
            normalized = lowercased,
            hash = hash,
            projectId = projectId,
        )
    }

    private fun removeBotMentions(text: String): String =
        text
            .replace(Regex("@[Vv]edra\\s*"), "")
            .trim()

    private fun removeTrailingPunctuation(text: String): String =
        text
            .replace(Regex("[?!.,;:]+$"), "")
            .trim()

    private fun normalizeWhitespace(text: String): String =
        text
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
