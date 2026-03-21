package io.vedra.infrastructure.slack.handler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.model.Tenant
import io.vedra.domain.service.TenantProjectService
import io.vedra.domain.service.TenantService
import io.vedra.infrastructure.database.outbox.OutboxEvent
import io.vedra.infrastructure.outbox.service.OutboxService
import io.vedra.infrastructure.slack.model.SlackEvent
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageEventHandlerTest {
    private val outboxService: OutboxService = mockk(relaxed = true)
    private val tenantProjectService: TenantProjectService = mockk()
    private val tenantService: TenantService = mockk()
    private val handler = MessageEventHandler(outboxService, tenantProjectService, tenantService)

    private val testTenant =
        Tenant(
            id = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"),
            name = "Test Tenant",
        )

    init {
        every { tenantProjectService.getProjectIdsForChannel(any(), any()) } returns listOf(null)
        every { tenantService.getDefaultTenant() } returns testTenant
        every { tenantService.resolveByExternalId(any()) } returns testTenant
    }

    @Test
    fun `should support message event type`() {
        val event =
            SlackEvent(
                eventId = "Ev123",
                type = "message",
                subtype = null,
                botId = null,
                text = "hello",
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        assertTrue(handler.supports(event))
    }

    @Test
    fun `should not support non-message event type`() {
        val event =
            SlackEvent(
                eventId = "Ev123",
                type = "reaction_added",
                subtype = null,
                botId = null,
                text = "hello",
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        assertFalse(handler.supports(event))
    }

    @Test
    fun `should save event to outbox`() {
        val event =
            SlackEvent(
                eventId = "Ev123",
                type = "message",
                subtype = null,
                botId = null,
                text = "Jakie jest prawo Ohma?",
                channel = "C456",
                threadTs = "1234567890.123456",
                workspaceId = "T123",
            )

        every {
            outboxService.saveEvent(
                eventType = "SLACK_MESSAGE_ANSWER",
                aggregateId = "Ev123",
                payload = any(),
            )
        } returns OutboxEvent(eventType = "SLACK_MESSAGE_ANSWER", payload = emptyMap())

        handler.handle(event)

        verify {
            outboxService.saveEvent(
                eventType = "SLACK_MESSAGE_ANSWER",
                aggregateId = "Ev123",
                payload =
                    match {
                        it["channel"] == "C456" &&
                            it["threadTs"] == "1234567890.123456" &&
                            it["question"] == "Jakie jest prawo Ohma?" &&
                            it["tenantId"] == testTenant.id.toString()
                    },
            )
        }
    }

    @Test
    fun `should not save event when channel is null`() {
        val event =
            SlackEvent(
                eventId = "Ev123",
                type = "message",
                subtype = null,
                botId = null,
                text = "question",
                channel = null,
                threadTs = "1234567890.123456",
            )

        handler.handle(event)

        verify(exactly = 0) { outboxService.saveEvent(any(), any(), any()) }
    }

    @Test
    fun `should not save event when threadTs is null`() {
        val event =
            SlackEvent(
                eventId = "Ev123",
                type = "message",
                subtype = null,
                botId = null,
                text = "question",
                channel = "C456",
                threadTs = null,
            )

        handler.handle(event)

        verify(exactly = 0) { outboxService.saveEvent(any(), any(), any()) }
    }

    @Test
    fun `should not save event when text is null`() {
        val event =
            SlackEvent(
                eventId = "Ev123",
                type = "message",
                subtype = null,
                botId = null,
                text = null,
                channel = "C456",
                threadTs = "1234567890.123456",
            )

        handler.handle(event)

        verify(exactly = 0) { outboxService.saveEvent(any(), any(), any()) }
    }
}
