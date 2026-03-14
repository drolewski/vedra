package io.vedra.shared.config.cors

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "application.cors")
data class CorsProperties(
    val allowedOrigins: List<String>,
    val allowedMethods: List<String>,
    val allowedHeaders: List<String>,
)