package io.vedra.infrastructure.slack.service.command.help

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelpCommandHandlerTest {
    private val handler = HelpCommandHandler()

    @Test
    fun `help lists only status config help`() {
        val text = handler.handle(HelpCommand())

        assertTrue(text.contains("/vedra status"))
        assertTrue(text.contains("/vedra config"))
        assertTrue(text.contains("/vedra help"))
    }

    @Test
    fun `help hides legacy commands`() {
        val text = handler.handle(HelpCommand())

        assertFalse(text.contains("/vedra create"))
        assertFalse(text.contains("/vedra list"))
        assertFalse(text.contains("link"))
        assertFalse(text.contains("unlink"))
    }
}
