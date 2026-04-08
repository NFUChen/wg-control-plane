package com.app.service

import com.app.model.ClientDeploymentMode
import com.app.model.WireGuardServer
import com.app.view.AddClientRequest
import org.springframework.stereotype.Component

/**
 * Resolves client deployment mode based on request parameters and server configuration.
 *
 * Deployment modes:
 * - LOCAL: Client deployed on control plane host via wg commands
 * - ANSIBLE: Client deployed to remote host via Ansible
 * - AGENT: Client pulls configuration via agentToken (self-service mode)
 */
@Component
class ClientDeploymentModeResolver {

    /**
     * Resolve deployment mode from request parameters.
     *
     * @param request The client creation request
     * @param server The server this client will be added to
     * @return The determined deployment mode
     * @throws IllegalArgumentException if conflicting deployment modes are specified
     */
    fun resolve(request: AddClientRequest, server: WireGuardServer): ClientDeploymentMode {
        // Validate no conflicting modes specified
        if (request.hostId != null && request.useAgentMode == true) {
            throw IllegalArgumentException(
                "Cannot specify both hostId (ANSIBLE mode) and useAgentMode (AGENT mode). Please choose only one deployment method."
            )
        }

        return when {
            // ANSIBLE mode: Client deployed to specific remote host via Ansible
            request.hostId != null -> {
                ClientDeploymentMode.ANSIBLE
            }

            // AGENT mode: Client pulls configuration via agentToken
            request.useAgentMode == true -> {
                ClientDeploymentMode.AGENT
            }

            // LOCAL mode: Default - deployed on control plane host
            else -> {
                ClientDeploymentMode.LOCAL
            }
        }
    }

    /**
     * Validate deployment mode compatibility with server and management service.
     *
     * @param deploymentMode The resolved deployment mode
     * @param server The server this client will be added to
     * @param isAnsibleService Whether this is called from AnsibleWireGuardManagementService
     * @throws IllegalStateException if deployment mode is incompatible
     */
    fun validateCompatibility(
        deploymentMode: ClientDeploymentMode,
        server: WireGuardServer,
        isAnsibleService: Boolean
    ) {
        when {
            // ANSIBLE mode requires server to have ansibleHost
            deploymentMode == ClientDeploymentMode.ANSIBLE && server.hostId == null -> {
                throw IllegalStateException(
                    "Cannot deploy client in ANSIBLE mode: Server '${server.name}' is not configured for Ansible deployment (no AnsibleHost assigned)"
                )
            }

            // ANSIBLE mode should use AnsibleWireGuardManagementService
            deploymentMode == ClientDeploymentMode.ANSIBLE && !isAnsibleService -> {
                throw UnsupportedOperationException(
                    "ANSIBLE mode clients must be created through AnsibleWireGuardManagementService"
                )
            }

            // LOCAL mode requires server without ansibleHost
            deploymentMode == ClientDeploymentMode.LOCAL && server.hostId != null && !isAnsibleService -> {
                throw IllegalStateException(
                    "Cannot deploy client in LOCAL mode: Server '${server.name}' is configured for Ansible deployment. Use ANSIBLE or AGENT mode instead."
                )
            }
        }
    }

    /**
     * Generate appropriate agent token based on deployment mode.
     *
     * @param deploymentMode The deployment mode
     * @param agentTokenGenerator The token generator
     * @param tokenPrefix The prefix for the token
     * @return Agent token if applicable, null otherwise
     */
    fun generateAgentTokenIfNeeded(
        deploymentMode: ClientDeploymentMode,
        agentTokenGenerator: AgentTokenGenerator,
        tokenPrefix: String
    ): String? {
        return when (deploymentMode) {
            ClientDeploymentMode.AGENT -> agentTokenGenerator.generateToken(tokenPrefix)
            else -> null
        }
    }
}