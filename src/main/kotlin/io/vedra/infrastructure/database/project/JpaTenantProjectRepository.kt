package io.vedra.infrastructure.database.project

import io.vedra.domain.repository.project.TenantProjectRepository
import org.springframework.stereotype.Repository
import java.util.UUID
import io.vedra.domain.model.TenantProject as DomainTenantProject

@Repository
class JpaTenantProjectRepository(
    private val springDataRepo: SpringDataTenantProjectRepository,
) : TenantProjectRepository {
    override fun findByTenantId(tenantId: UUID): List<DomainTenantProject> = springDataRepo.findByTenantId(tenantId).map { it.toDomain() }

    override fun findByTenantIdAndIsGeneralTrue(tenantId: UUID): DomainTenantProject? =
        springDataRepo.findByTenantIdAndIsGeneralTrue(tenantId)?.toDomain()

    override fun findByTenantIdAndId(
        tenantId: UUID,
        id: String,
    ): DomainTenantProject? = springDataRepo.findByTenantIdAndId(tenantId, UUID.fromString(id))?.toDomain()

    override fun findByTenantIdAndName(
        tenantId: UUID,
        name: String,
    ): DomainTenantProject? = springDataRepo.findByTenantIdAndName(tenantId, name)?.toDomain()

    override fun save(project: DomainTenantProject): DomainTenantProject = springDataRepo.save(project.toEntity()).toDomain()
}
