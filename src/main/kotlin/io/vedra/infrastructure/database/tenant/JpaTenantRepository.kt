package io.vedra.infrastructure.database.tenant

import io.vedra.domain.repository.tenant.TenantRepository
import org.springframework.stereotype.Repository
import java.util.UUID
import io.vedra.domain.model.Tenant as DomainTenant

@Repository
class JpaTenantRepository(
    private val springDataRepo: SpringDataTenantRepository,
) : TenantRepository {
    override fun findByExternalId(externalId: String): DomainTenant? = springDataRepo.findByExternalId(externalId)?.toDomain()

    override fun findByActiveTrue(): List<DomainTenant> = springDataRepo.findByActiveTrue().map { it.toDomain() }

    override fun findById(id: UUID): DomainTenant? = springDataRepo.findById(id).orElse(null)?.toDomain()

    override fun save(tenant: DomainTenant): DomainTenant = springDataRepo.save(tenant.toEntity()).toDomain()
}
