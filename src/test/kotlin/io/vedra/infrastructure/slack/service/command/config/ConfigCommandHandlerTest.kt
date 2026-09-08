package io.vedra.infrastructure.slack.service.command.config

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.model.ProjectVisibility
import io.vedra.domain.model.Tenant
import io.vedra.domain.model.TenantProject
import io.vedra.domain.service.TenantProjectService
import io.vedra.infrastructure.slack.service.SlackModalService
import io.vedra.infrastructure.slack.service.SlackTenantResolver
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigCommandHandlerTest {
    private val modalService: SlackModalService = mockk(relaxed = true)
    private val tenantResolver: SlackTenantResolver = mockk()
    private val tenantProjectService: TenantProjectService = mockk()
    private val handler = ConfigCommandHandler(modalService, tenantResolver, tenantProjectService)

    private val tenantId = UUID.randomUUID()
    private val testTenant = Tenant(id = tenantId, name = "Test")

    init {
        every { tenantResolver.withTenant(any(), any()) } answers {
            secondArg<(Tenant) -> String>()(testTenant)
        }
    }

    @Test
    fun `linked private project opens config modal with prefill`() {
        val projectId = UUID.randomUUID()
        val project = TenantProject(id = projectId, tenantId = tenantId, name = "P1", description = "D", industry = "IT")
        every { tenantProjectService.getLinkedProject("C1", tenantId) } returns project

        val result = handler.handle(ConfigCommand("C1", "T1", "TR1"))

        assertEquals("", result)
        verify {
            modalService.openConfigModal("TR1", "C1", tenantId, "P1", "D", "IT", "T1")
        }
    }

    @Test
    fun `missing link auto-provisions space then opens modal`() {
        val projectId = UUID.randomUUID()
        val project = TenantProject(id = projectId, tenantId = tenantId, name = "ch-abc", visibility = ProjectVisibility.PRIVATE)
        every { tenantProjectService.getLinkedProject("C1", tenantId) } returns null
        every { tenantProjectService.ensureChannelSpace(tenantId, "C1", null) } returns
            TenantProjectService.ChannelSpaceResult.Created(project)

        val result = handler.handle(ConfigCommand("C1", "T1", "TR1"))

        assertEquals("", result)
        verify { tenantProjectService.ensureChannelSpace(tenantId, "C1", null) }
        verify { modalService.openConfigModal(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `public space is not editable`() {
        val projectId = UUID.randomUUID()
        every { tenantProjectService.getLinkedProject("C1", tenantId) } returns
            TenantProject(id = projectId, tenantId = tenantId, name = "Baza ogólna", visibility = ProjectVisibility.PUBLIC)

        val result = handler.handle(ConfigCommand("C1", "T1", "TR1"))

        assertTrue(result.contains("publicznej"))
        verify(exactly = 0) { modalService.openConfigModal(any(), any(), any(), any(), any(), any(), any()) }
    }
}
