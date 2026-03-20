package io.vedra.infrastructure.slack.handler

import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.service.FeedbackService
import io.vedra.infrastructure.slack.model.SlackEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReactionEventHandlerTest {
    private val feedbackService: FeedbackService = mockk(relaxed = true)
    private val handler = ReactionEventHandler(feedbackService)

    @Test
    fun `should support reaction_added event type`() {
        val event =
            SlackEvent(
                eventId = null,
                type = "reaction_added",
                subtype = null,
                botId = null,
                text = null,
                channel = "C123",
                threadTs = "123.0",
            )
        assertTrue(handler.supports(event))
    }

    @Test
    fun `should not support message event type`() {
        val event =
            SlackEvent(eventId = null, type = "message", subtype = null, botId = null, text = "hello", channel = "C123", threadTs = "123.0")
        assertFalse(handler.supports(event))
    }

    @Test
    fun `should apply positive feedback on thumbsup`() {
        val event =
            SlackEvent(
                eventId = null,
                type = "reaction_added",
                subtype = null,
                botId = null,
                text = null,
                channel = "C123",
                threadTs = "1234567890.123456",
                reaction = "+1",
            )

        handler.handle(event)

        verify { feedbackService.applyPositiveFeedback("1234567890.123456") }
    }

    @Test
    fun `should apply negative feedback on thumbsdown`() {
        val event =
            SlackEvent(
                eventId = null,
                type = "reaction_added",
                subtype = null,
                botId = null,
                text = null,
                channel = "C123",
                threadTs = "1234567890.123456",
                reaction = "-1",
            )

        handler.handle(event)

        verify { feedbackService.applyNegativeFeedback("1234567890.123456") }
    }

    @Test
    fun `should not apply feedback for other reactions`() {
        val event =
            SlackEvent(
                eventId = null,
                type = "reaction_added",
                subtype = null,
                botId = null,
                text = null,
                channel = "C123",
                threadTs = "1234567890.123456",
                reaction = "heart",
            )

        handler.handle(event)

        verify(exactly = 0) { feedbackService.applyPositiveFeedback(any()) }
        verify(exactly = 0) { feedbackService.applyNegativeFeedback(any()) }
    }

    @Test
    fun `should do nothing when threadTs is null`() {
        val event =
            SlackEvent(
                eventId = null,
                type = "reaction_added",
                subtype = null,
                botId = null,
                text = null,
                channel = "C123",
                threadTs = null,
                reaction = "+1",
            )

        handler.handle(event)

        verify(exactly = 0) { feedbackService.applyPositiveFeedback(any()) }
    }

    @Test
    fun `should do nothing when reaction is null`() {
        val event =
            SlackEvent(
                eventId = null,
                type = "reaction_added",
                subtype = null,
                botId = null,
                text = null,
                channel = "C123",
                threadTs = "123.0",
                reaction = null,
            )

        handler.handle(event)

        verify(exactly = 0) { feedbackService.applyPositiveFeedback(any()) }
        verify(exactly = 0) { feedbackService.applyNegativeFeedback(any()) }
    }
}
