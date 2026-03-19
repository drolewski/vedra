package io.vedra.infrastructure.slack

import com.slack.api.bolt.App
import com.slack.api.bolt.socket_mode.SocketModeApp
import com.slack.api.model.event.AppMentionEvent
import com.slack.api.model.event.MessageEvent
import io.vedra.domain.service.KnowledgeService
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SlackConfig(
    @Value("\${slack.app-token}") private val appToken: String,
    @Value("\${slack.bot-token}") private val botToken: String,
    private val knowledgeService: KnowledgeService,
) {
    private var socketModeApp: SocketModeApp? = null

    @Bean
    fun slackApp(): App {
        val app = App()

        app.event(MessageEvent::class.java) { payload, ctx ->
            val text = payload.event.text
            if (text.isNullOrBlank()) return@event ctx.ack()

            val response = knowledgeService.answerQuestion(text)

            ctx.client().chatPostMessage { msg ->
                msg.channel(payload.event.channel)
                    .threadTs(payload.event.ts)
                    .text(response)
            }

            ctx.ack()
        }

        app.event(AppMentionEvent::class.java) { payload, ctx ->
            val text = payload.event.text
            if (text.isNullOrBlank()) return@event ctx.ack()

            val response = knowledgeService.answerQuestion(text)

            ctx.client().chatPostMessage { msg ->
                msg.channel(payload.event.channel)
                    .threadTs(payload.event.ts)
                    .text(response)
            }

            ctx.ack()
        }

        socketModeApp = SocketModeApp(appToken, app)
        socketModeApp?.start()

        return app
    }

    @PreDestroy
    fun stop() {
        socketModeApp?.stop()
    }
}
