package io.vedra.domain.model

import java.time.Instant

data class FeedbackMapping(
    val id: String,
    val channel: String,
    val messageTs: String,
    val entryIds: List<String>,
    val votes: Map<String, String> = emptyMap(),
    val createdAt: Instant = Instant.now(),
) {
    val positiveCount: Int get() = votes.count { it.value == "positive" }
    val negativeCount: Int get() = votes.count { it.value == "negative" }
    val totalVotes: Int get() = votes.size
}
