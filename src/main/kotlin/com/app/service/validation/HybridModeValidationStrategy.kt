package com.app.service.validation

import com.app.security.config.ControlPlaneProperties
import com.app.view.AddClientRequest
import com.app.view.CreateServerRequest
import com.app.view.UpdateServerRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.util.*

/**
 * Validation strategy for HYBRID mode - allows both local and remote operations
 */
@Component
@ConditionalOnProperty(
    name = ["app.control-plane.mode"],
    havingValue = "HYBRID",
    matchIfMissing = true
)
@Primary
class HybridModeValidationStrategy: ControlPlaneValidationStrategy {

    companion object {
        private val logger = LoggerFactory.getLogger(HybridModeValidationStrategy::class.java)
    }

    override fun validateServerCreation(request: CreateServerRequest): ValidationResult {
        logger.debug("Validating server creation in HYBRID mode: {}", request.name)

        // In hybrid mode, both local and remote servers are allowed
        return ValidationResult(isValid = true)
    }

    override fun validateClientCreation(request: AddClientRequest): ValidationResult {
        logger.debug("Validating client creation in HYBRID mode: {}", request.clientName)

        // In hybrid mode, all deployment modes are allowed
        return ValidationResult(isValid = true)
    }

    override fun validateServerUpdate(serverId: UUID, request: UpdateServerRequest): ValidationResult {
        logger.debug("Validating server update in HYBRID mode: {}", serverId)

        // In hybrid mode, updates are generally allowed
        return ValidationResult(isValid = true)
    }

    override fun enrichServerRequest(request: CreateServerRequest): CreateServerRequest {
        // No enrichment needed in hybrid mode
        return request
    }

    override fun enrichClientRequest(request: AddClientRequest): AddClientRequest {
        // No enrichment needed in hybrid mode
        return request
    }
}