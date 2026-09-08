package io.vedra.infrastructure.slack.exception

class SlackNotInstalledException(
    val teamId: String,
) : RuntimeException("Vedra nie jest zainstalowana na workspace $teamId")
