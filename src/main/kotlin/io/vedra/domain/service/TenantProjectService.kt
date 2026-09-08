package io.vedra.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.ChannelProject
import io.vedra.domain.model.ProjectVisibility
import io.vedra.domain.model.TenantProject
import io.vedra.domain.repository.project.ChannelProjectRepository
import io.vedra.domain.repository.project.TenantProjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class TenantProjectService(
    private val tenantProjectRepository: TenantProjectRepository,
    private val channelProjectRepository: ChannelProjectRepository,
) {
    fun getProjectIdsForChannel(
        tenantId: UUID,
        channelId: String,
    ): List<UUID?> {
        val channelProject = channelProjectRepository.findByChannelId(channelId)
        val publicProject = tenantProjectRepository.findPublicByTenantId(tenantId)

        return listOfNotNull(
            channelProject?.projectId,
            publicProject?.id?.takeIf { it != channelProject?.projectId },
        ).distinct().ifEmpty {
            emptyList()
        }
    }

    fun getPrivateLinkedProjectId(
        channelId: String,
        tenantId: UUID,
    ): UUID? {
        val linked = channelProjectRepository.findByChannelId(channelId) ?: return null
        val project =
            tenantProjectRepository.findByTenantIdAndId(tenantId, linked.projectId.toString())
                ?: return null
        return if (project.visibility == ProjectVisibility.PRIVATE) linked.projectId else null
    }

    @Transactional
    fun linkChannelToProject(
        tenantId: UUID,
        channelId: String,
        projectId: String,
    ): LinkResult {
        val project =
            tenantProjectRepository.findByTenantIdAndId(tenantId, projectId)
                ?: return LinkResult.PROJECT_NOT_FOUND

        if (project.visibility == ProjectVisibility.PUBLIC) {
            return LinkResult.CANNOT_LINK_GENERAL
        }

        val existing = channelProjectRepository.findByChannelId(channelId)
        if (existing != null) {
            channelProjectRepository.deleteByChannelId(channelId)
        }

        channelProjectRepository.save(ChannelProject(channelId = channelId, projectId = project.id!!))

        logger.info { "Linked channel=$channelId to project=${project.name}" }
        return LinkResult.SUCCESS
    }

    @Transactional
    fun unlinkChannel(channelId: String) {
        channelProjectRepository.deleteByChannelId(channelId)
        logger.info { "Unlinked channel=$channelId" }
    }

    fun getChannelStatus(
        channelId: String,
        tenantId: UUID,
    ): ChannelStatus {
        val channelProject = channelProjectRepository.findByChannelId(channelId)
        val projectName =
            channelProject?.let { cp ->
                tenantProjectRepository
                    .findByTenantId(tenantId)
                    .find { it.id == cp.projectId }
                    ?.name
            }
        return ChannelStatus(
            channelId = channelId,
            projectName = projectName,
            isLinked = channelProject != null,
        )
    }

    fun listProjects(tenantId: UUID): List<TenantProject> = tenantProjectRepository.findByTenantId(tenantId)

    @Transactional
    fun ensurePublicProject(tenantId: UUID): TenantProject {
        tenantProjectRepository.findPublicByTenantId(tenantId)?.let { return it }
        val project =
            TenantProject(
                tenantId = tenantId,
                name = "Baza ogólna",
                description = "Wiedza współdzielona tenanta",
                visibility = ProjectVisibility.PUBLIC,
            )
        return tenantProjectRepository.save(project)
    }

    @Transactional
    fun createPrivateAndLink(
        tenantId: UUID,
        channelId: String,
        name: String,
        description: String? = null,
        industry: String? = null,
    ): CreateResult {
        when (val result = createProject(tenantId, name, description, industry)) {
            is CreateResult.AlreadyExists -> return result
            is CreateResult.PublicAlreadyExists -> return result
            is CreateResult.Success -> {
                val existing = channelProjectRepository.findByChannelId(channelId)
                if (existing != null) {
                    channelProjectRepository.deleteByChannelId(channelId)
                }
                channelProjectRepository.save(ChannelProject(channelId = channelId, projectId = result.project.id!!))
                logger.info { "Linked channel=$channelId to project=${result.project.name}" }
                return result
            }
        }
    }

    @Transactional
    fun createProject(
        tenantId: UUID,
        name: String,
        description: String? = null,
        industry: String? = null,
        visibility: ProjectVisibility = ProjectVisibility.PRIVATE,
    ): CreateResult {
        val existing = tenantProjectRepository.findByTenantIdAndName(tenantId, name)
        if (existing != null) {
            return CreateResult.AlreadyExists(existing.name)
        }

        if (visibility == ProjectVisibility.PUBLIC && tenantProjectRepository.findPublicByTenantId(tenantId) != null) {
            val publicProject = tenantProjectRepository.findPublicByTenantId(tenantId)!!
            return CreateResult.PublicAlreadyExists(publicProject.name)
        }

        val project =
            TenantProject(
                tenantId = tenantId,
                name = name,
                description = description,
                industry = industry,
                visibility = visibility,
            )
        val saved = tenantProjectRepository.save(project)
        return CreateResult.Success(saved)
    }

    enum class LinkResult {
        SUCCESS,
        PROJECT_NOT_FOUND,
        CANNOT_LINK_GENERAL,
    }

    sealed class CreateResult {
        data class Success(
            val project: TenantProject,
        ) : CreateResult()

        data class AlreadyExists(
            val name: String,
        ) : CreateResult()

        data class PublicAlreadyExists(
            val name: String,
        ) : CreateResult()
    }

    data class ChannelStatus(
        val channelId: String,
        val projectName: String?,
        val isLinked: Boolean,
    )
}
