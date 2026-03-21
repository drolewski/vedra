package io.vedra.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.model.ChannelProject
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
        val generalProject = tenantProjectRepository.findByTenantIdAndIsGeneralTrue(tenantId)

        return when {
            channelProject != null && generalProject != null ->
                listOf(channelProject.projectId, generalProject.id)

            channelProject != null ->
                listOf(channelProject.projectId)

            generalProject != null ->
                listOf(generalProject.id)

            else -> emptyList()
        }
    }

    @Transactional
    fun linkChannelToProject(
        tenantId: UUID,
        channelId: String,
        projectName: String,
    ): LinkResult {
        val project =
            tenantProjectRepository.findByTenantIdAndName(tenantId, projectName)
                ?: return LinkResult.PROJECT_NOT_FOUND

        if (project.isGeneral) {
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

    fun getChannelStatus(channelId: String): ChannelStatus {
        val channelProject = channelProjectRepository.findByChannelId(channelId)
        val projectName =
            channelProject?.let { cp ->
                tenantProjectRepository
                    .findByTenantId(UUID.randomUUID())
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
    fun createProject(
        tenantId: UUID,
        name: String,
        description: String? = null,
        industry: String? = null,
        isGeneral: Boolean = false,
    ): CreateResult {
        val existing = tenantProjectRepository.findByTenantIdAndName(tenantId, name)
        if (existing != null) {
            return CreateResult.AlreadyExists(existing.name)
        }

        val project =
            TenantProject(
                tenantId = tenantId,
                name = name,
                description = description,
                industry = industry,
                isGeneral = isGeneral,
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
    }

    data class ChannelStatus(
        val channelId: String,
        val projectName: String?,
        val isLinked: Boolean,
    )
}
