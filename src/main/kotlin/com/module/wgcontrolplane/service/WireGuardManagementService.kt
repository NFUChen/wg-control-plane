package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.model.*
import com.module.wgcontrolplane.repository.WireGuardServerRepository
import com.module.wgcontrolplane.repository.WireGuardClientRepository
import com.module.wgcontrolplane.utils.WireGuardUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class WireGuardManagementService(
    private val serverRepository: WireGuardServerRepository,
    private val clientRepository: WireGuardClientRepository
) {

    /**
     * Create a new WireGuard server
     */
    fun createServer(
        name: String,
        networkAddress: String, // e.g., "10.0.0.1/24"
        listenPort: Int = 51820,
        endpoint: String, // e.g., "vpn.example.com:51820"
        dnsServers: List<String> = listOf(GOOGLE_DNS)
    ): WireGuardServer {
        require(!serverRepository.existsByName(name)) { "Server with name '$name' already exists" }
        require(!serverRepository.existsByListenPort(listenPort)) { "Port $listenPort is already in use" }

        val (privateKey, _) = WireGuardUtils.generateKeyPair()

        val server = WireGuardServer(
            name = name,
            privateKey = privateKey,
            addresses = mutableListOf(IPAddress(networkAddress)),
            listenPort = listenPort,
            endpoint = endpoint,
            dnsServers = dnsServers.toMutableList()
        )

        return serverRepository.save(server)
    }

    /**
     * Add a client to a server
     */
    fun addClientToServer(
        serverId: UUID,
        clientName: String,
        clientPublicKey: String,
        presharedKey: String? = null
    ): WireGuardClient {
        val server = serverRepository.findById(serverId)
            .orElseThrow { IllegalArgumentException("Server not found: $serverId") }

        require(!clientRepository.existsByPublicKey(clientPublicKey)) {
            "Client with public key already exists"
        }

        // Auto-assign IP address
        val clientIP = server.getNextAvailableClientIP()
            ?: throw IllegalStateException("No available IP addresses in server network")

        val client = WireGuardClient(
            name = clientName,
            publicKey = clientPublicKey,
            allowedIPs = mutableListOf(clientIP),
            presharedKey = presharedKey
        )

        server.addClient(client)
        serverRepository.save(server)

        return client
    }

    /**
     * Create a client with auto-generated keys
     */
    fun createClientForServer(
        serverId: UUID,
        clientName: String
    ): Pair<WireGuardClient, String> {
        val (privateKey, publicKey) = WireGuardUtils.generateKeyPair()
        val client = addClientToServer(serverId, clientName, publicKey)
        return Pair(client, privateKey)
    }

    /**
     * Remove a client from server
     */
    fun removeClientFromServer(serverId: UUID, clientId: UUID) {
        val server = serverRepository.findById(serverId)
            .orElseThrow { IllegalArgumentException("Server not found: $serverId") }

        server.removeClient(clientId)
        clientRepository.deleteById(clientId)
        serverRepository.save(server)
    }

    /**
     * Update client status (enable/disable)
     */
    fun updateClientStatus(clientId: UUID, enabled: Boolean): WireGuardClient {
        val client = clientRepository.findById(clientId)
            .orElseThrow { IllegalArgumentException("Client not found: $clientId") }

        val updatedClient = WireGuardClient(
            id = client.id,
            name = client.name,
            publicKey = client.publicKey,
            presharedKey = client.presharedKey,
            allowedIPs = client.allowedIPs,
            persistentKeepalive = client.persistentKeepalive,
            enabled = enabled,
            lastHandshake = client.lastHandshake,
            dataReceived = client.dataReceived,
            dataSent = client.dataSent,
            server = client.server,
            createdAt = client.createdAt,
            updatedAt = client.updatedAt
        )

        return clientRepository.save(updatedClient)
    }

    /**
     * Get server with all its clients
     */
    fun getServerWithClients(serverId: UUID): WireGuardServer? {
        return serverRepository.findByIdWithClients(serverId)
    }

    /**
     * List all servers
     */
    fun getAllServers(): List<WireGuardServer> {
        return serverRepository.findAll()
    }

    /**
     * List active servers
     */
    fun getActiveServers(): List<WireGuardServer> {
        return serverRepository.findByEnabledTrue()
    }

    /**
     * Get clients for a specific server
     */
    fun getServerClients(serverId: UUID): List<WireGuardClient> {
        return clientRepository.findByServerId(serverId)
    }

    /**
     * Get active clients for a specific server
     */
    fun getActiveServerClients(serverId: UUID): List<WireGuardClient> {
        return clientRepository.findActiveClientsByServerId(serverId)
    }


    /**
     * Update client connection statistics
     */
    fun updateClientStats(
        clientId: UUID,
        lastHandshake: LocalDateTime,
        dataReceived: Long,
        dataSent: Long
    ): WireGuardClient {
        val client = clientRepository.findById(clientId)
            .orElseThrow { IllegalArgumentException("Client not found: $clientId") }

        val updatedClient = WireGuardClient(
            id = client.id,
            name = client.name,
            publicKey = client.publicKey,
            presharedKey = client.presharedKey,
            allowedIPs = client.allowedIPs,
            persistentKeepalive = client.persistentKeepalive,
            enabled = client.enabled,
            lastHandshake = lastHandshake,
            dataReceived = dataReceived,
            dataSent = dataSent,
            server = client.server,
            createdAt = client.createdAt,
            updatedAt = client.updatedAt
        )

        return clientRepository.save(updatedClient)
    }

    /**
     * Get server statistics
     */
    fun getServerStatistics(serverId: UUID): Map<String, Any>? {
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
            "networkAddress" to (server.primaryAddress?.address ?: "")
        )
    }
}