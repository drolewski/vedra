package io.vedra.infrastructure.slack

import com.slack.api.bolt.App
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SlackConfig(
    @Value($$"${slack.bot-token:}") private val botToken: String,
    @Value($$"${slack.signing-secret:}") private val signingSecret: String,
    @Value($$"${slack.client-id:}") private val clientId: String,
    @Value($$"${slack.client-secret:}") private val clientSecret: String,
    @Value($$"${slack.redirect-url:}") private val redirectUrl: String,
    @Value($$"${slack.scopes:}") private val scopes: String,
) {
    @Bean
    fun slackApp(): App = App()

    @Bean
    fun slackSigningSecret(): String = signingSecret

    @Bean
    fun slackLegacyBotToken(): String = botToken

    @Bean
    fun slackOAuthConfig(): SlackOAuthConfig =
        SlackOAuthConfig(
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUrl = redirectUrl,
            scopes = scopes,
        )
}

data class SlackOAuthConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUrl: String,
    val scopes: String,
)
