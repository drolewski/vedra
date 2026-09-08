package io.vedra.domain.service

import io.vedra.domain.model.HybridWeights
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class HybridScoringService {
    fun finalScore(
        semanticScore: Double,
        qualityScore: Double,
        lastUsedAt: Instant?,
        createdAt: Instant,
        accepted: Boolean,
        weights: HybridWeights = HybridWeights(),
    ): Double =
        weights.semantic * semanticScore +
            weights.quality * qualityScore +
            weights.freshness * freshnessBonus(lastUsedAt, createdAt, weights.decayDays) +
            weights.acceptance * acceptanceBonus(accepted)

    fun freshnessBonus(
        lastUsedAt: Instant?,
        createdAt: Instant,
        decayDays: Double = HybridWeights().decayDays,
    ): Double {
        val base = lastUsedAt ?: createdAt
        val daysSince = Duration.between(base, Instant.now()).toMillis() / MILLIS_PER_DAY
        return 1.0 / (1.0 + maxOf(0.0, daysSince) / decayDays)
    }

    fun acceptanceBonus(accepted: Boolean): Double = if (accepted) 1.0 else UNACCEPTED_BONUS

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000.0
        private const val UNACCEPTED_BONUS = 0.3
    }
}
