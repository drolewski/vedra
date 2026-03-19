package io.vedra.domain.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.model.SlackEvent
import io.vedra.domain.model.SlackEventFilter
import io.vedra.domain.model.SlackEventHandler
import kotlin.test.Test

class SlackEventProcessorTest {
    private val filter: SlackEventFilter = mockk()
    private val handler: SlackEventHandler = mockk(relaxed = true)
    private val processor = SlackEventProcessor(listOf(filter), listOf(handler))

    private val userEvent =
        SlackEvent(
            type = "message",
            subtype = null,
            botId = null,
            text = "hello",
            channel = "C456",
            threadTs = "1234567890.123456",
        )

    @Test
    fun `should process event when all filters pass`() {
        every { filter.shouldProcess(userEvent) } returns true
        every { handler.supports(userEvent) } returns true

        processor.process(userEvent)

        verify { handler.handle(userEvent) }
    }

    @Test
    fun `should not process event when filter rejects`() {
        every { filter.shouldProcess(userEvent) } returns false

        processor.process(userEvent)

        verify(exactly = 0) { handler.handle(any()) }
    }

    @Test
    fun `should not process event when no handler supports`() {
        every { filter.shouldProcess(userEvent) } returns true
        every { handler.supports(userEvent) } returns false

        processor.process(userEvent)

        verify(exactly = 0) { handler.handle(any()) }
    }

    @Test
    fun `should process event with multiple filters all passing`() {
        val secondFilter: SlackEventFilter = mockk()
        val multiFilterProcessor = SlackEventProcessor(listOf(filter, secondFilter), listOf(handler))

        every { filter.shouldProcess(userEvent) } returns true
        every { secondFilter.shouldProcess(userEvent) } returns true
        every { handler.supports(userEvent) } returns true

        multiFilterProcessor.process(userEvent)

        verify { handler.handle(userEvent) }
    }

    @Test
    fun `should not process event when one of multiple filters rejects`() {
        val secondFilter: SlackEventFilter = mockk()
        val multiFilterProcessor = SlackEventProcessor(listOf(filter, secondFilter), listOf(handler))

        every { filter.shouldProcess(userEvent) } returns true
        every { secondFilter.shouldProcess(userEvent) } returns false

        multiFilterProcessor.process(userEvent)

        verify(exactly = 0) { handler.handle(any()) }
    }

    @Test
    fun `should use first matching handler`() {
        val secondHandler: SlackEventHandler = mockk(relaxed = true)

        val multiHandlerProcessor = SlackEventProcessor(listOf(filter), listOf(handler, secondHandler))

        every { filter.shouldProcess(userEvent) } returns true
        every { handler.supports(userEvent) } returns false
        every { secondHandler.supports(userEvent) } returns true

        multiHandlerProcessor.process(userEvent)

        verify(exactly = 0) { handler.handle(any()) }
        verify { secondHandler.handle(userEvent) }
    }

    @Test
    fun `should not process bot message due to filter`() {
        val botEvent =
            SlackEvent(
                type = "message",
                subtype = "bot_message",
                botId = null,
                text = "hello",
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        val botFilter =
            io.vedra.infrastructure.slack.filter
                .BotMessageFilter()
        val botProcessor = SlackEventProcessor(listOf(botFilter), listOf(handler))

        botProcessor.process(botEvent)

        verify(exactly = 0) { handler.handle(any()) }
    }
}
