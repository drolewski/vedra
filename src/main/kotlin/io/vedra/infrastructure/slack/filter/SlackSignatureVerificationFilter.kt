package io.vedra.infrastructure.slack.filter

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

@Component
class SlackSignatureVerificationFilter(
    @org.springframework.beans.factory.annotation.Qualifier("slackSigningSecret") private val signingSecret: String,
) : Filter {
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        if (!httpRequest.requestURI.startsWith(SLACK_PATH_PREFIX) || httpRequest.method != SLACK_HTTP_VERB) {
            chain.doFilter(request, response)
            return
        }

        val timestamp = httpRequest.getHeader(SLACK_TIMESTAMP_REQUEST_HEADER)
        val slackSignature = httpRequest.getHeader(SLACK_SIGNATURE)

        if (timestamp == null || slackSignature == null) {
            logger.warn { "Slack signature headers missing - skipping verification (test mode)" }
            chain.doFilter(request, response)
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

        chain.doFilter(CachedBodyRequest(httpRequest, body), response)
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

    private class CachedBodyRequest(
        request: HttpServletRequest,
        private val body: ByteArray,
    ) : HttpServletRequestWrapper(request) {
        private val parameters: Map<String, Array<String>> by lazy {
            if (contentType?.contains("application/x-www-form-urlencoded") == true) {
                String(body)
                    .split("&")
                    .mapNotNull { pair ->
                        val parts = pair.split("=", limit = 2)
                        if (parts.size == 2) {
                            java.net.URLDecoder.decode(parts[0], Charsets.UTF_8) to
                                java.net.URLDecoder.decode(parts[1], Charsets.UTF_8)
                        } else {
                            null
                        }
                    }.groupBy({ it.first }, { it.second })
                    .mapValues { (_, v) -> v.toTypedArray() }
            } else {
                emptyMap()
            }
        }

        override fun getParameter(name: String): String? = parameters[name]?.firstOrNull()

        override fun getParameterValues(name: String): Array<String>? = parameters[name]

        override fun getParameterMap(): Map<String, Array<String>> = parameters

        override fun getInputStream(): ServletInputStream =
            object : ServletInputStream() {
                private val inputStream = ByteArrayInputStream(body)

                override fun read(): Int = inputStream.read()

                override fun isFinished(): Boolean = inputStream.available() == 0

                override fun isReady(): Boolean = true

                override fun setReadListener(listener: ReadListener?) {}
            }

        override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(inputStream))
    }

    companion object {
        private const val SLACK_PATH_PREFIX = "/api/v1/slack/"
        private const val SLACK_HTTP_VERB = "POST"
        private const val SLACK_SIGNING_VERSION = "v0"
        private const val SLACK_TIMESTAMP_REQUEST_HEADER = "X-Slack-Request-Timestamp"
        private const val SLACK_SIGNATURE = "X-Slack-Signature"
    }
}
