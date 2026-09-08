package io.vedra.infrastructure.onboarding

import io.vedra.domain.event.TenantProvisionedEvent
import java.util.UUID
import kotlin.test.Test

class TenantProvisionedListenerTest {
    private val listener = TenantProvisionedListener("owner@vedra.local")

    @Test
    fun `handle logs three onboarding lines without throwing`() {
        listener.handle(
            TenantProvisionedEvent(
                tenantId = UUID.randomUUID(),
                externalId = "T123",
                workspaceName = "Acme",
                installerUserId = "U1",
            ),
        )
    }

    @Test
    fun `handle tolerates unknown installer`() {
        listener.handle(
            TenantProvisionedEvent(
                tenantId = UUID.randomUUID(),
                externalId = "T456",
                workspaceName = "Acme",
                installerUserId = null,
            ),
        )
    }
}
