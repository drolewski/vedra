package io.vedra.domain.model

import java.time.Instant
import java.util.UUID

data class ChannelProject(
    val channelId: String,
    val projectId: UUID,
    val createdAt: Instant = Instant.now(),
)
