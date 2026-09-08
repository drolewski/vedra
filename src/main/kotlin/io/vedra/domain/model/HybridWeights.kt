package io.vedra.domain.model

data class HybridWeights(
    val semantic: Double = 0.65,
    val quality: Double = 0.25,
    val freshness: Double = 0.05,
    val acceptance: Double = 0.05,
    val decayDays: Double = 90.0,
)
