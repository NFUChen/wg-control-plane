package com.app.exception

import com.app.service.validation.ControlPlaneValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.LocalDateTime

/**
 * Global exception handler for WireGuard API
 */
@ControllerAdvice
class GlobalExceptionHandler {

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    /**
     * Handle control plane validation errors
     */
    @ExceptionHandler(ControlPlaneValidationException::class)
    fun handleControlPlaneValidationException(
        ex: ControlPlaneValidationException
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Control plane validation failed: {}", ex.message)

        val details = mutableMapOf<String, String>()
        if (ex.suggestions.isNotEmpty()) {
            details["suggestions"] = ex.suggestions.joinToString("; ")
        }
        if (ex.warningMessage != null) {
            details["warning"] = ex.warningMessage
        }

        val errorResponse = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Control Plane Validation Error",
            message = ex.message ?: "Validation failed",
            details = details.takeIf { it.isNotEmpty() },
            timestamp = LocalDateTime.now()
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    /**
     * Handle validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.associate { error: FieldError ->
            error.field to (error.defaultMessage ?: "Invalid value")
        }

        val errorResponse = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Validation Failed",
            message = "Request validation failed",
            details = errors,
            timestamp = LocalDateTime.now(),
            path = "" // may be filled from HttpServletRequest if needed
        )

        return ResponseEntity.badRequest().body(errorResponse)
    }

    /**
     * Handle illegal argument exceptions (business logic errors)
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = ex.message ?: "Invalid request parameter",
            timestamp = LocalDateTime.now()
        )

        return ResponseEntity.badRequest().body(errorResponse)
    }

    /**
     * Handle illegal state exceptions (system state errors)
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = HttpStatus.CONFLICT.value(),
            error = "Conflict",
            message = ex.message ?: "System state conflict",
            timestamp = LocalDateTime.now()
        )

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }

    /**
     * Handle resource not found exceptions
     */
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = "Not Found",
            message = ex.message ?: "Resource not found",
            timestamp = LocalDateTime.now()
        )

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    /**
     * Handle general runtime exceptions
     */
    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = "An unexpected error occurred: ${ex.message}",
            timestamp = LocalDateTime.now()
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = "An unexpected error occurred: ${ex.message}",
            timestamp = LocalDateTime.now()
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
}

/**
 * Standard error response structure
 */
data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val details: Map<String, String>? = null,
    val timestamp: LocalDateTime,
    val path: String? = null
)