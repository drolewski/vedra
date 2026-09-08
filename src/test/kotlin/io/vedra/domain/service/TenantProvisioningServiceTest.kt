package io.vedra.domain.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.vedra.domain.event.TenantProvisionedEvent
import io.vedra.domain.model.Tenant
import io.vedra.domain.model.TenantProject
import io.vedra.domain.repository.tenant.TenantRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TenantProvisioningServiceTest {
    private val tenantRepository: TenantRepository = mockk()
    private val tenantProjectService: TenantProjectService = mockk()
    private val events: ApplicationEventPublisher = mockk(relaxed = true)
    private val service = TenantProvisioningService(tenantRepository, tenantProjectService, events)

    @Test
    fun `miss creates tenant plus public project and publishes event`() {
        val tenantId = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        every { tenantRepository.findByExternalId("T123") } returns null
        every { tenantRepository.save(any()) } returns Tenant(id = tenantId, name = "Acme", externalId = "T123")
        every { tenantProjectService.ensurePublicProject(tenantId) } returns
            TenantProject(id = publicId, tenantId = tenantId, name = "Baza ogólna")

        val result = service.provisionTenant("T123", "Acme", "U1")

        assertTrue(result.isNew)
        assertEquals(tenantId, result.tenant.id)
        assertEquals("Free", result.plan)
        verify { tenantRepository.save(match { it.name == "Acme" && it.externalId == "T123" }) }
        val slot = slot<Any>()
        verify { events.publishEvent(capture(slot)) }
        val event = slot.captured as TenantProvisionedEvent
        assertEquals(tenantId, event.tenantId)
        assertEquals("U1", event.installerUserId)
    }

    @Test
    fun `hit returns existing without event`() {
        val existing = Tenant(id = UUID.randomUUID(), name = "Acme", externalId = "T123")
        every { tenantRepository.findByExternalId("T123") } returns existing

        val result = service.provisionTenant("T123", "Acme")

        assertFalse(result.isNew)
        assertEquals(existing, result.tenant)
        verify(exactly = 0) { tenantRepository.save(any()) }
        verify(exactly = 0) { tenantProjectService.ensurePublicProject(any()) }
        verify(exactly = 0) { events.publishEvent(any()) }
    }

    @Test
    fun `null workspaceName falls back to externalId`() {
        val tenantId = UUID.randomUUID()
        every { tenantRepository.findByExternalId("T999") } returns null
        every { tenantRepository.save(any()) } returns Tenant(id = tenantId, name = "T999", externalId = "T999")
        every { tenantProjectService.ensurePublicProject(tenantId) } returns
            TenantProject(id = UUID.randomUUID(), tenantId = tenantId, name = "Baza ogólna")

        val result = service.provisionTenant("T999", null)

        assertTrue(result.isNew)
        verify { tenantRepository.save(match { it.name == "T999" }) }
    }

    @Test
    fun `race on unique constraint reuses existing`() {
        val existing = Tenant(id = UUID.randomUUID(), name = "Acme", externalId = "T123")
        every { tenantRepository.findByExternalId("T123") } returnsMany listOf(null, existing)
        every { tenantRepository.save(any()) } throws DataIntegrityViolationException("dup")

        val result = service.provisionTenant("T123", "Acme")

        assertFalse(result.isNew)
        assertEquals(existing, result.tenant)
        verify(exactly = 0) { events.publishEvent(any()) }
    }
}
