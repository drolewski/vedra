package io.vedra.infrastructure.slack.service.command.help

import io.vedra.infrastructure.slack.service.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class HelpCommandHandler : CommandHandler<HelpCommand> {
    override fun supports(command: Any): Boolean = command is HelpCommand

    override fun handle(command: HelpCommand): String =
        """
        *Komendy Vedra:*
        • `/vedra status` - Pokaż konfigurację przestrzeni tego kanału
        • `/vedra config` - Edytuj nazwę, opis i branżę przestrzeni tego kanału
        • `/vedra help` - Pokaż tę pomoc
        """.trimIndent()
}
