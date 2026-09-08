package io.vedra.infrastructure.config

import io.vedra.domain.model.HybridWeights
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "vedra.rag.hybrid-scoring")
class HybridScoringProperties {
    var enabled: Boolean = true
    var semanticWeight: Double = 0.65
    var qualityWeight: Double = 0.25
    var freshnessWeight: Double = 0.05
    var acceptanceWeight: Double = 0.05
    var freshnessDecayDays: Double = 90.0

    fun toWeights(): HybridWeights =
        HybridWeights(
            semantic = semanticWeight,
            quality = qualityWeight,
            freshness = freshnessWeight,
            acceptance = acceptanceWeight,
            decayDays = freshnessDecayDays,
        )
}
