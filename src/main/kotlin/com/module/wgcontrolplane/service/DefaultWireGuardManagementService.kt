package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.dto.AddClientRequest
import com.module.wgcontrolplane.dto.CreateServerRequest
import com.module.wgcontrolplane.dto.ServerStatisticsResponse
import com.module.wgcontrolplane.model.IPAddress
import com.module.wgcontrolplane.model.WireGuardClient
import com.module.wgcontrolplane.model.WireGuardServer
import com.module.wgcontrolplane.repository.WireGuardClientRepository
import com.module.wgcontrolplane.repository.WireGuardServerRepository
import com.module.wgcontrolplane.utils.WireGuardKeyGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*


@Service
@Transactional
class DefaultWireGuardManagementService(
    private val serverRepository: WireGuardServerRepository,
    private val clientRepository: WireGuardClientRepository,
    private val keyGenerator: WireGuardKeyGenerator,
    private val wireGuardCommandService: WireGuardCommandService
) : WireGuardManagementService {

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultWireGuardManagementService::class.java)
    }

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

        // First try to add peer to WireGuard interface (if server is enabled)
        // Only proceed with database save if this succeeds
        if (server.enabled) {
            try {
                val interfaceName = wireGuardCommandService.getInterfaceName(server.name)
                wireGuardCommandService.addPeerToInterface(interfaceName, client)
                logger.info("Successfully added peer to WireGuard interface, proceeding with database save")
            } catch (e: Exception) {
                logger.error("Failed to add peer to WireGuard interface, aborting database save", e)
                throw RuntimeException("Cannot add client: failed to add peer to WireGuard interface", e)
            }
        }

        // Only save to database if WireGuard command succeeded (or server is disabled)
        server.addClient(client)
        val savedServer = serverRepository.save(server)

        // Return the persisted client
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

        // Find the client to remove
        val client = server.clients.find { it.id == clientId }
            ?: throw IllegalArgumentException("Client not found: $clientId")

        // First try to remove peer from WireGuard interface (if server is enabled)
        // Only proceed with database removal if this succeeds
        if (server.enabled) {
            try {
                val interfaceName = wireGuardCommandService.getInterfaceName(server.name)
                wireGuardCommandService.removePeerFromInterface(interfaceName, client.publicKey)
                logger.info("Successfully removed peer from WireGuard interface, proceeding with database removal")
            } catch (e: Exception) {
                logger.error("Failed to remove peer from WireGuard interface, aborting database removal", e)
                throw RuntimeException("Cannot remove client: failed to remove peer from WireGuard interface", e)
            }
        }

        // Only remove from database if WireGuard command succeeded (or server is disabled)
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
    override fun getServerStatistics(serverId: UUID): ServerStatisticsResponse? {
        val server = serverRepository.findByIdWithClients(serverId) ?: return null

        val activeClients = server.clients.filter { it.enabled }
        val onlineClients = activeClients.filter { it.isOnline }

        return ServerStatisticsResponse(
            serverId = server.id,
            serverName = server.name,
            endpoint = server.endpoint,
            listenPort = server.listenPort,
            networkAddress = server.primaryAddress.address,
            totalClients = activeClients.size,
            onlineClients = onlineClients.size,
            offlineClients = activeClients.size - onlineClients.size,
            totalDataReceived = activeClients.sumOf { it.dataReceived },
            totalDataSent = activeClients.sumOf { it.dataSent }
        )
    }
}