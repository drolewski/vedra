package io.vedra.infrastructure.slack.service.command

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class CommandDispatcher(
    private val handlers: List<CommandHandler<*>>,
) {
    fun dispatch(command: Any): String {
        val handler = handlers.firstOrNull { it.supports(command) }

        if (handler == null) {
            logger.warn { "No handler found for command: ${command::class.simpleName}" }
            return UNKNOWN_COMMAND_MESSAGE
        }

        logger.debug { "Dispatching command to ${handler::class.simpleName}" }

        @Suppress("UNCHECKED_CAST")
        return (handler as CommandHandler<Any>).handle(command)
    }

    companion object {
        const val UNKNOWN_COMMAND_MESSAGE = "Nieznana komenda. Użyj `/vedra help` aby zobaczyć dostępne komendy."
    }
}
