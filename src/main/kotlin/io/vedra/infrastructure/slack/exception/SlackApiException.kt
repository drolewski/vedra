package io.vedra.infrastructure.slack.exception

class SlackApiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class SlackPayloadException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
