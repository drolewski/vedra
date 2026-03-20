package io.vedra.domain.repository.project

import io.vedra.infrastructure.database.project.ChannelProject
import org.springframework.data.jpa.repository.JpaRepository

interface ChannelProjectRepository : JpaRepository<ChannelProject, String> {
    fun findByChannelId(channelId: String): ChannelProject?

    fun deleteByChannelId(channelId: String)
}
