package io.vedra.domain.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.install.SlackInstallation
import io.vedra.infrastructure.slack.install.SlackInstallationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

data class InstallationDto(
    val teamId: String,
    val teamName: String?,
    val botToken: String,
    val botUserId: String?,
    val installerUserId: String?,
    val scopes: String,
    val appId: String?,
)

@Service
class SlackInstallationService(
    private val repository: SlackInstallationRepository,
) {
    @Transactional
    fun saveOrUpdate(dto: InstallationDto): SlackInstallation {
        val existing = repository.findById(dto.teamId).orElse(null)
        return if (existing == null) {
            repository
                .save(
                    SlackInstallation(
                        teamId = dto.teamId,
                        teamName = dto.teamName,
                        botToken = dto.botToken,
                        botUserId = dto.botUserId,
                        installerUserId = dto.installerUserId,
                        scopes = dto.scopes,
                        appId = dto.appId,
                        installedAt = Instant.now(),
                        revokedAt = null,
                        active = true,
                    ),
                ).also { logger.info { "[INSTALL] Saved installation team=${dto.teamId}" } }
        } else {
            existing.teamName = dto.teamName
            existing.botToken = dto.botToken
            existing.botUserId = dto.botUserId
            if (dto.installerUserId != null) existing.installerUserId = dto.installerUserId
            existing.scopes = dto.scopes
            existing.appId = dto.appId
            existing.installedAt = Instant.now()
            existing.revokedAt = null
            existing.active = true
            repository.save(existing).also { logger.info { "[INSTALL] Updated installation team=${dto.teamId}" } }
        }
    }

    @Transactional(readOnly = true)
    fun findBotToken(teamId: String): String? {
        val inst = repository.findById(teamId).orElse(null) ?: return null
        if (!inst.active || inst.revokedAt != null) return null
        return inst.botToken.ifBlank { null }
    }

    @Transactional(readOnly = true)
    fun isInstalled(teamId: String): Boolean = findBotToken(teamId) != null

    @Transactional
    fun markRevoked(teamId: String) {
        val inst = repository.findById(teamId).orElse(null) ?: return
        inst.revokedAt = Instant.now()
        inst.active = false
        repository.save(inst)
        logger.info { "[INSTALL] Revoked installation team=$teamId" }
    }

    @Transactional
    fun markActive(teamId: String) {
        val inst = repository.findById(teamId).orElse(null) ?: return
        inst.active = true
        inst.revokedAt = null
        repository.save(inst)
    }
}
