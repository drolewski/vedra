package io.vedra.api.controller

import io.vedra.domain.model.CacheStats
import io.vedra.infrastructure.cache.CacheStatsCollector
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/cache")
class CacheAdminController(
    private val cacheStatsCollector: CacheStatsCollector,
) {
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<CacheStats> = ResponseEntity.ok(cacheStatsCollector.getStats())
}
