package com.app.service.validation

import com.app.security.config.ControlPlaneMode
import com.app.security.config.ControlPlaneProperties
import com.app.view.AddClientRequest
import com.app.view.CreateServerRequest
import com.app.view.UpdateServerRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * Central validation service for WireGuard operations
 * Uses strategy pattern to validate based on current control plane mode
 */
@Service
class WireGuardValidationService(
    private val strategy: ControlPlaneValidationStrategy,
    private val properties: ControlPlaneProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(WireGuardValidationService::class.java)
    }

    /**
     * Validate and enrich server creation request
     */
    fun validateAndEnrich(request: CreateServerRequest): CreateServerRequest {
        logger.debug(
            "Validating server creation request for '{}' in {} mode",
            request.name,
            properties.mode
        )

        val validation = strategy.validateServerCreation(request)

        if (!validation.isValid) {
            logger.warn("Server creation validation failed: {}", validation.errorMessage)
            throw ControlPlaneValidationException(
                validation.errorMessage!!,
                validation.suggestions,
                validation.warningMessage
            )
        }

        val enrichedRequest = strategy.enrichServerRequest(request)

        if (validation.warningMessage != null) {
            logger.warn("Server creation warning: {}", validation.warningMessage)
        }

        logger.debug("Server creation request validated and enriched successfully")
        return enrichedRequest
    }

    /**
     * Validate and enrich client creation request
     */
    fun validateAndEnrich(request: AddClientRequest): AddClientRequest {
        logger.debug(
            "Validating client creation request for '{}' in {} mode",
            request.clientName,
            properties.mode
        )

        val validation = strategy.validateClientCreation(request)

        if (!validation.isValid) {
            logger.warn("Client creation validation failed: {}", validation.errorMessage)
            throw ControlPlaneValidationException(
                validation.errorMessage!!,
                validation.suggestions,
                validation.warningMessage
            )
        }

        val enrichedRequest = strategy.enrichClientRequest(request)

        if (validation.warningMessage != null) {
            logger.warn("Client creation warning: {}", validation.warningMessage)
        }

        logger.debug("Client creation request validated and enriched successfully")
        return enrichedRequest
    }

    /**
     * Validate server update request
     */
    fun validateServerUpdate(serverId: UUID, request: UpdateServerRequest) {
        logger.debug(
            "Validating server update for {} in {} mode",
            serverId,
            properties.mode
        )

        val validation = strategy.validateServerUpdate(serverId, request)

        if (!validation.isValid) {
            logger.warn("Server update validation failed: {}", validation.errorMessage)
            throw ControlPlaneValidationException(
                validation.errorMessage!!,
                validation.suggestions,
                validation.warningMessage
            )
        }

        if (validation.warningMessage != null) {
            logger.warn("Server update warning: {}", validation.warningMessage)
        }

        logger.debug("Server update request validated successfully")
    }

    /**
     * Get current control plane mode for informational purposes
     */
    fun getCurrentMode() = properties.mode

    /**
     * Check if local operations are allowed in current mode
     */
    fun areLocalOperationsAllowed(): Boolean {
        return when (properties.mode) {
            ControlPlaneMode.HYBRID -> true
            ControlPlaneMode.PURE_REMOTE -> false
        }
    }
}