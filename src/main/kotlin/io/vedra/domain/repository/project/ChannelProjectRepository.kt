package io.vedra.domain.repository.project

import io.vedra.domain.model.ChannelProject

interface ChannelProjectRepository {
    fun findByChannelId(channelId: String): ChannelProject?

    fun save(channelProject: ChannelProject): ChannelProject

    fun deleteByChannelId(channelId: String)
}
