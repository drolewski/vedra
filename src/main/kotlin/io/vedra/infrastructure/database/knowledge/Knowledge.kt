package io.vedra.infrastructure.database.knowledge

import com.pgvector.PGvector
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "knowledge_entries")
data class KnowledgeEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,
    val source: KnowledgeSourceType,
    val content: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val metadata: Map<String, Any> = emptyMap(),
    @Column(columnDefinition = "vector(1536)")
    var embedding: PGvector? = null,
    val createdAt: Instant = Instant.now(),
)

enum class KnowledgeSourceType {
    DOCUMENT,
    FAQ,
}
