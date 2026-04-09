package com.app.service.validation

import com.app.model.ClientDeploymentMode
import com.app.security.config.ControlPlaneProperties
import com.app.view.AddClientRequest
import com.app.view.CreateServerRequest
import com.app.view.UpdateServerRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.*

/**
 * Validation strategy for PURE_REMOTE mode - only allows remote operations
 */
@Component
@ConditionalOnProperty(
    name = ["app.control-plane.mode"],
    havingValue = "PURE_REMOTE"
)
class PureRemoteValidationStrategy(
    private val properties: ControlPlaneProperties
) : ControlPlaneValidationStrategy {

    companion object {
        private val logger = LoggerFactory.getLogger(PureRemoteValidationStrategy::class.java)
    }

    override fun validateServerCreation(request: CreateServerRequest): ValidationResult {
        logger.debug("Validating server creation in PURE_REMOTE mode: {}", request.name)

        if (request.hostId == null) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Pure control plane mode requires all servers to be deployed on remote hosts",
                suggestions = listOf(
                    "Please specify a hostId for remote deployment",
                    "Check available Ansible hosts in your inventory"
                ),
                warningMessage = "Local WireGuard operations are disabled in pure control plane mode"
            )
        }

        logger.debug("Server creation validated for remote deployment to hostId: {}", request.hostId)
        return ValidationResult(isValid = true)
    }

    override fun validateClientCreation(request: AddClientRequest): ValidationResult {
        logger.debug("Validating client creation in PURE_REMOTE mode: {}", request.clientName)

        // In pure remote mode, clients must be deployed via ANSIBLE or AGENT mode
        val isLocalMode = request.hostId == null && request.useAgentMode != true

        if (isLocalMode) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Pure control plane mode requires remote client deployment",
                suggestions = listOf(
                    "Set hostId for Ansible deployment to a remote host",
                    "Enable useAgentMode for agent-based deployment",
                    "Consider switching to HYBRID mode if local deployment is needed"
                ),
                warningMessage = "Local WireGuard operations are disabled in pure control plane mode"
            )
        }

        val deploymentMode: ClientDeploymentMode = when {
            request.hostId != null -> ClientDeploymentMode.ANSIBLE
            request.useAgentMode == true -> ClientDeploymentMode.AGENT
            else -> ClientDeploymentMode.LOCAL
        }

        logger.debug("Client creation validated for {} deployment mode", deploymentMode)
        return ValidationResult(isValid = true)
    }

    override fun validateServerUpdate(serverId: UUID, request: UpdateServerRequest): ValidationResult {
        logger.debug("Validating server update in PURE_REMOTE mode: {}", serverId)

        // In pure remote mode, we need to ensure the server remains remotely managed
        // This validation assumes the server is already validated to be remote
        // Additional checks could be added here if needed

        return ValidationResult(isValid = true)
    }

    override fun enrichServerRequest(request: CreateServerRequest): CreateServerRequest {
        // In pure remote mode, we might want to add default remote configuration
        // For now, we'll just return the request as-is
        return request
    }

    override fun enrichClientRequest(request: AddClientRequest): AddClientRequest {
        // In pure remote mode, we could set some default values
        // For example, if useAgentMode is not specified and hostId is null,
        // we might want to default to agent mode
        return request
    }
}