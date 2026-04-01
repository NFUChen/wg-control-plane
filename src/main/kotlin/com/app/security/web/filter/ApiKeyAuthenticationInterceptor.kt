package com.app.security.web.filter

import org.springframework.beans.factory.annotation.Value
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class ApiKeyAuthenticationInterceptor(
    @Value("\${app.internal-api-key:}") private val validApiKey: String
) : HandlerInterceptor {

    private val logger = LoggerFactory.getLogger(ApiKeyAuthenticationInterceptor::class.java)

    companion object {
        const val API_KEY_HEADER = "X-API-Key"
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        // Only intercept /api/internal/** paths
        if (!request.requestURI.startsWith("/api/internal/")) {
            return true
        }

        // Check if API key is configured
        if (validApiKey.isBlank()) {
            logger.error("Internal API key not configured")
            response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
            response.setHeader("Content-Type", "application/json")
            response.writer.write("""{"error": "Server configuration error", "message": "Internal API key not configured"}""")
            return false
        }

        val providedApiKey = extractApiKeyFromRequest(request)
        if (providedApiKey == null) {
            logger.warn("No API key found in request to ${request.requestURI}")
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.setHeader("Content-Type", "application/json")
            response.writer.write("""{"error": "API key required", "message": "Please provide a valid API key in X-API-Key header"}""")
            return false
        }

        if (providedApiKey != validApiKey) {
            logger.warn("Invalid API key provided for request to ${request.requestURI}")
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.setHeader("Content-Type", "application/json")
            response.writer.write("""{"error": "Invalid API key", "message": "The provided API key is invalid"}""")
            return false
        }

        logger.debug("API key authenticated for request to ${request.requestURI}")
        return true
    }

    private fun extractApiKeyFromRequest(request: HttpServletRequest): String? {
        // First, try to get token from X-API-Key header
        val apiKeyHeader = request.getHeader(API_KEY_HEADER)
        if (!apiKeyHeader.isNullOrBlank()) {
            return apiKeyHeader
        }

        // Fallback to Authorization Bearer header
        val authHeader = request.getHeader(AUTHORIZATION_HEADER)
        if (!authHeader.isNullOrBlank() && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length)
        }

        return null
    }
}