package io.vedra.infrastructure.slack.install

import io.github.oshai.kotlinlogging.KotlinLogging
import io.vedra.infrastructure.slack.SlackOAuthConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/slack")
class SlackOAuthController(
    private val oAuthService: SlackOAuthService,
    private val oAuthConfig: SlackOAuthConfig,
    @Value($$"${vedra.owner-email:owner@vedra.local}") private val ownerEmail: String,
) {
    @GetMapping("/install")
    fun install(): ResponseEntity<Void> {
        if (oAuthConfig.clientId.isBlank()) {
            logger.warn { "[INSTALL] /install without SLACK_CLIENT_ID" }
            return ResponseEntity.badRequest().build()
        }
        val url = oAuthService.buildInstallUrl()
        return ResponseEntity.status(302).location(URI.create(url)).build()
    }

    @GetMapping("/oauth/callback")
    fun callback(
        @RequestParam(value = "code", required = false) code: String?,
        @RequestParam(value = "state", required = false) state: String?,
        @RequestParam(value = "error", required = false) error: String?,
    ): ResponseEntity<Void> {
        if (!error.isNullOrBlank()) {
            logger.warn { "[INSTALL] OAuth denied by user error=$error" }
            return ResponseEntity.status(302).location(URI.create("/api/v1/slack/oauth/success?error=$error")).build()
        }
        return try {
            oAuthService.handleCallback(code, state)
            ResponseEntity.status(302).location(URI.create("/api/v1/slack/oauth/success")).build()
        } catch (e: SlackOAuthException) {
            logger.warn(e) { "[INSTALL] OAuth callback failed" }
            ResponseEntity.status(302).location(URI.create("/api/v1/slack/oauth/success?error=install_failed")).build()
        }
    }

    @GetMapping("/oauth/success", produces = [MediaType.TEXT_HTML_VALUE])
    fun success(
        @RequestParam(value = "error", required = false) error: String?,
    ): ResponseEntity<String> {
        val body =
            if (error.isNullOrBlank()) {
                "<h1>Vedra zainstalowana!</h1>" +
                    "<p>Zaproś bota na kanał: <code>/invite @Vedra</code> — przestrzeń utworzy się sama.</p>" +
                    "<p>Szczegóły: <code>/vedra config</code>. Status: <code>/vedra status</code>.</p>"
            } else {
                "<h1>Instalacja nie powiodła się ($error).</h1>" +
                    "<p>Spróbuj ponownie: <a href=\"/api/v1/slack/install\">/api/v1/slack/install</a>. " +
                    "Jeśli problem wróci, napisz do $ownerEmail.</p>"
            }
        return ResponseEntity.ok(body)
    }
}
