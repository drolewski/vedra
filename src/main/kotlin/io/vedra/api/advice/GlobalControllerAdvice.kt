package io.vedra.api.advice

import io.vedra.api.advice.shared.ControllerAdviceMessageTranslator
import io.vedra.api.advice.shared.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalControllerAdvice(
    private val controllerAdviceMessageTranslator: ControllerAdviceMessageTranslator,
) {
    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ErrorResponse> {
        val errorResponse =
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                message = controllerAdviceMessageTranslator.translate(GLOBAL_EXCEPTION),
            )
        return ResponseEntity(errorResponse, HttpStatus.BAD_REQUEST)
    }

    companion object {
        private const val GLOBAL_EXCEPTION = "exception.global"
    }
}
