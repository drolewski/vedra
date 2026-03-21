package io.vedra.infrastructure.database.industry

import io.vedra.domain.repository.industry.IndustryRepository
import org.springframework.stereotype.Repository
import java.util.UUID
import io.vedra.domain.model.Industry as DomainIndustry

@Repository
class JpaIndustryRepository(
    private val springDataRepo: SpringDataIndustryRepository,
) : IndustryRepository {
    override fun findByTenantId(tenantId: UUID): List<DomainIndustry> = springDataRepo.findByTenantId(tenantId).map { it.toDomain() }

    override fun findByTenantIdAndName(
        tenantId: UUID,
        name: String,
    ): DomainIndustry? = springDataRepo.findByTenantIdAndName(tenantId, name)?.toDomain()

    override fun findById(id: UUID): DomainIndustry? = springDataRepo.findById(id).orElse(null)?.toDomain()

    override fun save(industry: DomainIndustry): DomainIndustry = springDataRepo.save(industry.toEntity()).toDomain()
}
