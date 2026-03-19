package io.vedra.infrastructure.embedding.ollama

import com.pgvector.PGvector
import io.vedra.infrastructure.embedding.EmbeddingPort
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Component

@Component
class OllamaEmbeddingAdapter(
    private val embeddingModel: EmbeddingModel,
) : EmbeddingPort {
    override fun embed(text: String): PGvector =
        embeddingModel
            .embed(text)
            .toPGvector()

    override fun embedBatch(texts: List<String>): List<PGvector> =
        embeddingModel
            .embedForResponse(texts)
            .results
            .sortedBy { it.index }
            .map { it.output.toPGvector() }

    private fun FloatArray.toPGvector(): PGvector = PGvector(this)
}
