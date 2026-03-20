package io.vedra.infrastructure.slack.service.command

interface CommandHandler<T> {
    fun supports(command: Any): Boolean

    fun handle(command: T): String
}
