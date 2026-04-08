package com.app.controller

import com.app.view.*
import com.app.model.ServerConfigurationMetadata
import com.app.model.ServerConfigurationPreview
import com.app.service.*
import com.app.utils.ConfigFileNameSanitizer
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*


@RestController
@RequestMapping("/api/public/wireguard")
class PublicWireGuardController(
    private val wireGuardService: WireGuardManagementService
){
    @PostMapping("/configuration/agent-token")
    fun getConfigurationByAgentToken(@RequestBody(required = true) agentTokenRequest: AgentTokenRequest): ResponseEntity<AgentConfigurationResponse> {
        val config = wireGuardService.getConfigurationByAgentToken(agentToken = agentTokenRequest.agentToken)
        return ResponseEntity.ok(config)
    }
}

@RestController
@RequestMapping("/api/private/wireguard")
class WireGuardController(
    private val wireGuardService: WireGuardManagementService,
    private val globalConfigurationService: GlobalConfigurationService,
    private val wireGuardServerEndpointResolver: WireGuardServerEndpointResolver,
    private val templateService: WireGuardTemplateService
) {

    /**
     * Create a new WireGuard server
     */
    @PostMapping("/servers")
    fun createServer(@Valid @RequestBody request: CreateServerRequest): ResponseEntity<ServerResponse> {
        val server = wireGuardService.createServer(request)
        val globalConfig = globalConfigurationService.getCurrentConfig()
        val endpoint = wireGuardServerEndpointResolver.resolve(server, globalConfig)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ServerResponse.from(server, wireGuardService.isServerInterfaceOnline(server.id), endpoint)
        )
    }


    @PostMapping("/servers/{serverId}/start")
    fun launchServer(@PathVariable serverId: UUID): ResponseEntity<Void> {
        wireGuardService.launchServer(serverId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/servers/{serverId}/stop")
    fun stopServer(@PathVariable serverId: UUID): ResponseEntity<Void> {
        wireGuardService.stopServer(serverId)
        return ResponseEntity.ok().build()
    }

    /**
     * Get all servers
     */
    @GetMapping("/servers")
    fun getAllServers(): ResponseEntity<List<ServerResponse>> {
        val servers = wireGuardService.getAllServers()
        val globalConfig = globalConfigurationService.getCurrentConfig()
        val serverResponses = servers.map {
            val endpoint = wireGuardServerEndpointResolver.resolve(it, globalConfig)
            ServerResponse.from(it, wireGuardService.isServerInterfaceOnline(it.id), endpoint)
        }
        return ResponseEntity.ok(serverResponses)
    }

    /**
     * Get active servers only
     */
    @GetMapping("/servers/active")
    fun getActiveServers(): ResponseEntity<List<ServerResponse>> {
        val servers = wireGuardService.getActiveServers()
        val globalConfig = globalConfigurationService.getCurrentConfig()
        val serverResponses = servers.map {
            val endpoint = wireGuardServerEndpointResolver.resolve(it, globalConfig)
            ServerResponse.from(it, wireGuardService.isServerInterfaceOnline(it.id), endpoint)
        }
        return ResponseEntity.ok(serverResponses)
    }

    /**
     * Get server details with clients
     */
    @GetMapping("/servers/{serverId}")
    fun getServerWithClients(@PathVariable serverId: UUID): ResponseEntity<ServerDetailResponse> {
        val server = wireGuardService.getServerWithClients(serverId)
            ?: return ResponseEntity.notFound().build()
        val globalConfig = globalConfigurationService.getCurrentConfig()
        val endpoint = wireGuardServerEndpointResolver.resolve(server, globalConfig)
        return ResponseEntity.ok(ServerDetailResponse.from(server, endpoint))
    }

    /**
     * Get server statistics
     */
    @GetMapping("/servers/{serverId}/stats")
    fun getServerStats(@PathVariable serverId: UUID): ResponseEntity<ServerStatisticsResponse> {
        val stats = wireGuardService.getServerStatistics(serverId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(stats)
    }

    /**
     * Get server configuration preview (JSON format with complete configuration including private key).
     */
    @GetMapping("/servers/{serverId}/preview")
    fun getServerConfigurationPreview(
        @PathVariable serverId: UUID
    ): ResponseEntity<ServerConfigurationPreview> {
        val server = wireGuardService.getServerById(serverId)
        val configContent = templateService.generateServerConfig(server)
        val validationErrors = templateService.validateConfigFormat(configContent)
        val configHash = templateService.generateConfigHash(configContent)

        val sanitizedFileName = ConfigFileNameSanitizer.sanitize(
            originalName = server.name,
            reservedNamePrefix = "server",
            fallback = "server_config"
        )
        val preview = ServerConfigurationPreview(
            fileName = "${sanitizedFileName}.conf",
            content = configContent,
            metadata = ServerConfigurationMetadata(
                serverId = server.id,
                serverName = server.name,
                createdAt = LocalDateTime.now(),
                configHash = configHash,
                validationErrors = validationErrors
            )
        )

        return ResponseEntity.ok(preview)
    }

    /**
     * Download full server configuration file.
     */
    @GetMapping("/servers/{serverId}/download")
    fun downloadServerConfiguration(
        @PathVariable serverId: UUID
    ): ResponseEntity<String> {
        val server = wireGuardService.getServerById(serverId)
        val configContent = templateService.generateServerConfig(server)
        val sanitizedFileName = ConfigFileNameSanitizer.sanitize(
            originalName = server.name,
            reservedNamePrefix = "server",
            fallback = "server_config"
        )

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .header("Content-Disposition", "attachment; filename=\"${sanitizedFileName}.conf\"")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("Expires", "0")
            .body(configContent)
    }

    /**
     * Update an existing WireGuard server
     */
    @PutMapping("/servers/{serverId}")
    fun updateServer(
        @PathVariable serverId: UUID,
        @Valid @RequestBody request: UpdateServerRequest
    ): ResponseEntity<ServerResponse> {
        val server = wireGuardService.updateServer(serverId, request)
            ?: return ResponseEntity.notFound().build()
        val globalConfig = globalConfigurationService.getCurrentConfig()
        val endpoint = wireGuardServerEndpointResolver.resolve(server, globalConfig)
        return ResponseEntity.ok(
            ServerResponse.from(server, wireGuardService.isServerInterfaceOnline(server.id), endpoint)
        )
    }

    /**
     * Delete a WireGuard server
     */
    @DeleteMapping("/servers/{serverId}")
    fun deleteServer(@PathVariable serverId: UUID): ResponseEntity<Void> {
        wireGuardService.deleteServer(serverId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Add existing client to server
     */
    @PostMapping("/servers/{serverId}/clients/add")
    fun addClientToServer(
        @PathVariable serverId: UUID,
        @Valid @RequestBody request: AddClientRequest
    ): ResponseEntity<ClientResponse> {
        val client = wireGuardService.addClientToServer(serverId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ClientResponse.from(client))
    }

    /**
     * Update an existing client (name, allowed IPs, PSK, keepalive, enabled).
     */
    @PutMapping("/servers/{serverId}/clients/{clientId}")
    fun updateClient(
        @PathVariable serverId: UUID,
        @PathVariable clientId: UUID,
        @Valid @RequestBody request: UpdateClientRequest
    ): ResponseEntity<ClientResponse> {
        val client = wireGuardService.updateClient(serverId, clientId, request)
        return ResponseEntity.ok(ClientResponse.from(client))
    }

    /**
     * Get clients for a server
     */
    @GetMapping("/servers/{serverId}/clients")
    fun getServerClients(@PathVariable serverId: UUID): ResponseEntity<List<ClientResponse>> {
        val clients = wireGuardService.getServerClients(serverId)
        val clientResponses = clients.map { ClientResponse.from(it) }
        return ResponseEntity.ok(clientResponses)
    }

    /**
     * Get active clients for a server
     */
    @GetMapping("/servers/{serverId}/clients/active")
    fun getActiveServerClients(@PathVariable serverId: UUID): ResponseEntity<List<ClientResponse>> {
        val clients = wireGuardService.getActiveServerClients(serverId)
        val clientResponses = clients.map { ClientResponse.from(it) }
        return ResponseEntity.ok(clientResponses)
    }

    /**
     * Remove client from server
     */
    @DeleteMapping("/servers/{serverId}/clients/{clientId}")
    fun removeClientFromServer(
        @PathVariable serverId: UUID,
        @PathVariable clientId: UUID
    ): ResponseEntity<Void> {
        wireGuardService.removeClientFromServer(serverId, clientId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Retry a failed client deployment or removal cleanup.
     * Returns the updated client on successful re-deploy, or 204 if the client was
     * successfully cleaned up and deleted (PENDING_REMOVAL case).
     */
    @PostMapping("/servers/{serverId}/clients/{clientId}/retry-deploy")
    fun retryClientDeployment(
        @PathVariable serverId: UUID,
        @PathVariable clientId: UUID
    ): ResponseEntity<ClientResponse> {
        val client = wireGuardService.retryClientDeployment(serverId, clientId)
        return if (client != null) {
            ResponseEntity.ok(ClientResponse.from(client))
        } else {
            ResponseEntity.noContent().build()
        }
    }

}