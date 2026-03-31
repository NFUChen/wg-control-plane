package com.module.wgcontrolplane.controller

import com.module.wgcontrolplane.dto.*
import com.module.wgcontrolplane.service.WireGuardManagementService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/wireguard")
class WireGuardController(
    private val wireGuardService: WireGuardManagementService
) {

    /**
     * Create a new WireGuard server
     */
    @PostMapping("/servers")
    fun createServer(@Valid @RequestBody request: CreateServerRequest): ResponseEntity<ServerResponse> {
        val server = wireGuardService.createServer(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ServerResponse.from(server))
    }


    @PostMapping("/server-up/{serverId}")
    fun launchServer(@PathVariable serverId: UUID): ResponseEntity<Void> {
        wireGuardService.launchServer(serverId)
        return ResponseEntity.ok().build()
    }

    /**
     * Get all servers
     */
    @GetMapping("/servers")
    fun getAllServers(): ResponseEntity<List<ServerResponse>> {
        val servers = wireGuardService.getAllServers()
        val serverResponses = servers.map { ServerResponse.from(it) }
        return ResponseEntity.ok(serverResponses)
    }

    /**
     * Get active servers only
     */
    @GetMapping("/servers/active")
    fun getActiveServers(): ResponseEntity<List<ServerResponse>> {
        val servers = wireGuardService.getActiveServers()
        val serverResponses = servers.map { ServerResponse.from(it) }
        return ResponseEntity.ok(serverResponses)
    }

    /**
     * Get server details with clients
     */
    @GetMapping("/servers/{serverId}")
    fun getServerWithClients(@PathVariable serverId: UUID): ResponseEntity<ServerDetailResponse> {
        val server = wireGuardService.getServerWithClients(serverId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ServerDetailResponse.from(server))
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
     * Update an existing WireGuard server
     */
    @PutMapping("/servers/{serverId}")
    fun updateServer(
        @PathVariable serverId: UUID,
        @Valid @RequestBody request: UpdateServerRequest
    ): ResponseEntity<ServerResponse> {
        val server = wireGuardService.updateServer(serverId, request)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ServerResponse.from(server))
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
     * Update client statistics
     */
    @PutMapping("/clients/{clientId}/stats")
    fun updateClientStats(
        @PathVariable clientId: UUID,
        @Valid @RequestBody request: UpdateClientStatsRequest
    ): ResponseEntity<ClientResponse> {
        val client = wireGuardService.updateClientStats(
            clientId,
            request.lastHandshake,
            request.dataReceived,
            request.dataSent
        )
        return ResponseEntity.ok(ClientResponse.from(client))
    }
}