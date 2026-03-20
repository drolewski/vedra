package io.vedra.infrastructure.slack.service.command.help

import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class HelpCommandHandler : CommandHandler<HelpCommand> {
    override fun supports(command: Any): Boolean = command is HelpCommand

    override fun handle(command: HelpCommand): String =
        """
        *Komendy Vedra:*
        • `/vedra link <projekt>` - Powiąż kanał z projektem
        • `/vedra unlink` - Odłącz kanał od projektu
        • `/vedra list` - Pokaż dostępne projekty
        • `/vedra status` - Pokaż aktualny kontekst kanału
        """.trimIndent()
}
