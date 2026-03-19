package io.vedra.infrastructure.embedding

import com.pgvector.PGvector

interface EmbeddingPort {
    fun embed(text: String): PGvector

    fun embedBatch(texts: List<String>): List<PGvector>
}
