package io.vedra.api.advice.shared

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource

@Configuration
class ControllerAdviceMessageSourceConfig {
    @Bean
    fun controllerAdviceMessageSource(): MessageSource {
        val messageSource = ReloadableResourceBundleMessageSource()
        messageSource.setBasename("classpath:messages/errors")
        messageSource.setDefaultEncoding("UTF-8")
        messageSource.setCacheSeconds(-1) // cache forever
        return messageSource
    }
}