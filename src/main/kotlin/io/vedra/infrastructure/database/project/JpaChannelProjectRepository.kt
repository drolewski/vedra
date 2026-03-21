package io.vedra.infrastructure.database.project

import io.vedra.domain.repository.project.ChannelProjectRepository
import org.springframework.stereotype.Repository
import io.vedra.domain.model.ChannelProject as DomainChannelProject

@Repository
class JpaChannelProjectRepository(
    private val springDataRepo: SpringDataChannelProjectRepository,
    private val tenantProjectSpringDataRepo: SpringDataTenantProjectRepository,
) : ChannelProjectRepository {
    override fun findByChannelId(channelId: String): DomainChannelProject? = springDataRepo.findByChannelId(channelId)?.toDomain()

    override fun save(channelProject: DomainChannelProject): DomainChannelProject {
        val project = tenantProjectSpringDataRepo.findById(channelProject.projectId).orElseThrow()
        val entity = channelProject.toEntity(project)
        return springDataRepo.save(entity).toDomain()
    }

    override fun deleteByChannelId(channelId: String) {
        springDataRepo.deleteByChannelId(channelId)
    }
}
