package io.vedra.infrastructure.slack.install

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "slack_installations")
data class SlackInstallation(
    @Id
    @Column(name = "team_id", nullable = false)
    val teamId: String = "",
    @Column(name = "team_name")
    var teamName: String? = null,
    @Column(name = "bot_token", nullable = false)
    @Convert(converter = TokenCryptoConverter::class)
    var botToken: String = "",
    @Column(name = "bot_user_id")
    var botUserId: String? = null,
    @Column(name = "installer_user_id")
    var installerUserId: String? = null,
    @Column(name = "scopes", nullable = false)
    var scopes: String = "",
    @Column(name = "app_id")
    var appId: String? = null,
    @Column(name = "installed_at", nullable = false)
    var installedAt: Instant = Instant.now(),
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
    @Column(name = "active", nullable = false)
    var active: Boolean = true,
) {
    constructor() : this(teamId = "")
}
