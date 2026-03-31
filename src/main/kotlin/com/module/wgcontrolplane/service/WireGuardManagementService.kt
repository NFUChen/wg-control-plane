package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.dto.*
import com.module.wgcontrolplane.model.*
import java.time.LocalDateTime
import java.util.*

interface WireGuardManagementService {

    /**
     * Create a new WireGuard server
     */
    fun createServer(request: CreateServerRequest): WireGuardServer

    /**
     * Add a client to a server
     */
    fun addClientToServer(serverId: UUID, request: AddClientRequest): WireGuardClient

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

    /**
     * Get clients for a specific server
     */
    fun getServerClients(serverId: UUID): List<WireGuardClient>

    /**
     * Get active clients for a specific server
     */
    fun getActiveServerClients(serverId: UUID): List<WireGuardClient>

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
}
