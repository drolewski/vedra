package io.vedra.infrastructure.database.feedback

import io.vedra.domain.model.FeedbackMapping as DomainFeedbackMapping

fun FeedbackMapping.toDomain(): DomainFeedbackMapping =
    DomainFeedbackMapping(
        id = this.id,
        channel = this.channel,
        messageTs = this.messageTs,
        entryIds = this.entryIds,
        votes = this.votes,
        createdAt = this.createdAt,
    )

fun DomainFeedbackMapping.toEntity(): FeedbackMapping =
    FeedbackMapping(
        id = this.id,
        channel = this.channel,
        messageTs = this.messageTs,
        entryIds = this.entryIds,
        votes = this.votes,
    )
