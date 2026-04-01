package com.app.security.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "app.admin")
class AdminProperties(
    val username: String,
    val password: String,
    val email: String
)


@ConfigurationProperties(prefix = "web")
class WebProperties(
    /**
     * List of routes that do not require authentication.
     * These routes are accessible without any security checks.
     */
    @Value("\${unprotected-routes}") val unprotectedRoutes: List<String>,
    @Value("\${jwt-secret}") val jwtSecret: String,
    @Value("\${jwt-valid-seconds}") val jwtValidSeconds: Int,
    @Value("\${domain}") val domain: String,
)