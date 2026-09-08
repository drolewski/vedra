package io.vedra.infrastructure.slack.install

import com.slack.api.Slack
import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.domain.service.InstallationDto
import io.vedra.domain.service.SlackInstallationService
import io.vedra.domain.service.TenantProvisioningService
import io.vedra.infrastructure.slack.SlackOAuthConfig
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

data class OAuthCallbackResult(
    val teamId: String,
    val teamName: String?,
    val tenantId: String,
    val isNewTenant: Boolean,
)

@Service
class SlackOAuthService(
    private val oauthConfig: SlackOAuthConfig,
    @org.springframework.beans.factory.annotation.Qualifier("slackSigningSecret") private val signingSecret: String,
    private val installationService: SlackInstallationService,
    private val provisioningService: TenantProvisioningService,
) {
    fun buildInstallUrl(state: String? = null): String {
        val s = state ?: createState()
        val params =
            listOf(
                "client_id=" + enc(oauthConfig.clientId),
                "scope=" + enc(oauthConfig.scopes),
                "redirect_uri=" + enc(oauthConfig.redirectUrl),
                "state=" + enc(s),
            ).joinToString("&")
        return "https://slack.com/oauth/v2/authorize?$params"
    }

    fun createState(): String {
        val ts = Instant.now().epochSecond
        val nonce =
            UUID
                .randomUUID()
                .toString()
                .replace("-", "")
                .take(16)
        val payload = "$ts.$nonce"
        return "$payload.${hmac(payload)}"
    }

    fun validateState(state: String?): Boolean {
        if (state.isNullOrBlank()) return false
        val parts = state.split(".")
        if (parts.size != 3) return false
        val ts = parts[0].toLongOrNull() ?: return false
        if (Instant.now().epochSecond - ts > STATE_TTL_SECONDS) return false
        return hmac("${parts[0]}.${parts[1]}") == parts[2]
    }

    fun handleCallback(
        code: String?,
        state: String?,
    ): OAuthCallbackResult {
        if (!validateState(state)) throw SlackOAuthException("Nieprawidłowy parametr state. Rozpocznij instalację od nowa.")
        if (code.isNullOrBlank()) throw SlackOAuthException("Brak kodu autoryzacyjnego od Slacka.")
        if (oauthConfig.clientId.isBlank() || oauthConfig.clientSecret.isBlank()) {
            throw SlackOAuthException("Brak konfiguracji OAuth po stronie serwera (SLACK_CLIENT_ID/SECRET).")
        }

        val slack = Slack.getInstance()
        val response =
            try {
                slack.methods().oauthV2Access {
                    it
                        .code(code)
                        .clientId(oauthConfig.clientId)
                        .clientSecret(oauthConfig.clientSecret)
                        .redirectUri(oauthConfig.redirectUrl.ifBlank { null })
                }
            } catch (e: Exception) {
                logger.warn(e) { "[INSTALL] oauth.v2.access call failed" }
                throw SlackOAuthException("Slack odrzucił wymianę kodu.", e)
            }

        if (!response.isOk || response.accessToken.isNullOrBlank() || response.team?.id.isNullOrBlank()) {
            logger.warn { "[INSTALL] oauth.v2.access error" }
            throw SlackOAuthException("Slack odrzucił instalację.")
        }

        val teamId = response.team!!.id
        val dto =
            InstallationDto(
                teamId = teamId,
                teamName = response.team?.name,
                botToken = response.accessToken!!,
                botUserId = response.botUserId,
                installerUserId = response.authedUser?.id,
                scopes = response.scope ?: oauthConfig.scopes,
                appId = response.appId,
            )
        installationService.saveOrUpdate(dto)
        val provision = provisioningService.provisionTenant(teamId, dto.teamName, dto.installerUserId)
        logger.info { "[INSTALL] OAuth installed team=$teamId newTenant=${provision.isNew}" }
        return OAuthCallbackResult(teamId, dto.teamName, provision.tenant.id.toString(), provision.isNew)
    }

    private fun hmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(signingSecret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun enc(v: String): String = URLEncoder.encode(v, Charsets.UTF_8)

    companion object {
        const val STATE_TTL_SECONDS = 600L
    }
}

class SlackOAuthException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
