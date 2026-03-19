package io.vedra.infrastructure.slack

import com.slack.api.bolt.App
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SlackConfig(
    @Value($$"${slack.bot-token}") private val botToken: String,
) {
    @Bean
    fun slackApp(): App {
        val app = App()
        app.client().authTest { it.token(botToken) }
        return app
    }
}
