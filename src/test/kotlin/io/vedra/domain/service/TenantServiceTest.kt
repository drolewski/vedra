package io.vedra.domain.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.model.Tenant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TenantServiceTest {
    private val provisioningService: TenantProvisioningService = mockk()
    private val service = TenantService(provisioningService)

    @Test
    fun `resolve delegates to provisioning`() {
        val tenant = Tenant(id = UUID.randomUUID(), name = "Acme", externalId = "T123")
        every { provisioningService.provisionTenant("T123", "Acme", null) } returns ProvisionResult(tenant, true)

        assertEquals(tenant, service.resolveByExternalId("T123", "Acme"))
        verify { provisioningService.provisionTenant("T123", "Acme", null) }
    }

    @Test
    fun `resolve rejects blank externalId`() {
        assertFailsWith<TenantExternalIdMissingException> {
            service.resolveByExternalId(null, "Acme")
        }
    }
}
