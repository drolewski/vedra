package io.vedra.e2e

import io.mockk.every
import io.mockk.mockk
import io.vedra.api.controller.CacheAdminController
import io.vedra.domain.model.CacheStats
import io.vedra.infrastructure.cache.CacheStatsCollector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CacheAdminRegressionTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var collector: CacheStatsCollector

    @BeforeEach
    fun setUp() {
        collector = mockk()
        every { collector.getStats() } returns
            CacheStats(
                cacheHits = 10,
                cacheMisses = 5,
                cacheHitRatio = 0.66,
                llmCallsSaved = 10,
                embeddingCallsSaved = 5,
            )
        mockMvc = MockMvcBuilders.standaloneSetup(CacheAdminController(collector)).build()
    }

    @Test
    fun `TC-CACHE-01 stats contract is stable`() {
        mockMvc
            .perform(get("/api/v1/admin/cache/stats"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cacheHits").value(10))
            .andExpect(jsonPath("$.cacheMisses").value(5))
            .andExpect(jsonPath("$.llmCallsSaved").value(10))
            .andExpect(jsonPath("$.embeddingCallsSaved").value(5))
    }
}
