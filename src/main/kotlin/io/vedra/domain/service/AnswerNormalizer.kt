package io.vedra.domain.service

import io.vedra.domain.model.NormalizedAnswer
import org.springframework.stereotype.Component

@Component
class AnswerNormalizer {
    fun normalize(llmResponse: String): NormalizedAnswer {
        var result = llmResponse.trim()
        result = removeOpeningPhrases(result)
        result = removeClosingPhrases(result)
        result = normalizeWhitespace(result)
        return NormalizedAnswer(
            original = llmResponse,
            normalized = result,
        )
    }

    private fun removeOpeningPhrases(text: String): String {
        var result = text
        for (phrase in OPENING_PHRASES) {
            val pattern = Regex("^\\s*${Regex.escape(phrase)}[,:]?\\s*", RegexOption.IGNORE_CASE)
            result = pattern.replace(result, "")
        }
        return result
    }

    private fun removeClosingPhrases(text: String): String {
        var result = text
        for (phrase in CLOSING_PHRASES) {
            val pattern = Regex("${Regex.escape(phrase)}[.!?]*\\s*$", RegexOption.IGNORE_CASE)
            result = pattern.replace(result, "")
        }
        return result
    }

    private fun normalizeWhitespace(text: String): String =
        text
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    companion object {
        private val OPENING_PHRASES =
            listOf(
                "Odpowiadając na pytanie",
                "Na podstawie dostarczonej wiedzy",
                "W odpowiedzi na",
                "Zgodnie z dostępną wiedzą",
                "Mając na uwadze",
                "Analizując dostarczony kontekst",
            )

        private val CLOSING_PHRASES =
            listOf(
                "Mam nadzieję, że pomogłem",
                "Mam nadzieję, że pomogłam",
                "W razie pytań",
                "Jeśli potrzebujesz więcej informacji",
                "Jeśli masz dodatkowe pytania",
                "Pozdrawiam",
                "Życzę powodzenia",
            )
    }
}
