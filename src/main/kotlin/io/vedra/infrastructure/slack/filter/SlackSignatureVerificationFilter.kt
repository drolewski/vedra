package io.vedra.infrastructure.slack.filter

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class SlackSignatureVerificationFilter(
    @Value($$"${slack.signing-secret}") private val signingSecret: String,
) : Filter {
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        if (httpRequest.requestURI != SLACK_EVENTS_ENDPOINT || httpRequest.method != SLACK_HTTP_VERB) {
            chain.doFilter(request, response)
            return
        }

        val timestamp = httpRequest.getHeader(SLACK_TIMESTAMP_REQUEST_HEADER)
        val slackSignature = httpRequest.getHeader(SLACK_SIGNATURE)

        if (timestamp == null || slackSignature == null) {
            httpResponse.status = HttpStatus.UNAUTHORIZED.value()
            return
        }

        if (isTimestampTooOld(timestamp)) {
            httpResponse.status = HttpStatus.UNAUTHORIZED.value()
            return
        }

        val body = httpRequest.inputStream.readBytes()
        val signatureBaseString = "$SLACK_SIGNING_VERSION:$timestamp:${String(body)}"
        val computedSignature = "$SLACK_SIGNING_VERSION=" + computeHmacSha256(signatureBaseString, signingSecret)

        if (computedSignature != slackSignature) {
            httpResponse.status = HttpStatus.UNAUTHORIZED.value()
            return
        }

        val wrappedRequest = CachedBodyHttpServletRequest(httpRequest, body)
        chain.doFilter(wrappedRequest, response)
    }

    private fun isTimestampTooOld(timestamp: String): Boolean {
        val requestTime = Instant.ofEpochSecond(timestamp.toLong())
        val now = Instant.now()
        val diff = java.time.Duration.between(requestTime, now)
        return diff.abs().toMinutes() > 5
    }

    private fun computeHmacSha256(
        data: String,
        key: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val SLACK_EVENTS_ENDPOINT = "/api/v1/slack/events"
        private const val SLACK_HTTP_VERB = "POST"
        private const val SLACK_SIGNING_VERSION = "v0"
        private const val SLACK_TIMESTAMP_REQUEST_HEADER = "X-Slack-Request-Timestamp"
        private const val SLACK_SIGNATURE = "X-Slack-Signature"
    }
}
