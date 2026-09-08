package io.vedra.infrastructure.slack

import org.springframework.stereotype.Component

@Component
class SlackMessageNormalizer {
    fun normalize(rawMessage: String): String {
        var result = rawMessage
        result = removeSlackFormatting(result)
        result = resolveMentions(result)
        result = resolveChannels(result)
        result = resolveLinks(result)
        result = resolveEmoji(result)
        result = normalizeWhitespace(result)
        return result.trim()
    }

    private fun removeSlackFormatting(text: String): String {
        var result = text
        result = result.replace(Regex("```[\\s\\S]*?```"), "") // code blocks
        result = result.replace(Regex("`[^`]*`"), "") // inline code
        result = result.replace(Regex("\\*\\*([^*]*)\\*\\*"), "$1") // bold
        result = result.replace(Regex("_([^_]*)_"), "$1") // italic
        result = result.replace(Regex("~([^~]*)~"), "$1") // strikethrough
        result = result.replace(Regex(">\\s?"), "") // blockquotes
        return result
    }

    private fun resolveMentions(text: String): String = text.replace(Regex("<@(U[A-Z0-9]+)>"), "@$1")

    private fun resolveChannels(text: String): String =
        text
            .replace(Regex("<#(C[A-Z0-9]+)\\|([^>]+)>"), "#$2")
            .replace(Regex("<#(C[A-Z0-9]+)>"), "#$1")

    private fun resolveLinks(text: String): String =
        text
            .replace(Regex("<(https?://[^|>]+)\\|([^>]+)>"), "$2 ($1)")
            .replace(Regex("<(https?://[^>]+)>"), "$1")

    private fun resolveEmoji(text: String): String =
        text.replace(Regex(":([a-z0-9_+-]+):")) { match ->
            EMOJI_MAP[match.groupValues[1]] ?: ""
        }

    private fun normalizeWhitespace(text: String): String =
        text
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    companion object {
        private val EMOJI_MAP =
            mapOf(
                "white_check_mark" to "\u2713",
                "thumbsup" to "\uD83D\uDC4D",
                "thumbsdown" to "\uD83D\uDC4E",
                "heavy_check_mark" to "\u2714\uFE0F",
                "x" to "\u274C",
                "warning" to "\u26A0\uFE0F",
                "bulb" to "\uD83D\uDCA1",
                "star" to "\u2B50",
                "rocket" to "\uD83D\uDE80",
                "fire" to "\uD83D\uDD25",
                "heart" to "\u2764\uFE0F",
                "ok" to "OK",
                "yes" to "Tak",
                "no" to "Nie",
            )
    }
}
