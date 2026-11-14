package io.vedra.infrastructure.slack.filter

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test

class SlackSignatureVerificationFilterTest {
    private val signingSecret = "test-signing-secret"
    private val filter = SlackSignatureVerificationFilter(signingSecret)

    private fun computeSignature(
        timestamp: String,
        body: String,
    ): String {
        val signatureBaseString = "v0:$timestamp:$body"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(signingSecret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(signatureBaseString.toByteArray())
        return "v0=" + hash.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `should pass through non-slack endpoint`() {
        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val chain = mockk<FilterChain>(relaxed = true)

        every { request.requestURI } returns "/api/v1/chat"
        every { request.method } returns "POST"

        filter.doFilter(request, response, chain)

        verify { chain.doFilter(request, response) }
    }

    @Test
    fun `should pass through GET requests`() {
        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val chain = mockk<FilterChain>(relaxed = true)

        every { request.requestURI } returns "/api/v1/slack/events"
        every { request.method } returns "GET"

        filter.doFilter(request, response, chain)

        verify { chain.doFilter(request, response) }
    }

    @Test
    fun `should return 401 when timestamp header is missing`() {
        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val chain = mockk<FilterChain>(relaxed = true)

        every { request.requestURI } returns "/api/v1/slack/events"
        every { request.method } returns "POST"
        every { request.getHeader("X-Slack-Request-Timestamp") } returns null
        every { request.getHeader("X-Slack-Signature") } returns "v0=abc"

        filter.doFilter(request, response, chain)

        verify { response.status = 401 }
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `should return 401 when signature header is missing`() {
        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val chain = mockk<FilterChain>(relaxed = true)

        every { request.requestURI } returns "/api/v1/slack/events"
        every { request.method } returns "POST"
        every { request.getHeader("X-Slack-Request-Timestamp") } returns "1234567890"
        every { request.getHeader("X-Slack-Signature") } returns null

        filter.doFilter(request, response, chain)

        verify { response.status = 401 }
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `should return 401 when timestamp is too old`() {
        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val chain = mockk<FilterChain>()

        val oldTimestamp =
            Instant
                .now()
                .minusSeconds(600)
                .epochSecond
                .toString()

        every { request.requestURI } returns "/api/v1/slack/events"
        every { request.method } returns "POST"
        every { request.getHeader("X-Slack-Request-Timestamp") } returns oldTimestamp
        every { request.getHeader("X-Slack-Signature") } returns "v0=abc"

        filter.doFilter(request, response, chain)

        verify { response.status = 401 }
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `should return 401 when signature is invalid`() {
        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val chain = mockk<FilterChain>()

        val timestamp = Instant.now().epochSecond.toString()
        val body = """{"challenge":"test"}"""
        val bodyBytes = body.toByteArray()
        val inputStream = java.io.ByteArrayInputStream(bodyBytes)

        every { request.requestURI } returns "/api/v1/slack/events"
        every { request.method } returns "POST"
        every { request.getHeader("X-Slack-Request-Timestamp") } returns timestamp
        every { request.getHeader("X-Slack-Signature") } returns "v0=invalid_signature"
        every { request.inputStream } returns
            object : jakarta.servlet.ServletInputStream() {
                override fun read(): Int = inputStream.read()

                override fun isFinished(): Boolean = inputStream.available() == 0

                override fun isReady(): Boolean = true

                override fun setReadListener(listener: jakarta.servlet.ReadListener?) {}
            }

        filter.doFilter(request, response, chain)

        verify { response.status = 401 }
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `should pass through when signature is valid`() {
        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val chain = mockk<FilterChain>(relaxed = true)

        val timestamp = Instant.now().epochSecond.toString()
        val body = """{"challenge":"test"}"""
        val bodyBytes = body.toByteArray()
        val inputStream = java.io.ByteArrayInputStream(bodyBytes)
        val signature = computeSignature(timestamp, body)

        every { request.requestURI } returns "/api/v1/slack/events"
        every { request.method } returns "POST"
        every { request.getHeader("X-Slack-Request-Timestamp") } returns timestamp
        every { request.getHeader("X-Slack-Signature") } returns signature
        every { request.inputStream } returns
            object : jakarta.servlet.ServletInputStream() {
                override fun read(): Int = inputStream.read()

                override fun isFinished(): Boolean = inputStream.available() == 0

                override fun isReady(): Boolean = true

                override fun setReadListener(listener: jakarta.servlet.ReadListener?) {}
            }

        filter.doFilter(request, response, chain)

        verify { chain.doFilter(any(), response) }
        verify(exactly = 0) { response.status = any() }
    }

    @Test
    fun `should pass through when signature is valid with event payload`() {
        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val chain = mockk<FilterChain>(relaxed = true)

        val timestamp = Instant.now().epochSecond.toString()
        val body = """{"event":{"type":"message","text":"hello","channel":"C123","ts":"123.456"}}"""
        val bodyBytes = body.toByteArray()
        val inputStream = java.io.ByteArrayInputStream(bodyBytes)
        val signature = computeSignature(timestamp, body)

        every { request.requestURI } returns "/api/v1/slack/events"
        every { request.method } returns "POST"
        every { request.getHeader("X-Slack-Request-Timestamp") } returns timestamp
        every { request.getHeader("X-Slack-Signature") } returns signature
        every { request.inputStream } returns
            object : jakarta.servlet.ServletInputStream() {
                override fun read(): Int = inputStream.read()

                override fun isFinished(): Boolean = inputStream.available() == 0

                override fun isReady(): Boolean = true

                override fun setReadListener(listener: jakarta.servlet.ReadListener?) {}
            }

        filter.doFilter(request, response, chain)

        verify { chain.doFilter(any(), response) }
    }

    @Test
    fun `should reject malformed future and out of range timestamps`() {
        val now = Instant.now().epochSecond
        for (timestamp in listOf(
            "invalid",
            "",
            Long.MAX_VALUE.toString(),
            Long.MIN_VALUE.toString(),
            (now + 301).toString(),
            (now - 301).toString(),
        )) {
            val request = mockk<HttpServletRequest>()
            val response = mockk<HttpServletResponse>(relaxed = true)
            val chain = mockk<FilterChain>(relaxed = true)
            every { request.requestURI } returns "/api/v1/slack/events"
            every { request.method } returns "POST"
            every { request.getHeader("X-Slack-Request-Timestamp") } returns timestamp
            every { request.getHeader("X-Slack-Signature") } returns "v0=invalid"
            filter.doFilter(request, response, chain)
            verify { response.status = 401 }
            verify(exactly = 0) { chain.doFilter(any(), any()) }
        }
    }

    @Test
    fun `should reject Slack requests when signing secret is missing`() {
        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val chain = mockk<FilterChain>(relaxed = true)
        every { request.requestURI } returns "/api/v1/slack/events"
        every { request.method } returns "POST"
        every { request.getHeader("X-Slack-Request-Timestamp") } returns Instant.now().epochSecond.toString()
        every { request.getHeader("X-Slack-Signature") } returns "v0=invalid"
        SlackSignatureVerificationFilter("").doFilter(request, response, chain)
        verify { response.status = 401 }
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }
}
