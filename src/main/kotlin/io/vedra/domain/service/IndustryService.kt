package io.vedra.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.repository.industry.IndustryRepository
import io.vedra.infrastructure.database.industry.Industry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class IndustryService(
    private val industryRepository: IndustryRepository,
) {
    fun getIndustriesForTenant(tenantId: UUID): List<Industry> = industryRepository.findByTenantId(tenantId)

    @Transactional
    fun getOrCreateIndustry(
        tenantId: UUID,
        name: String,
    ): Industry {
        val existing = industryRepository.findByTenantIdAndName(tenantId, name)
        if (existing != null) {
            return existing
        }

        logger.info { "Creating new industry '$name' for tenant=$tenantId" }
        val industry = Industry(tenantId = tenantId, name = name)
        return industryRepository.save(industry)
    }
}
