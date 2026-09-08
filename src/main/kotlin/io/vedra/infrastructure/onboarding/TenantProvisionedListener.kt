package io.vedra.infrastructure.onboarding

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.event.TenantProvisionedEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private val logger = KotlinLogging.logger {}

@Component
class TenantProvisionedListener(
    @Value($$"${vedra.owner-email:owner@vedra.local}") private val ownerEmail: String,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: TenantProvisionedEvent) {
        runCatching {
            logger.info {
                "[ONBOARDING] Welcome email -> admin (${event.installerUserId ?: "unknown"}) " +
                    "tenant=${event.tenantId}: Vedra gotowa! Zaproś @Vedra na kanał (/invite @Vedra), " +
                    "utworz projekt (/vedra create), sprawdz status (/vedra status)."
            }
        }.onFailure { logger.warn(it) { "[ONBOARDING] Welcome email log failed tenant=${event.tenantId}" } }

        runCatching {
            logger.info {
                "[ONBOARDING] Owner notification -> $ownerEmail: " +
                    "Nowy klient ${event.workspaceName} (${event.externalId}) tenant=${event.tenantId} plan=${event.plan}."
            }
        }.onFailure { logger.warn(it) { "[ONBOARDING] Owner notification log failed tenant=${event.tenantId}" } }

        runCatching {
            logger.info {
                "[ONBOARDING] Offer docs -> tenant=${event.tenantId}: " +
                    "Oferta_Free_${event.externalId}.md (3 projekty / 3 branże+general / 500 ingest / 100 pytań mies.)."
            }
        }.onFailure { logger.warn(it) { "[ONBOARDING] Offer docs log failed tenant=${event.tenantId}" } }
    }
}
