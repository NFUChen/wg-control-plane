package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.dto.*
import com.module.wgcontrolplane.model.*
import com.module.wgcontrolplane.repository.WireGuardServerRepository
import com.module.wgcontrolplane.repository.WireGuardClientRepository
import com.module.wgcontrolplane.utils.WireGuardKeyGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    fun getServerStatistics(serverId: UUID): Map<String, Any>?
}

@Service
@Transactional
class DefaultWireGuardManagementService(
    private val serverRepository: WireGuardServerRepository,
    private val clientRepository: WireGuardClientRepository,
    private val keyGenerator: WireGuardKeyGenerator
) : WireGuardManagementService {

    /**
     * Create a new WireGuard server
     */
    @Transactional
    override fun createServer(request: CreateServerRequest): WireGuardServer {
        require(!serverRepository.existsByName(request.name)) { "Server with name '${request.name}' already exists" }
        require(!serverRepository.existsByListenPort(request.listenPort)) { "Port ${request.listenPort} is already in use" }

        val (privateKey, publicKey) = keyGenerator.generateKeyPair()

        val server = WireGuardServer(
            name = request.name,
            privateKey = privateKey,
            publicKey = publicKey,
            addresses = mutableListOf(IPAddress(request.networkAddress)),
            listenPort = request.listenPort,
            endpoint = request.endpoint,
            dnsServers = request.dnsServers.map { IPAddress(it) }.toMutableList(),
        )

        return serverRepository.save(server)
    }

    /**
     * Add a client to a server
     */
    @Transactional
    override fun addClientToServer(serverId: UUID, request: AddClientRequest): WireGuardClient {
        val server = serverRepository.findById(serverId)
            .orElseThrow { IllegalArgumentException("Server not found: $serverId") }

        val (privateKey, publicKey) = keyGenerator.generateKeyPair()

        val client = WireGuardClient(
            name = request.clientName,
            privateKey = privateKey,
            publicKey = publicKey,
            allowedIPs = request.addresses.toMutableList(),
            presharedKey = request.presharedKey,
            server = server
        )

        // Add client to server - this sets the server relationship and validates
        server.addClient(client)

        // Save server - cascade will save the client due to CascadeType.ALL
        val savedServer = serverRepository.save(server)

        // Return the persisted client (find it in the saved server's clients)
        return savedServer.clients.find { it.name == client.name && it.privateKey == client.privateKey }
            ?: throw IllegalStateException("Failed to save client")
    }

    /**
     * Remove a client from server
     */
    override fun removeClientFromServer(serverId: UUID, clientId: UUID) {
        // Get server with clients
        val server = serverRepository.findByIdWithClients(serverId)
            ?: throw IllegalArgumentException("Server not found: $serverId")

        // Find and remove the client from the server's collection
        val client = server.clients.find { it.id == clientId }
            ?: throw IllegalArgumentException("Client not found: $clientId")

        // Remove from collection - orphanRemoval will delete from DB
        server.clients.remove(client)
        serverRepository.save(server)
    }

    /**
     * Get server with all its clients
     */
    override fun getServerWithClients(serverId: UUID): WireGuardServer? {
        return serverRepository.findByIdWithClients(serverId)
    }

    /**
     * List all servers
     */
    override fun getAllServers(): List<WireGuardServer> {
        return serverRepository.findAll()
    }

    /**
     * List active servers
     */
    override fun getActiveServers(): List<WireGuardServer> {
        return serverRepository.findByEnabledTrue()
    }

    /**
     * Get clients for a specific server
     */
    override fun getServerClients(serverId: UUID): List<WireGuardClient> {
        return clientRepository.findByServerId(serverId)
    }

    /**
     * Get active clients for a specific server
     */
    override fun getActiveServerClients(serverId: UUID): List<WireGuardClient> {
        return clientRepository.findActiveClientsByServerId(serverId)
    }


    /**
     * Update client connection statistics
     */
    override fun updateClientStats(
        clientId: UUID,
        lastHandshake: LocalDateTime,
        dataReceived: Long,
        dataSent: Long
    ): WireGuardClient {
        val client = clientRepository.findById(clientId)
            .orElseThrow { IllegalArgumentException("Client not found: $clientId") }

        // Update mutable properties directly on the managed entity
        // This is JPA-friendly since these properties are var (mutable)
        client.lastHandshake = lastHandshake
        client.dataReceived = dataReceived
        client.dataSent = dataSent
        // updatedAt will be automatically updated by @UpdateTimestamp

        return clientRepository.save(client)
    }

    /**
     * Get server statistics
     */
    override fun getServerStatistics(serverId: UUID): Map<String, Any>? {
        val server = serverRepository.findByIdWithClients(serverId) ?: return null

        val activeClients = server.clients.filter { it.enabled }
        val onlineClients = activeClients.filter { it.isOnline }

        return mapOf(
            "serverId" to server.id.toString(),
            "serverName" to server.name,
            "endpoint" to server.endpoint,
            "listenPort" to server.listenPort,
            "totalClients" to activeClients.size,
            "onlineClients" to onlineClients.size,
            "offlineClients" to (activeClients.size - onlineClients.size),
            "totalDataReceived" to activeClients.sumOf { it.dataReceived },
            "totalDataSent" to activeClients.sumOf { it.dataSent },
            "networkAddress" to server.primaryAddress.address
        )
    }
}