package io.vedra.infrastructure.slack.service.command

import io.vedra.infrastructure.slack.service.command.config.ConfigCommand
import kotlin.test.Test
import kotlin.test.assertTrue

class SlackCommandParserTest {
    private val parser = SlackCommandParser()

    @Test
    fun `config parses to ConfigCommand`() {
        val result = parser.parse("config", "C1", "T1", "TR1")

        assertTrue(result is ConfigCommand)
    }

    @Test
    fun `unknown action falls back to help`() {
        val result = parser.parse("xyz", "C1", "T1", "TR1")

        assertTrue(result is io.vedra.infrastructure.slack.service.command.help.HelpCommand)
    }
}
