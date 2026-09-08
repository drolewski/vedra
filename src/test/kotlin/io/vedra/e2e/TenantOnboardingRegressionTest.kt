package io.vedra.e2e

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.event.TenantProvisionedEvent
import io.vedra.domain.model.ProjectVisibility
import io.vedra.domain.model.Tenant
import io.vedra.domain.model.TenantProject
import io.vedra.domain.repository.tenant.TenantRepository
import io.vedra.domain.service.TenantExternalIdMissingException
import io.vedra.domain.service.TenantProjectService
import io.vedra.domain.service.TenantProvisioningService
import io.vedra.domain.service.TenantService
import io.vedra.infrastructure.onboarding.OnboardingPlans
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class TenantOnboardingRegressionTest {
    private lateinit var tenantRepository: TenantRepository
    private lateinit var tenantProjectService: TenantProjectService
    private lateinit var events: ApplicationEventPublisher
    private lateinit var provisioningService: TenantProvisioningService
    private lateinit var tenantService: TenantService

    @BeforeEach
    fun setUp() {
        tenantRepository = mockk()
        tenantProjectService = mockk()
        events = mockk(relaxed = true)
        provisioningService = TenantProvisioningService(tenantRepository, tenantProjectService, events)
        tenantService = TenantService(provisioningService)
    }

    @Test
    fun `FT-TEN-01 first contact provisions tenant with public project`() {
        val tenantId = UUID.randomUUID()
        every { tenantRepository.findByExternalId("T-NEW") } returns null
        every { tenantRepository.save(any()) } returns Tenant(id = tenantId, name = "Nowa Firma", externalId = "T-NEW")
        every { tenantProjectService.ensurePublicProject(tenantId) } returns
            TenantProject(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                name = "Baza ogólna",
                visibility = ProjectVisibility.PUBLIC,
            )

        val result = tenantService.resolveByExternalId("T-NEW", "Nowa Firma")

        assertEquals(tenantId, result.id)
        assertEquals("T-NEW", result.externalId)
        verify { tenantProjectService.ensurePublicProject(tenantId) }
        verify { events.publishEvent(any<TenantProvisionedEvent>()) }
    }

    @Test
    fun `FT-TEN-02 repeat contact reuses tenant without duplicates`() {
        val existing = Tenant(id = UUID.randomUUID(), name = "Nowa Firma", externalId = "T-NEW")
        every { tenantRepository.findByExternalId("T-NEW") } returns existing

        val result = provisioningService.provisionTenant("T-NEW", "Nowa Firma")

        assertFalse(result.isNew)
        assertEquals(existing, result.tenant)
        assertEquals(OnboardingPlans.DEFAULT, result.plan)
        verify(exactly = 0) { tenantRepository.save(any()) }
        verify(exactly = 0) { events.publishEvent(any()) }
    }

    @Test
    fun `FT-TEN-05 blank workspace is rejected`() {
        assertThrows(TenantExternalIdMissingException::class.java) {
            tenantService.resolveByExternalId("  ", "Nowa Firma")
        }
        assertTrue(OnboardingPlans.limitsFor().maxProjects == 3)
    }
}
