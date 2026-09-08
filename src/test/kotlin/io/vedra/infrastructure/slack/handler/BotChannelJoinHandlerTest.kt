package io.vedra.infrastructure.slack.handler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.model.Tenant
import io.vedra.domain.model.TenantProject
import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.slack.model.SlackEvent
import io.vedra.infrastructure.slack.port.SlackClientPort
import io.vedra.infrastructure.slack.service.SlackMessageService
import io.vedra.infrastructure.slack.service.SlackTenantResolver
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BotChannelJoinHandlerTest {
    private val tenantProjectService: TenantProjectService = mockk()
    private val tenantResolver: SlackTenantResolver = mockk()
    private val slackClient: SlackClientPort = mockk()
    private val messageService: SlackMessageService = mockk(relaxed = true)
    private val handler = BotChannelJoinHandler(tenantProjectService, tenantResolver, slackClient, messageService)

    private val tenantId = UUID.randomUUID()
    private val testTenant = Tenant(id = tenantId, name = "Test")

    init {
        every { tenantResolver.withTenantResult<Unit>(any(), any()) } answers {
            secondArg<(Tenant) -> Unit>()(testTenant)
        }
        every { slackClient.getChannelName(any()) } returns "general"
    }

    private fun joinEvent() =
        SlackEvent(
            eventId = "Ev1",
            type = "member_joined_channel",
            subtype = null,
            botId = null,
            text = null,
            channel = "C1",
            threadTs = null,
            userId = "U1",
            workspaceId = "T1",
        )

    @Test
    fun `supports member_joined_channel only`() {
        assertTrue(handler.supports(joinEvent()))
        assertFalse(handler.supports(joinEvent().copy(type = "message")))
    }

    @Test
    fun `created space sends welcome once`() {
        val project = TenantProject(id = UUID.randomUUID(), tenantId = tenantId, name = "kanał-general")
        every { tenantProjectService.ensureChannelSpace(tenantId, "C1", "general") } returns
            TenantProjectService.ChannelSpaceResult.Created(project)

        handler.handle(joinEvent())

        verify { tenantProjectService.ensureChannelSpace(tenantId, "C1", "general") }
        verify { messageService.sendChannelMessage("C1", match { it.contains("kanał-general") }) }
    }

    @Test
    fun `already linked stays silent`() {
        every { tenantProjectService.ensureChannelSpace(tenantId, "C1", "general") } returns
            TenantProjectService.ChannelSpaceResult.AlreadyLinked(null)

        handler.handle(joinEvent())

        verify(exactly = 0) { messageService.sendChannelMessage(any(), any()) }
    }
}
