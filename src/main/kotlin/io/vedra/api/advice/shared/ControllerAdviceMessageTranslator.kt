package io.vedra.api.advice.shared

import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component

@Component
class ControllerAdviceMessageTranslator(
    private val controllerAdviceMessageSource: MessageSource,
) {

    fun translate(messageKey: String): String {
        val locale = LocaleContextHolder.getLocaleContext()
        return controllerAdviceMessageSource.getMessage(messageKey, null, locale?.locale)
    }
}