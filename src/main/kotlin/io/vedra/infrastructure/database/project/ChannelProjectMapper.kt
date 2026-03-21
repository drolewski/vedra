package io.vedra.infrastructure.database.project

import io.vedra.domain.model.ChannelProject as DomainChannelProject

fun ChannelProject.toDomain(): DomainChannelProject =
    DomainChannelProject(
        channelId = this.channelId,
        projectId = this.projectId,
        createdAt = this.createdAt,
    )

fun DomainChannelProject.toEntity(project: TenantProject): ChannelProject =
    ChannelProject(
        channelId = this.channelId,
        project = project,
    )
