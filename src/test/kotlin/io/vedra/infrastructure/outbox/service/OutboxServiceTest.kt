package io.vedra.infrastructure.outbox.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.database.outbox.OutboxStatus
import io.vedra.infrastructure.outbox.repository.OutboxEventRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class OutboxServiceTest {
    private val outboxEventRepository: OutboxEventRepository = mockk(relaxed = true)
    private val outboxService = OutboxService(outboxEventRepository)

    @Test
    fun `saveEvent should create new event when no existing event with same aggregateId`() {
        val eventType = "TEST_EVENT"
        val aggregateId = "agg-123"
        val payload = mapOf("key" to "value")

        every { outboxEventRepository.findByAggregateIdAndStatus(aggregateId, OutboxStatus.PENDING) } returns emptyList()
        every { outboxEventRepository.save<OutboxEvent>(any()) } answers { firstArg() }

        val result = outboxService.saveEvent(eventType, aggregateId, payload)

        assertEquals(eventType, result.eventType)
        assertEquals(aggregateId, result.aggregateId)
        assertEquals(payload, result.payload)
        assertEquals(OutboxStatus.PENDING, result.status)

        verify { outboxEventRepository.save(any()) }
    }

    @Test
    fun `saveEvent should return existing event when event with same aggregateId exists`() {
        val aggregateId = "agg-123"
        val existingEvent =
            OutboxEvent(
                id = UUID.randomUUID(),
                eventType = "TEST_EVENT",
                aggregateId = aggregateId,
                payload = mapOf("existing" to "data"),
            )

        every {
            outboxEventRepository.findByAggregateIdAndStatus(aggregateId, OutboxStatus.PENDING)
        } returns listOf(existingEvent)

        val result = outboxService.saveEvent("NEW_EVENT", aggregateId, mapOf("new" to "data"))

        assertSame(existingEvent, result)
        verify(exactly = 0) { outboxEventRepository.save(any()) }
    }

    @Test
    fun `saveEvent should create event when aggregateId is null`() {
        every { outboxEventRepository.save<OutboxEvent>(any()) } answers { firstArg() }

        val result = outboxService.saveEvent("TEST_EVENT", null, mapOf("key" to "value"))

        assertEquals("TEST_EVENT", result.eventType)
        assertEquals(null, result.aggregateId)

        verify { outboxEventRepository.save(any()) }
    }

    @Test
    fun `getPendingEvents should delegate to repository`() {
        val events =
            listOf(
                OutboxEvent(eventType = "EVENT_1", payload = emptyMap()),
                OutboxEvent(eventType = "EVENT_2", payload = emptyMap()),
            )

        every { outboxEventRepository.findPendingEvents(10) } returns events

        val result = outboxService.getPendingEvents(10)

        assertEquals(events, result)
        verify { outboxEventRepository.findPendingEvents(10) }
    }

    @Test
    fun `markAsProcessing should update event status`() {
        val event = OutboxEvent(eventType = "TEST", payload = emptyMap())
        every { outboxEventRepository.save<OutboxEvent>(any()) } answers { firstArg() }

        outboxService.markAsProcessing(event)

        assertEquals(OutboxStatus.PROCESSING, event.status)
        verify { outboxEventRepository.save(event) }
    }

    @Test
    fun `markAsCompleted should update event status and processedAt`() {
        val event = OutboxEvent(eventType = "TEST", payload = emptyMap())
        every { outboxEventRepository.save<OutboxEvent>(any()) } answers { firstArg() }

        outboxService.markAsCompleted(event)

        assertEquals(OutboxStatus.COMPLETED, event.status)
        assertNotNull(event.processedAt)
        verify { outboxEventRepository.save(event) }
    }

    @Test
    fun `markAsFailed should update event status, errorMessage and increment retryCount`() {
        val event = OutboxEvent(eventType = "TEST", payload = emptyMap())
        every { outboxEventRepository.save<OutboxEvent>(any()) } answers { firstArg() }

        outboxService.markAsFailed(event, "Test error")

        assertEquals(OutboxStatus.FAILED, event.status)
        assertEquals("Test error", event.errorMessage)
        assertEquals(1, event.retryCount)
        verify { outboxEventRepository.save(event) }
    }

    @Test
    fun `deleteCompletedOlderThan should delegate to repository`() {
        val olderThan = Instant.now().minus(java.time.Duration.ofDays(7))

        every { outboxEventRepository.deleteCompletedOlderThan(olderThan) } returns 5

        val result = outboxService.deleteCompletedOlderThan(olderThan)

        assertEquals(5, result)
        verify { outboxEventRepository.deleteCompletedOlderThan(olderThan) }
    }
}
