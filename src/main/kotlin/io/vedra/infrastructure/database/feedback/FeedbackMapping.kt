package io.vedra.infrastructure.database.feedback

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "feedback_mappings")
data class FeedbackMapping(
    @Id
    @Column(name = "thread_ts", nullable = false, length = 50)
    val threadTs: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entry_ids", columnDefinition = "jsonb", nullable = false)
    val entryIds: List<UUID>,
    @Column(name = "answer_source", nullable = false, length = 20)
    val answerSource: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    constructor() : this(
        threadTs = "",
        entryIds = emptyList(),
        answerSource = "",
    )
}
