package io.vedra.domain.repository.knowledge

import io.vedra.infrastructure.database.knowledge.KnowledgeEntry
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KnowledgeRepository : JpaRepository<KnowledgeEntry, UUID> {
    // todo get, add and vector search
}