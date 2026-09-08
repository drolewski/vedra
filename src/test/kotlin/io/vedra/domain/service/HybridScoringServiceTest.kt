package io.vedra.domain.service

import io.vedra.domain.model.HybridWeights
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HybridScoringServiceTest {
    private val service = HybridScoringService()
    private val now = Instant.now()

    @Test
    fun `final score should combine all components with default weights`() {
        val result = service.finalScore(0.9, 0.5, now, now, true)

        assertEquals(0.81, result, 0.001)
    }

    @Test
    fun `unaccepted entry should get reduced acceptance bonus`() {
        val accepted = service.finalScore(0.9, 0.5, now, now, true)
        val unaccepted = service.finalScore(0.9, 0.5, now, now, false)

        assertEquals(accepted - unaccepted, 0.05 * 0.7, 0.0001)
    }

    @Test
    fun `quality score should move final score by its weight`() {
        val low = service.finalScore(1.0, 0.0, now, now, false)
        val high = service.finalScore(1.0, 1.0, now, now, false)

        assertEquals(high - low, 0.25, 0.0001)
    }

    @Test
    fun `freshness should decay with age`() {
        val fresh = service.freshnessBonus(now, now, 90.0)
        val old = service.freshnessBonus(now.minusSeconds(90 * 86_400L), now, 90.0)

        assertEquals(1.0, fresh, 0.001)
        assertEquals(0.5, old, 0.001)
        assertTrue(old < fresh)
    }

    @Test
    fun `freshness should fall back to created at when never used`() {
        val result = service.freshnessBonus(null, now, 90.0)

        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun `custom weights should be respected`() {
        val weights = HybridWeights(semantic = 1.0, quality = 0.0, freshness = 0.0, acceptance = 0.0)

        assertEquals(0.9, service.finalScore(0.9, 0.0, null, now, false, weights), 0.0001)
    }
}
