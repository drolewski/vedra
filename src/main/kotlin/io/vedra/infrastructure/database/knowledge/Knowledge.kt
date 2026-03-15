package io.vedra.infrastructure.database.knowledge

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.*

@Entity
@Table(name = "knowledge_entries")
data class KnowledgeEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    val tenantId: UUID,
    val source: KnowledgeSourceType,
    val content: String,
    val createdAt: Instant = Instant.now(),
)

enum class KnowledgeSourceType {
    DOCUMENT,
    CONVERSATION
}