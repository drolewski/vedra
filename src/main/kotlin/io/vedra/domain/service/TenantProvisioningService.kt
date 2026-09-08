package io.vedra.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.event.TenantProvisionedEvent
import io.vedra.domain.model.Tenant
import io.vedra.domain.repository.tenant.TenantRepository
import io.vedra.infrastructure.onboarding.OnboardingPlans
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

data class ProvisionResult(
    val tenant: Tenant,
    val isNew: Boolean,
    val plan: String = OnboardingPlans.DEFAULT,
)

@Service
class TenantProvisioningService(
    private val tenantRepository: TenantRepository,
    private val tenantProjectService: TenantProjectService,
    private val events: ApplicationEventPublisher,
) {
    @Transactional
    fun provisionTenant(
        externalId: String,
        workspaceName: String?,
        installerUserId: String? = null,
    ): ProvisionResult {
        tenantRepository.findByExternalId(externalId)?.let {
            return ProvisionResult(tenant = it, isNew = false)
        }

        val name = workspaceName ?: externalId
        try {
            val saved = tenantRepository.save(Tenant(name = name, externalId = externalId))
            val publicProject = tenantProjectService.ensurePublicProject(saved.id!!)
            val limits = OnboardingPlans.limitsFor()
            logger.info {
                "[ONBOARDING] Provisioned tenant=${saved.id} externalId=$externalId " +
                    "name=\"$name\" publicProjectId=${publicProject.id} plan=${OnboardingPlans.DEFAULT} " +
                    "limits=${limits.maxProjects} projects/${limits.monthlyIngest} ingest/${limits.monthlyAnswers} answers"
            }
            events.publishEvent(
                TenantProvisionedEvent(
                    tenantId = saved.id!!,
                    externalId = externalId,
                    workspaceName = name,
                    installerUserId = installerUserId,
                ),
            )
            return ProvisionResult(tenant = saved, isNew = true)
        } catch (e: DataIntegrityViolationException) {
            val existing = tenantRepository.findByExternalId(externalId)
            if (existing != null) {
                logger.info { "[ONBOARDING] Race on externalId=$externalId, reusing tenant=${existing.id}" }
                return ProvisionResult(tenant = existing, isNew = false)
            }
            throw e
        }
    }
}
