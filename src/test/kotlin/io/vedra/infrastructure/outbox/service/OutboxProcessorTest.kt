package io.vedra.infrastructure.outbox.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.outbox.handler.OutboxEventHandler
import java.util.UUID
import kotlin.test.Test

class OutboxProcessorTest {
    private val outboxService: OutboxService = mockk(relaxed = true)
    private val handler: OutboxEventHandler = mockk(relaxed = true)
    private val processor = OutboxProcessor(outboxService, listOf(handler), 7)

    @Test
    fun `processPendingEvents should do nothing when no pending events`() {
        every { outboxService.getPendingEvents(10) } returns emptyList()

        processor.processPendingEvents()

        verify(exactly = 0) { outboxService.markAsProcessing(any()) }
        verify(exactly = 0) { handler.handle(any()) }
    }

    @Test
    fun `processPendingEvents should process each pending event`() {
        val event1 =
            OutboxEvent(
                id = UUID.randomUUID(),
                eventType = "TEST_TYPE",
                payload = mapOf("key" to "value1"),
            )
        val event2 =
            OutboxEvent(
                id = UUID.randomUUID(),
                eventType = "TEST_TYPE",
                payload = mapOf("key" to "value2"),
            )

        every { outboxService.getPendingEvents(10) } returns listOf(event1, event2)
        every { handler.supports("TEST_TYPE") } returns true

        processor.processPendingEvents()

        verify(exactly = 2) { outboxService.markAsProcessing(any()) }
        verify(exactly = 2) { handler.handle(any()) }
        verify(exactly = 2) { outboxService.markAsCompleted(any()) }
    }

    @Test
    fun `processPendingEvents should mark as failed when no handler found`() {
        val event =
            OutboxEvent(
                id = UUID.randomUUID(),
                eventType = "UNKNOWN_TYPE",
                payload = emptyMap(),
            )

        every { outboxService.getPendingEvents(10) } returns listOf(event)
        every { handler.supports("UNKNOWN_TYPE") } returns false

        processor.processPendingEvents()

        verifyOrder {
            outboxService.markAsProcessing(event)
            outboxService.markAsFailed(event, "No handler found for event type: UNKNOWN_TYPE")
        }
        verify(exactly = 0) { handler.handle(any()) }
    }

    @Test
    fun `processPendingEvents should mark as failed when handler throws exception`() {
        val event =
            OutboxEvent(
                id = UUID.randomUUID(),
                eventType = "TEST_TYPE",
                payload = emptyMap(),
            )

        every { outboxService.getPendingEvents(10) } returns listOf(event)
        every { handler.supports("TEST_TYPE") } returns true
        every { handler.handle(event) } throws RuntimeException("Handler error")

        processor.processPendingEvents()

        verifyOrder {
            outboxService.markAsProcessing(event)
            outboxService.markAsFailed(event, "Handler error")
        }
    }

    @Test
    fun `processPendingEvents should select correct handler from multiple handlers`() {
        val slackHandler: OutboxEventHandler = mockk(relaxed = true)
        val emailHandler: OutboxEventHandler = mockk(relaxed = true)
        val multiHandlerProcessor = OutboxProcessor(outboxService, listOf(slackHandler, emailHandler), 7)

        val event =
            OutboxEvent(
                id = UUID.randomUUID(),
                eventType = "EMAIL_EVENT",
                payload = emptyMap(),
            )

        every { outboxService.getPendingEvents(10) } returns listOf(event)
        every { slackHandler.supports("EMAIL_EVENT") } returns false
        every { emailHandler.supports("EMAIL_EVENT") } returns true

        multiHandlerProcessor.processPendingEvents()

        verify(exactly = 0) { slackHandler.handle(any()) }
        verify(exactly = 1) { emailHandler.handle(event) }
    }

    @Test
    fun `cleanupCompletedEvents should delete old completed events`() {
        every { outboxService.deleteCompletedOlderThan(any()) } returns 10

        processor.cleanupCompletedEvents()

        verify { outboxService.deleteCompletedOlderThan(any()) }
    }

    @Test
    fun `cleanupCompletedEvents should not log when no events deleted`() {
        every { outboxService.deleteCompletedOlderThan(any()) } returns 0

        processor.cleanupCompletedEvents()

        verify { outboxService.deleteCompletedOlderThan(any()) }
    }
}
