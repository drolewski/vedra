package io.vedra.infrastructure.database.project

import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataChannelProjectRepository : JpaRepository<ChannelProject, String> {
    fun findByChannelId(channelId: String): ChannelProject?

    fun deleteByChannelId(channelId: String)
}
