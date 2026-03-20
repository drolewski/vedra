package io.vedra.infrastructure.slack.formatter

interface SlackBlockBuilder {
    fun build(): List<Map<String, Any>>
}
