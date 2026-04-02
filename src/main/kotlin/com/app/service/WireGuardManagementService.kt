package com.app.service

import com.app.view.*
import com.app.model.*
import java.time.LocalDateTime
import java.util.*

interface WireGuardManagementService {

    /**
     * Create a new WireGuard server
     */
    fun createServer(request: CreateServerRequest): WireGuardServer

    /**
     * Update an existing WireGuard server
     */
    fun updateServer(serverId: UUID, request: UpdateServerRequest): WireGuardServer?

    /**
     * Add a client to a server
     */
    fun addClientToServer(serverId: UUID, request: AddClientRequest): WireGuardClient

    /**
     * Update an existing client (name, IPs, PSK, keepalive, enabled). Public key is immutable.
     */
    fun updateClient(serverId: UUID, clientId: UUID, request: UpdateClientRequest): WireGuardClient

    /**
     * Remove a client from server
     */
    fun removeClientFromServer(serverId: UUID, clientId: UUID)
    /**
     * Get server with all its clients
     */
    fun getServerWithClients(serverId: UUID): WireGuardServer?

    /**
     * List all servers
     */
    fun getAllServers(): List<WireGuardServer>

    /**
     * List active servers
     */
    fun getActiveServers(): List<WireGuardServer>

    fun getServerById(serverId: UUID): WireGuardServer

    /**
     * Get clients for a specific server
     */
    fun getServerClients(serverId: UUID): List<WireGuardClient>

    /**
     * Get active clients for a specific server
     */
    fun getActiveServerClients(serverId: UUID): List<WireGuardClient>

    /**
     * Get client by ID
     */
    fun getClientById(clientId: UUID): WireGuardClient

    /**
     * Update client connection statistics
     */
    fun updateClientStats(
        clientId: UUID,
        lastHandshake: LocalDateTime,
        dataReceived: Long,
        dataSent: Long
    ): WireGuardClient

    /**
     * Get server statistics
     */
    fun getServerStatistics(serverId: UUID): ServerStatisticsResponse?

    fun launchServer(serverId: UUID)

    fun stopServer(serverId: UUID)

    /** Whether the WireGuard interface for this server is currently up (wg is running). */
    fun isServerInterfaceOnline(serverId: UUID): Boolean

    /**
     * Retry a failed client deployment or a failed removal cleanup.
     * Only applicable for Ansible-managed clients whose [WireGuardClient.deploymentStatus]
     * is [ClientDeploymentStatus.DEPLOY_FAILED] or [ClientDeploymentStatus.PENDING_REMOVAL].
     *
     * @return the updated client, or `null` if the client was deleted after successful cleanup.
     */
    fun retryClientDeployment(serverId: UUID, clientId: UUID): WireGuardClient?
}
