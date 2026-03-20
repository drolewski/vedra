package io.vedra.infrastructure.slack.model.dto

data class SlackChallengePayload(
    val challenge: String,
) {
    companion object {
        fun fromRawBody(body: Map<String, Any>): SlackChallengePayload {
            val challenge =
                body["challenge"] as? String
                    ?: throw IllegalArgumentException("Missing 'challenge' field")
            return SlackChallengePayload(challenge = challenge)
        }
    }
}
