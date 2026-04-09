package com.app.security.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Centralized application configuration properties
 * Replaces scattered @Value annotations throughout the codebase
 */

@ConfigurationProperties(prefix = "app.admin")
data class AdminProperties(
    val username: String,
    val password: String,
    val email: String
)

@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
    val internalApiKey: String? = null
)

@ConfigurationProperties(prefix = "web")
data class WebProperties(
    /**
     * List of routes that do not require authentication.
     * These routes are accessible without any security checks.
     */
    val unprotectedRoutes: List<String>,
    val jwtSecret: String,
    val jwtValidSeconds: Int,
    val domain: String,
    /**
     * URL prefix for the Angular SPA (must match Angular `baseHref`). Used by [com.app.controller.SpaController] and public security matchers.
     */
    val spaBasePath: String = "/app",
)

@ConfigurationProperties(prefix = "wireguard")
data class WireGuardProperties(
    val config: ConfigProperties
) {
    data class ConfigProperties(
        val directory: String = "/etc/wireguard"
    )
}

/**
 * Control plane mode configuration
 */
enum class ControlPlaneMode {
    /** Hybrid mode - supports both local and remote operations */
    HYBRID,
    /** Pure remote mode - only remote operations allowed */
    PURE_REMOTE
}

/**
 * Control plane operational mode properties
 */
@ConfigurationProperties(prefix = "app.control-plane")
data class ControlPlaneProperties(
    /** Control plane operational mode */
    val mode: ControlPlaneMode = ControlPlaneMode.HYBRID
)

/**
 * Main application properties container
 * This is the single entry point for all configuration properties
 */
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val admin: AdminProperties,
    /**
     * Base URL for the application (since frontend and backend are deployed together)
     */
    val baseUrl: String,
    val security: SecurityProperties,
    val controlPlane: ControlPlaneProperties = ControlPlaneProperties()
)