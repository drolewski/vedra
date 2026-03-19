package io.vedra.infrastructure.database.outbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutboxEventTest {
    @Test
    fun `should create event with default PENDING status`() {
        val event =
            OutboxEvent(
                eventType = "TEST_EVENT",
                payload = mapOf("key" to "value"),
            )

        assertEquals(OutboxStatus.PENDING, event.status)
        assertEquals(0, event.retryCount)
        assertEquals(3, event.maxRetries)
        assertNull(event.processedAt)
        assertNull(event.errorMessage)
    }

    @Test
    fun `should mark as processing`() {
        val event = OutboxEvent(eventType = "TEST_EVENT", payload = emptyMap())

        event.markAsProcessing()

        assertEquals(OutboxStatus.PROCESSING, event.status)
    }

    @Test
    fun `should mark as completed`() {
        val event = OutboxEvent(eventType = "TEST_EVENT", payload = emptyMap())

        event.markAsCompleted()

        assertEquals(OutboxStatus.COMPLETED, event.status)
        assertNotNull(event.processedAt)
    }

    @Test
    fun `should mark as failed with error message`() {
        val event = OutboxEvent(eventType = "TEST_EVENT", payload = emptyMap())
        val errorMessage = "Something went wrong"

        event.markAsFailed(errorMessage)

        assertEquals(OutboxStatus.FAILED, event.status)
        assertEquals(errorMessage, event.errorMessage)
        assertEquals(1, event.retryCount)
    }

    @Test
    fun `should increment retry count on each failure`() {
        val event = OutboxEvent(eventType = "TEST_EVENT", payload = emptyMap())

        event.markAsFailed("Error 1")
        event.markAsFailed("Error 2")

        assertEquals(2, event.retryCount)
        assertEquals("Error 2", event.errorMessage)
    }

    @Test
    fun `canRetry should return true when retry count is less than max retries`() {
        val event = OutboxEvent(eventType = "TEST_EVENT", payload = emptyMap())

        assertTrue(event.canRetry())

        event.markAsFailed("Error")
        assertTrue(event.canRetry())

        event.markAsFailed("Error")
        assertTrue(event.canRetry())
    }

    @Test
    fun `canRetry should return false when retry count equals max retries`() {
        val event = OutboxEvent(eventType = "TEST_EVENT", payload = emptyMap())

        repeat(3) { event.markAsFailed("Error") }

        assertFalse(event.canRetry())
        assertEquals(3, event.retryCount)
    }

    @Test
    fun `equals should compare by id`() {
        val id = java.util.UUID.randomUUID()
        val event1 = OutboxEvent(id = id, eventType = "TEST", payload = emptyMap())
        val event2 = OutboxEvent(id = id, eventType = "TEST", payload = emptyMap())
        val event3 = OutboxEvent(eventType = "TEST", payload = emptyMap())

        assertEquals(event1, event2)
        assertFalse(event1 == event3)
    }

    @Test
    fun `hashCode should be based on id`() {
        val id = java.util.UUID.randomUUID()
        val event1 = OutboxEvent(id = id, eventType = "TEST", payload = emptyMap())
        val event2 = OutboxEvent(id = id, eventType = "TEST", payload = emptyMap())

        assertEquals(event1.hashCode(), event2.hashCode())
    }
}
