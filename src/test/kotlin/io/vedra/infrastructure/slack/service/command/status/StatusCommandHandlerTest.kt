package io.vedra.infrastructure.slack.service.command.status

import io.mockk.every
import io.mockk.mockk
import io.vedra.domain.model.Tenant
import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.slack.service.SlackTenantResolver
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusCommandHandlerTest {
    private val tenantProjectService: TenantProjectService = mockk()
    private val tenantResolver: SlackTenantResolver = mockk()
    private val handler = StatusCommandHandler(tenantProjectService, tenantResolver)

    private val tenantId = UUID.randomUUID()
    private val testTenant = Tenant(id = tenantId, name = "Test")

    init {
        every { tenantResolver.withTenant(any(), any()) } answers {
            secondArg<(Tenant) -> String>()(testTenant)
        }
    }

    @Test
    fun `linked status shows full configuration`() {
        every { tenantProjectService.getChannelStatus("C1", tenantId) } returns
            TenantProjectService.ChannelStatus("C1", "P1", "Opis", "IT", true)

        val text = handler.handle(StatusCommand("C1", "T1"))

        assertTrue(text.contains("P1"))
        assertTrue(text.contains("Opis"))
        assertTrue(text.contains("IT"))
        assertTrue(text.contains("/vedra config"))
    }

    @Test
    fun `unlinked status invites config without legacy commands`() {
        every { tenantProjectService.getChannelStatus("C1", tenantId) } returns
            TenantProjectService.ChannelStatus("C1", null, null, null, false)

        val text = handler.handle(StatusCommand("C1", "T1"))

        assertTrue(text.contains("/vedra config"))
        assertFalse(text.contains("/vedra create"))
        assertFalse(text.contains("/vedra list"))
        assertFalse(text.contains("link"))
    }
}
