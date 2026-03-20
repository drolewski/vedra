package io.vedra.infrastructure.database.feedback

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "feedback_mappings")
data class FeedbackMapping(
    @Id
    val id: String,
    @Column(nullable = false, length = 50)
    val channel: String,
    @Column(name = "message_ts", nullable = false, length = 50)
    val messageTs: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entry_ids", columnDefinition = "jsonb", nullable = false)
    val entryIds: List<String>,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var votes: Map<String, String> = emptyMap(),
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    constructor() : this(
        id = "",
        channel = "",
        messageTs = "",
        entryIds = emptyList(),
    )

    val positiveCount: Int get() = votes.count { it.value == "positive" }
    val negativeCount: Int get() = votes.count { it.value == "negative" }
    val totalVotes: Int get() = votes.size
}
