package com.app.service

import com.app.model.*
import com.app.view.AddClientRequest
import com.app.view.CreateServerRequest
import com.app.view.UpdateClientRequest
import com.app.view.UpdateServerRequest
import com.app.view.ServerStatisticsResponse
import com.app.repository.WireGuardClientRepository
import com.app.repository.WireGuardServerRepository
import com.app.security.config.WireGuardProperties
import com.app.utils.ErrorHandlingUtils
import com.app.utils.WireGuardKeyGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.*
import kotlin.jvm.optionals.getOrNull


/**
 * Local WireGuard (wg-quick) on the control plane host. Primary bean is [DelegatingWireGuardManagementService].
 */
@Service
@Transactional
class DefaultWireGuardManagementService(
    private val agentTokenGenerator: AgentTokenGenerator,
    private val serverRepository: WireGuardServerRepository,
    private val clientRepository: WireGuardClientRepository,
    private val keyGenerator: WireGuardKeyGenerator,
    private val wireGuardCommandService: WireGuardCommandService,
    private val wireGuardTemplateService: WireGuardTemplateService,
    private val ipConflictDetectionService: IPConflictDetectionService,
    private val globalConfigurationService: GlobalConfigurationService,
    private val wireGuardProperties: WireGuardProperties,
    private val deploymentModeResolver: ClientDeploymentModeResolver
) : WireGuardManagementService {

    companion object {
        private val logger = LoggerFactory.getLogger(DefaultWireGuardManagementService::class.java)
    }

    val SERVER_TOKEN_PREFIX = "wg"
    val CLIENT_TOKEN_PREFIX = "wgc"

    // Safe call helpers that use the class logger
    private inline fun <T> safeCall(errorMessage: String, block: () -> T): T =
        ErrorHandlingUtils.safeCall(logger, errorMessage, block)

    private inline fun safeCallSilent(block: () -> Unit) =
        ErrorHandlingUtils.safeCallSilent(logger, block)

    /**
     * Create a new WireGuard server
     */
    @Transactional
    override fun createServer(request: CreateServerRequest): WireGuardServer {
        require(request.hostId == null) {
            "Ansible-managed servers must be created with the remote deployment path (hostId is set only for new remote servers)"
        }
        require(!serverRepository.existsByName(request.name)) { "Server with name '${request.name}' already exists" }
        require(!serverRepository.existsByListenPort(request.listenPort)) { "Port ${request.listenPort} is already in use" }

        val (privateKey, publicKey) = keyGenerator.generateKeyPair()

        val server = WireGuardServer(
            name = request.name,
            interfaceName = request.interfaceName,
            privateKey = privateKey,
            publicKey = publicKey,
            addresses = mutableListOf(IPAddress(request.networkAddress)),
            listenPort = request.listenPort,
            dnsServers = request.dnsServers.map { IPAddress(it) }.toMutableList(),
            postUp = request.postUp?.trim()?.takeIf { it.isNotEmpty() },
            postDown = request.postDown?.trim()?.takeIf { it.isNotEmpty() },
            agentToken = agentTokenGenerator.generateToken(SERVER_TOKEN_PREFIX)
        )

        return serverRepository.save(server)
    }

    /**
     * Add a client to a server
     */
    @Transactional
    override fun addClientToServer(serverId: UUID, request: AddClientRequest): WireGuardClient {
        val server = serverRepository.findByIdWithClients(serverId)
            ?: throw IllegalArgumentException("Server not found: $serverId")

        // Resolve deployment mode using resolver
        val deploymentMode = deploymentModeResolver.resolve(request, server)
        deploymentModeResolver.validateCompatibility(deploymentMode, server, isAnsibleService = false)

        logger.info("Creating client '${request.clientName}' in $deploymentMode mode for server: ${server.name}")

        // Check for IP conflicts before creating the client
        val clientIPs = request.peerIPs.toMutableList().apply { addAll(request.allowedIPs) }
        ipConflictDetectionService.validateNewClientIPs(server, clientIPs)

        val interfaceName = request.interfaceName.trim()
        require(interfaceName.isValidWireGuardInterfaceName()) {
            "Client interface name must be wg0 through wg99"
        }

        val (privateKey, publicKey) = keyGenerator.generateKeyPair()
        val globalConfig = globalConfigurationService.getCurrentConfig()

        val client = WireGuardClient(
            name = request.clientName,
            interfaceName = interfaceName,
            privateKey = privateKey,
            publicKey = publicKey,
            peerIPs = request.peerIPs.toMutableList(),
            allowedIPs = request.allowedIPs.toMutableList(),
            presharedKey = request.presharedKey,
            server = server,
            deploymentMode = deploymentMode,
            agentToken = deploymentModeResolver.generateAgentTokenIfNeeded(
                deploymentMode,
                agentTokenGenerator,
                CLIENT_TOKEN_PREFIX
            )
        ).apply {
            // Apply global configuration defaults
            persistentKeepalive = globalConfig.defaultPersistentKeepalive
        }

        // Handle deployment based on mode
        when (deploymentMode) {
            ClientDeploymentMode.LOCAL -> {
                // Add peer to WireGuard interface (if server is enabled)
                if (server.enabled && wireGuardCommandService.isInterfaceRunning(server.interfaceName)) {
                    safeCall("Cannot add client: failed to add peer to WireGuard interface") {
                        wireGuardCommandService.addPeerToInterface(server.interfaceName, client)
                        logger.info("Successfully added peer to WireGuard interface")
                    }
                }
                // Update local configuration file
                safeCall("Cannot add client: failed to update configuration file") {
                    writeServerConfigFile(server)
                    logger.info("Successfully updated configuration file with new client")
                }
            }
            ClientDeploymentMode.AGENT -> {
                // Agent mode: client will pull config via agentToken
                logger.info("Client created in AGENT mode. Token: ${client.agentToken}")
                // No immediate deployment - client will fetch config later
            }
            ClientDeploymentMode.ANSIBLE -> {
                // Should not reach here due to earlier check
                throw IllegalStateException("ANSIBLE mode should be handled by AnsibleWireGuardManagementService")
            }
        }

        // Save to database
        server.addClient(client)
        val savedServer = serverRepository.save(server)

        // Return the persisted client
        return savedServer.clients.find { it.name == client.name && it.privateKey == client.privateKey }
            ?: throw IllegalStateException("Failed to save client")
    }

    @Transactional
    override fun updateClient(serverId: UUID, clientId: UUID, request: UpdateClientRequest): WireGuardClient {
        val server = serverRepository.findByIdWithClients(serverId)
            ?: throw IllegalArgumentException("Server not found: $serverId")

        val client = server.clients.find { it.id == clientId }
            ?: throw IllegalArgumentException("Client not found: $clientId")

        val hadNoChanges =
            request.clientName == null &&
                request.interfaceName == null &&
                request.peerIPs == null &&
                request.allowedIPs == null &&
                request.presharedKey == null &&
                request.persistentKeepalive == null &&
                request.enabled == null
        if (hadNoChanges) {
            return client
        }

        val wasEnabled = client.enabled
        val previousPsk = client.presharedKey

        request.clientName?.let { name ->
            val trimmed = name.trim()
            require(trimmed.length >= 2) { "Client name must be at least 2 characters" }
            client.name = trimmed
        }

        request.interfaceName?.let { raw ->
            val trimmed = raw.trim()
            require(trimmed.isValidWireGuardInterfaceName()) {
                "Client interface name must be wg0 through wg99"
            }
            client.interfaceName = trimmed
        }

        request.peerIPs?.let { peerIPs ->
            ipConflictDetectionService.validateUpdatedClientIPs(server, clientId, peerIPs)
            client.peerIPs.clear()
            client.peerIPs.addAll(peerIPs)
        }
        request.allowedIPs?.let { allowedIPs ->
            ipConflictDetectionService.validateUpdatedClientIPs(server, clientId, allowedIPs)
            client.allowedIPs.clear()
            client.allowedIPs.addAll(allowedIPs)
        }

        request.presharedKey?.let { psk ->
            client.presharedKey = psk.trim().takeIf { it.isNotEmpty() }
        }

        request.persistentKeepalive?.let { ka ->
            require(ka in 0..65535) { "Persistent keepalive must be between 0 and 65535" }
            client.persistentKeepalive = ka
        }

        request.enabled?.let { client.enabled = it }

        val pskChanged =
            (previousPsk ?: "").trim() != (client.presharedKey ?: "").trim()

        val updatedServer = serverRepository.save(server)

        safeCall("Cannot update client: failed to update configuration file") {
            writeServerConfigFile(updatedServer)
        }

        // The running interface can diverge from the DB: when wg is up, apply CLI changes so in-kernel peers match the client we just persisted.
        // If we skip this block (server disabled or interface down), peers will be applied on the next server-up / restart from the config file.
        if (server.enabled && wireGuardCommandService.isInterfaceRunning(server.interfaceName)) {
            when {
                // Disabled → enabled: the peer should not be present yet; add it.
                client.enabled && !wasEnabled -> {
                    safeCall("Cannot update client: failed to add peer to WireGuard interface") {
                        wireGuardCommandService.addPeerToInterface(server.interfaceName, client)
                    }
                }
                // Enabled → disabled: drop the peer from wg (disconnect); on-disk config was already updated by writeServerConfigFile.
                !client.enabled && wasEnabled -> {
                    safeCall("Cannot update client: failed to remove peer from WireGuard interface") {
                        wireGuardCommandService.removePeerFromInterface(server.interfaceName, client.publicKey)
                    }
                }
                // Stays enabled: for allowed-ips / keepalive-only changes, `wg set peer` overwrites the same public key.
                // If the PSK changed, remove then re-add so we do not leave a stale PSK in some environments.
                client.enabled && wasEnabled -> {
                    if (pskChanged) {
                        safeCall("Cannot update client: failed to refresh peer on WireGuard interface") {
                            wireGuardCommandService.removePeerFromInterface(server.interfaceName, client.publicKey)
                            wireGuardCommandService.addPeerToInterface(server.interfaceName, client)
                        }
                    } else {
                        safeCall("Cannot update client: failed to update peer on WireGuard interface") {
                            wireGuardCommandService.addPeerToInterface(server.interfaceName, client)
                        }
                    }
                }
                // Stays disabled (!enabled && !wasEnabled): no peer should be on the interface; nothing to do.
            }
        }

        return updatedServer.clients.find { it.id == clientId }
            ?: throw IllegalStateException("Failed to reload client after update")
    }

    /**
     * Delete a WireGuard server
     */
    @Transactional
    override fun deleteServer(serverId: UUID) {
        val server = serverRepository.findByIdWithClients(serverId)
            ?: throw IllegalArgumentException("Server not found: $serverId")

        logger.info("Starting deletion of server: ${server.name} (ID: ${server.id})")

        // Stop the interface if it's running
        if (wireGuardCommandService.isInterfaceRunning(server.interfaceName)) {
            safeCall("Cannot delete server: failed to stop WireGuard interface") {
                wireGuardCommandService.stopWireGuardInterface(server.interfaceName)
                logger.info("Successfully stopped WireGuard interface: ${server.interfaceName}")
            }
        }

        // Delete configuration file from filesystem
        safeCallSilent {
            val configFile = Paths.get(wireGuardProperties.config.directory, "${server.interfaceName}.conf")
            if (Files.exists(configFile)) {
                Files.delete(configFile)
                logger.info("Successfully deleted configuration file: $configFile")
            }
        }

        // Delete from database (cascade will delete associated clients due to orphanRemoval = true)
        serverRepository.delete(server)
        logger.info("Successfully deleted server from database: ${server.name} (ID: ${server.id})")
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
        if (server.enabled && wireGuardCommandService.isInterfaceRunning(server.interfaceName)) {
            safeCall("Cannot remove client: failed to remove peer from WireGuard interface") {
                wireGuardCommandService.removePeerFromInterface(server.interfaceName, client.publicKey)
                logger.info("Successfully removed peer from WireGuard interface, proceeding with database removal")
            }
        }

        // Only remove from database if WireGuard command succeeded (or server is disabled)
        server.clients.remove(client)
        val updatedServer = serverRepository.save(server)

        // Update local configuration file to remove the client
        safeCall("Cannot remove client: failed to update configuration file") {
            writeServerConfigFile(updatedServer)
            logger.info("Successfully updated configuration file after removing client")
        }
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

    override fun getServerById(serverId: UUID): WireGuardServer {
        return serverRepository.findById(serverId).getOrNull() ?: throw IllegalArgumentException("Server not found: $serverId")
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
     * Get client by ID
     */
    override fun getClientById(clientId: UUID): WireGuardClient {
        return clientRepository.findById(clientId).getOrNull() ?: throw IllegalArgumentException("Client not found: $clientId")
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
            totalClients = activeClients.size,
            activeClients = onlineClients.size,
            isOnline = isServerInterfaceOnline(server.id),
            totalDataReceived = activeClients.sumOf { it.dataReceived },
            totalDataSent = activeClients.sumOf { it.dataSent }
        )
    }

    override fun launchServer(serverId: UUID) {
        val server = serverRepository.findByIdWithClients(serverId)
            ?: throw IllegalArgumentException("Server not found: $serverId")

        if (!server.enabled) {
            logger.warn("Attempting to launch server that is not enabled: ${server.name} (ID: ${server.id})")
            return
        }

        // Check if interface is already running
        val isRunning = wireGuardCommandService.isInterfaceRunning(server.interfaceName)
        if (isRunning) {
            logger.info("WireGuard interface ${server.interfaceName} is already running, stopping to apply latest configuration")
            safeCall("Cannot restart server: failed to stop existing interface") {
                wireGuardCommandService.stopWireGuardInterface(server.interfaceName)
                logger.info("Successfully stopped existing interface: ${server.interfaceName}")
            }
        }

        safeCall("Cannot launch server: failed to write configuration file") {
            // Generate and write configuration file with latest data
            writeServerConfigFile(server)
            logger.info("Successfully wrote configuration file for server: ${server.name}")
        }

        safeCall("Cannot launch server: failed to execute WireGuard command") {
            wireGuardCommandService.launchWireGuardInterface(server.interfaceName)
            logger.info("Successfully launched WireGuard server: ${server.name}, interface name: ${server.interfaceName}, ID: ${server.id}")
        }
    }

    override fun stopServer(serverId: UUID) {
        val server = serverRepository.findByIdWithClients(serverId)
            ?: throw IllegalArgumentException("Server not found: $serverId")

        safeCall("Cannot stop server: failed to execute WireGuard command") {
            wireGuardCommandService.stopWireGuardInterface(server.interfaceName)
            logger.info("Successfully stop WireGuard server: ${server.name}, interface name: ${server.interfaceName}, ID: ${server.id}")
        }
    }

    override fun isServerInterfaceOnline(serverId: UUID): Boolean {
        val server = serverRepository.findById(serverId).orElse(null) ?: return false
        return wireGuardCommandService.isInterfaceRunning(server.interfaceName)
    }

    override fun retryClientDeployment(serverId: UUID, clientId: UUID): WireGuardClient? {
        throw UnsupportedOperationException("Retry deploy is only supported for Ansible-managed servers")
    }

    override fun getConfigurationByAgentToken(agentToken: String): String {
        val server = serverRepository.findByAgentToken(agentToken)
        if (server != null) {
            return wireGuardTemplateService.generateServerConfig(server)
        }

        val client = clientRepository.findByAgentToken(agentToken)
        if (client != null) {
            return wireGuardTemplateService.generateClientConfig(client, client.server)
        }

        throw IllegalArgumentException("No server or client found for the provided agent token")

    }

    /**
     * Write server configuration file to local filesystem
     */
    private fun writeServerConfigFile(server: WireGuardServer) {
        // Generate configuration content using template service
        val configContent = wireGuardTemplateService.generateServerConfig(server)

        // Ensure config directory exists
        val configDir = File(wireGuardProperties.config.directory)
        if (!configDir.exists()) {
            configDir.mkdirs()
            logger.info("Created WireGuard configuration directory: ${wireGuardProperties.config.directory}")
        }

        // Write configuration file
        val configFile = Paths.get(wireGuardProperties.config.directory, "${server.interfaceName}.conf")
        Files.write(
            configFile,
            configContent.toByteArray(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )

        logger.info("Wrote WireGuard configuration file: $configFile")
    }

    /**
     * Update an existing WireGuard server with rollback support
     */
    @Transactional
    override fun updateServer(serverId: UUID, request: UpdateServerRequest): WireGuardServer? {
        val server = serverRepository.findByIdWithClients(serverId) ?: return null

        logger.info("Starting update for server: ${server.name} (ID: ${server.id})")

        // Create snapshot by cloning the original server state
        val originalServer = cloneServer(server)
        val wasRunning = server.enabled && wireGuardCommandService.isInterfaceRunning(server.interfaceName)

        try {
            // Validate constraints before making changes
            validateServerUpdateConstraints(request, server)

            // Determine if restart is needed
            val needsRestart = needsInterfaceRestart(server, request) && wasRunning

            if (needsRestart) {
                logger.info("Server update requires restart. Stopping interface: ${server.interfaceName}")
                wireGuardCommandService.stopWireGuardInterface(server.interfaceName)
            }

            // Apply updates
            applyServerUpdates(server, request)

            // Save to database
            val updatedServer = serverRepository.save(server)
            logger.info("Successfully updated server in database")

            writeServerConfigFile(updatedServer)

            if (needsRestart) {
                wireGuardCommandService.launchWireGuardInterface(updatedServer.interfaceName)
                logger.info("Successfully restarted server with new configuration")
            }

            logger.info("Successfully updated server: ${updatedServer.name} (ID: ${updatedServer.id})")
            return updatedServer

        } catch (e: Exception) {
            logger.error("Server update failed, performing rollback", e)
            rollbackServer(server, originalServer, originalServer.interfaceName, wasRunning)
            throw RuntimeException("Server update failed: ${e.message}", e)
        }
    }

    /**
     * Clone server for rollback snapshot (simple object copy)
     */
    private fun cloneServer(original: WireGuardServer): WireGuardServer {
        return WireGuardServer(
            id = original.id,
            name = original.name,
            interfaceName = original.interfaceName,
            privateKey = original.privateKey,
            publicKey = original.publicKey,
            addresses = original.addresses.toMutableList(),
            listenPort = original.listenPort,
            dnsServers = original.dnsServers.toMutableList(),
            postUp = original.postUp,
            postDown = original.postDown,
            enabled = original.enabled,
            ansibleHost = original.ansibleHost,
            agentToken = original.agentToken
        )
    }

    /**
     * Check if interface restart is needed based on changes
     */
    private fun needsInterfaceRestart(server: WireGuardServer, request: UpdateServerRequest): Boolean {
        // Same trimming rules as applyServerUpdates: blank or whitespace-only means "no script"
        fun trimmedNonBlankOrNull(value: String?): String? =
            value?.trim()?.takeIf { it.isNotEmpty() }

        val interfaceNameChanged =
            request.interfaceName != null && request.interfaceName != server.interfaceName
        val networkAddressChanged =
            request.networkAddress != null && request.networkAddress != server.primaryAddress.address
        val listenPortChanged =
            request.listenPort != null && request.listenPort != server.listenPort
        val postUpChanged =
            request.postUp != null &&
                trimmedNonBlankOrNull(request.postUp) != trimmedNonBlankOrNull(server.postUp)
        val postDownChanged =
            request.postDown != null &&
                trimmedNonBlankOrNull(request.postDown) != trimmedNonBlankOrNull(server.postDown)

        return interfaceNameChanged ||
            networkAddressChanged ||
            listenPortChanged ||
            postUpChanged ||
            postDownChanged
    }

    /**
     * Apply update request to server entity
     */
    private fun applyServerUpdates(server: WireGuardServer, request: UpdateServerRequest) {
        request.name?.let { server.name = it }
        request.interfaceName?.let { server.interfaceName = it }
        request.networkAddress?.let {
            server.addresses.clear()
            server.addresses.add(IPAddress(it))
        }
        request.listenPort?.let { server.listenPort = it }
        request.dnsServers?.let { dnsList ->
            server.dnsServers.clear()
            server.dnsServers.addAll(dnsList.map { IPAddress(it) })
        }
        if (request.postUp != null) {
            server.postUp = request.postUp.trim().takeIf { it.isNotEmpty() }
        }
        if (request.postDown != null) {
            server.postDown = request.postDown.trim().takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Rollback server state using snapshot
     */
    private fun rollbackServer(
        current: WireGuardServer,
        snapshot: WireGuardServer,
        originalInterfaceName: String,
        wasRunning: Boolean
    ) {
        safeCallSilent {
            logger.info("Performing rollback for server: ${current.name}")

            // Restore all properties from snapshot
            current.name = snapshot.name
            current.interfaceName = snapshot.interfaceName
            current.addresses.clear()
            current.addresses.addAll(snapshot.addresses)
            current.listenPort = snapshot.listenPort
            current.dnsServers.clear()
            current.dnsServers.addAll(snapshot.dnsServers)
            current.postUp = snapshot.postUp
            current.postDown = snapshot.postDown
            current.enabled = snapshot.enabled

            // Save rollback state
            serverRepository.save(current)

            // Restore interface if it was running
            if (wasRunning) {
                safeCallSilent {
                    wireGuardCommandService.launchWireGuardInterface(originalInterfaceName)
                    logger.info("Successfully restored original interface during rollback")
                }
            }

            logger.info("Rollback completed successfully")
        }
    }

    /**
     * Validate server update constraints
     */
    private fun validateServerUpdateConstraints(request: UpdateServerRequest, server: WireGuardServer) {
        request.name?.let { newName ->
            if (newName != server.name && serverRepository.existsByName(newName)) {
                throw IllegalArgumentException("Server with name '$newName' already exists")
            }
        }

        request.listenPort?.let { newPort ->
            if (newPort != server.listenPort && serverRepository.existsByListenPort(newPort)) {
                throw IllegalArgumentException("Port $newPort is already in use")
            }
        }

        request.interfaceName?.let { newInterfaceName ->
            if (newInterfaceName != server.interfaceName &&
                serverRepository.existsByInterfaceName(newInterfaceName)) {
                throw IllegalArgumentException("Interface name '$newInterfaceName' is already in use")
            }
        }

        request.networkAddress?.let { newAddress ->
            safeCall("Invalid network address format: $newAddress") {
                IPAddress(newAddress)
            }
        }
    }
}