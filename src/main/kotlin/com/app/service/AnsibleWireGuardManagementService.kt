package com.app.service

import com.app.view.AddClientRequest
import com.app.view.CreateServerRequest
import com.app.view.UpdateClientRequest
import com.app.view.UpdateServerRequest
import com.app.view.ServerStatisticsResponse
import com.app.model.AnsibleHost
import com.app.model.IPAddress
import com.app.model.WireGuardClient
import com.app.model.WireGuardServer
import com.app.repository.WireGuardClientRepository
import com.app.repository.WireGuardServerRepository
import com.app.service.ansible.AnsiblePlaybookExecutor
import com.app.service.ansible.AnsibleService
import com.app.utils.WireGuardKeyGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * WireGuard Management Service that deploys to remote hosts via Ansible.
 * Unlike DefaultWireGuardManagementService which manages local interfaces,
 * this service deploys WireGuard to remote AnsibleHost machines.
 */
@Service("AnsibleWireGuardManagementService")
@Transactional
class AnsibleWireGuardManagementService(
    private val serverRepository: WireGuardServerRepository,
    private val clientRepository: WireGuardClientRepository,
    private val keyGenerator: WireGuardKeyGenerator,
    private val ansiblePlaybookExecutor: AnsiblePlaybookExecutor,
    private val ansibleService: AnsibleService,
    private val wireGuardTemplateService: WireGuardTemplateService
) : WireGuardManagementService {

    companion object {
        private val logger = LoggerFactory.getLogger(AnsibleWireGuardManagementService::class.java)
    }

    // ========== Server Management ==========

    override fun createServer(request: CreateServerRequest): WireGuardServer {
        logger.info("Creating WireGuard server for Ansible deployment: ${request.name}")

        // Verify AnsibleHost exists and is usable
        validateAndGetAnsibleHost(request.hostId)

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
            hostId = request.hostId
        )

        return serverRepository.save(server)
    }

    override fun updateServer(serverId: UUID, request: UpdateServerRequest): WireGuardServer? {
        logger.info("Updating WireGuard server: $serverId")

        val server = serverRepository.findById(serverId).orElse(null) ?: return null

        // Update basic properties (deployment hostId cannot be changed)
        request.name?.let { server.name = it }
        request.interfaceName?.let { server.interfaceName = it }
        request.listenPort?.let { server.listenPort = it }
        request.postUp?.let { server.postUp = it }
        request.postDown?.let { server.postDown = it }

        return serverRepository.save(server)
    }

    override fun launchServer(serverId: UUID) {
        logger.info("Deploying WireGuard server via Ansible: $serverId")

        val server = getServerWithAnsibleHost(serverId)
        if (!server.enabled) {
            throw IllegalStateException("Cannot launch disabled server: $serverId")
        }

        val targetHost = validateAndGetAnsibleHost(server.hostId!!)

        // Generate Ansible inventory (target host only)
        val inventoryContent = generateInventoryForHost(targetHost)

        // Generate WireGuard configuration variables
        val extraVars = generateServerDeploymentVars(server)

        // Deploy WireGuard server via Ansible
        val job = ansiblePlaybookExecutor.executePlaybookAsync(
            inventoryContent = inventoryContent,
            playbook = "wireguard-install.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireGuardManagementService",
            notes = "Deploy WireGuard server '${server.name}' to host '${targetHost.name}'"
        )

        logger.info("Started WireGuard server deployment job: ${job.id} for server: ${server.name}")
    }

    override fun stopServer(serverId: UUID) {
        logger.info("Stopping WireGuard server via Ansible: $serverId")

        val server = getServerWithAnsibleHost(serverId)
        val targetHost = validateAndGetAnsibleHost(server.hostId!!)

        val inventoryContent = generateInventoryForHost(targetHost)
        val extraVars = mapOf(
            "wg_action" to "stop",
            "wg_interface" to server.interfaceName,
            "wg_server_name" to server.name
        )

        val job = ansiblePlaybookExecutor.executePlaybookAsync(
            inventoryContent = inventoryContent,
            playbook = "wireguard-control.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireGuardManagementService",
            notes = "Stop WireGuard server '${server.name}' on host '${targetHost.name}'"
        )

        logger.info("Started WireGuard server stop job: ${job.id} for server: ${server.name}")
    }

    override fun isServerInterfaceOnline(serverId: UUID): Boolean {
        logger.info("Checking WireGuard server status via Ansible: $serverId")

        val server = getServerWithAnsibleHost(serverId)
        val targetHost = validateAndGetAnsibleHost(server.hostId!!)

        val inventoryContent = generateInventoryForHost(targetHost)
        val extraVars = mapOf(
            "wg_action" to "status",
            "wg_interface" to server.interfaceName,
            "wg_server_name" to server.name
        )

        val job = ansiblePlaybookExecutor.executePlaybook(
            inventoryContent = inventoryContent,
            playbook = "wireguard-status.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireGuardManagementService",
            notes = "Check status of WireGuard server '${server.name}' on host '${targetHost.name}'"
        )

        return job.isSuccessful() && job.exitCode == 0
    }

    // ========== Client Management ==========

    override fun addClientToServer(serverId: UUID, request: AddClientRequest): WireGuardClient {
        logger.info("Adding client to WireGuard server via Ansible: $serverId")

        val server = getServerWithAnsibleHost(serverId)
        val serverTargetHost = validateAndGetAnsibleHost(server.hostId!!)

        val client = WireGuardClient(
            name = request.clientName,
            publicKey = request.clientPublicKey ?: keyGenerator.generateKeyPair().second,
            privateKey = keyGenerator.generateKeyPair().first,
            allowedIPs = request.addresses.toMutableList(),
            presharedKey = request.presharedKey,
            server = server
        )

        // Set client deployment host
        client.hostId = request.hostId

        val savedClient = clientRepository.save(client)

        // If server is online, deploy configuration
        if (isServerInterfaceOnline(serverId)) {
            // Reload server entity so it includes the newly added client
            val updatedServer = getServerWithAnsibleHost(serverId)

            // Step 1: update server config with client peer
            deployRemoteServerConfiguration(updatedServer, serverTargetHost, savedClient)

            // Step 2: if client is also deployed to a remote host, deploy client config
            if (savedClient.hostId != null) {
                try {
                    val clientTargetHost = validateAndGetAnsibleHost(savedClient.hostId!!)
                    deployRemoteClientConfiguration(savedClient, clientTargetHost, updatedServer)
                    logger.info("Successfully deployed remote client configuration for '${savedClient.name}'")
                } catch (e: Exception) {
                    logger.error("Failed to deploy remote client configuration: ${e.message}")
                    // Server config is updated; client deploy failure does not block saving the record
                }
            }
        }

        return savedClient
    }

    override fun updateClient(serverId: UUID, clientId: UUID, request: UpdateClientRequest): WireGuardClient {
        logger.info("Updating client via Ansible: $clientId on server: $serverId")

        val client = clientRepository.findById(clientId).orElseThrow {
            IllegalArgumentException("Client not found: $clientId")
        }

        val server = getServerWithAnsibleHost(serverId)
        val serverTargetHost = validateAndGetAnsibleHost(server.hostId!!)

        // Check whether hostId changed (client deployment migration)
        val originalHostId = client.hostId
        val newHostId = request.hostId

        // If client moves from one remote host to another, clean up the old host first
        if (originalHostId != null && originalHostId != newHostId) {
            try {
                val originalClientHost = validateAndGetAnsibleHost(originalHostId)
                removeRemoteClientConfiguration(client, originalClientHost)
                logger.info("Successfully cleaned up original client configuration on host '${originalClientHost.name}'")
            } catch (e: Exception) {
                logger.error("Failed to cleanup original client configuration: ${e.message}")
                // Continue; do not block the update
            }
        }

        // Update client properties
        request.clientName?.let { client.name = it }
        request.addresses?.let { client.allowedIPs = it.toMutableList() }
        request.presharedKey?.let { client.presharedKey = it }
        request.persistentKeepalive?.let { client.persistentKeepalive = it }
        request.enabled?.let { client.enabled = it }
        if (request.hostId != null) { client.hostId = request.hostId }

        val savedClient = clientRepository.save(client)

        // Update server and client configuration via Ansible
        if (isServerInterfaceOnline(serverId)) {
            // Reload server entity with latest client state
            val updatedServer = getServerWithAnsibleHost(serverId)

            // Step 1: update server configuration
            deployRemoteServerConfiguration(updatedServer, serverTargetHost, savedClient)

            // Step 2: if client must be deployed to a remote host, deploy client configuration
            if (savedClient.hostId != null) {
                try {
                    val clientTargetHost = validateAndGetAnsibleHost(savedClient.hostId!!)
                    deployRemoteClientConfiguration(savedClient, clientTargetHost, updatedServer)
                    logger.info("Successfully deployed updated client configuration to '${clientTargetHost.name}'")
                } catch (e: Exception) {
                    logger.error("Failed to deploy updated client configuration: ${e.message}")
                    // Server config is updated; client deploy failure does not block record update
                }
            }
        }

        return savedClient
    }

    override fun removeClientFromServer(serverId: UUID, clientId: UUID) {
        logger.info("Removing client via Ansible: $clientId from server: $serverId")

        val client = clientRepository.findById(clientId).orElseThrow {
            IllegalArgumentException("Client not found: $clientId")
        }

        val server = getServerWithAnsibleHost(serverId)
        val serverTargetHost = validateAndGetAnsibleHost(server.hostId!!)

        // If server is offline, delete DB record only
        if (!isServerInterfaceOnline(serverId)) {
            clientRepository.delete(client)
            return
        }

        // Step 1: if client is on a remote host, clean up client config first
        if (client.hostId != null) {
            try {
                val clientTargetHost = validateAndGetAnsibleHost(client.hostId!!)
                removeRemoteClientConfiguration(client, clientTargetHost)
                logger.info("Successfully cleaned up remote client configuration for '${client.name}'")
            } catch (e: Exception) {
                logger.error("Failed to clean up remote client configuration: ${e.message}")
                // Continue; server-side cleanup must still run at minimum
            }
        }

        // Step 2: update server config, remove client peer
        removeClientFromRemoteServerConfiguration(server, serverTargetHost, client)

        // Step 3: persist server (orphanRemoval deletes client record)
        serverRepository.save(server)
    }

    // ========== Query Methods (same implementation) ==========

    override fun getServerWithClients(serverId: UUID): WireGuardServer? {
        return serverRepository.findById(serverId).orElse(null)
    }

    override fun getAllServers(): List<WireGuardServer> {
        return serverRepository.findAll()
    }

    override fun getActiveServers(): List<WireGuardServer> {
        return serverRepository.findAll().filter { it.enabled }
    }

    override fun getServerById(serverId: UUID): WireGuardServer {
        return serverRepository.findById(serverId).orElseThrow {
            IllegalArgumentException("Server not found: $serverId")
        }
    }

    override fun getServerClients(serverId: UUID): List<WireGuardClient> {
        val server = getServerById(serverId)
        return server.clients.toList()
    }

    override fun getActiveServerClients(serverId: UUID): List<WireGuardClient> {
        val server = getServerById(serverId)
        return server.clients.filter { it.enabled }
    }

    override fun getClientById(clientId: UUID): WireGuardClient {
        return clientRepository.findById(clientId).orElseThrow {
            IllegalArgumentException("Client not found: $clientId")
        }
    }

    override fun updateClientStats(
        clientId: UUID,
        lastHandshake: LocalDateTime,
        dataReceived: Long,
        dataSent: Long
    ): WireGuardClient {
        val client = getClientById(clientId)
        client.lastHandshake = lastHandshake
        client.dataReceived = dataReceived
        client.dataSent = dataSent
        return clientRepository.save(client)
    }

    override fun getServerStatistics(serverId: UUID): ServerStatisticsResponse? {
        val server = getServerById(serverId)
        val clients = getServerClients(serverId)

        return ServerStatisticsResponse(
            serverId = serverId,
            serverName = server.name,
            totalClients = clients.size,
            activeClients = clients.count { it.enabled },
            isOnline = isServerInterfaceOnline(serverId),
            totalDataReceived = clients.sumOf { it.dataReceived },
            totalDataSent = clients.sumOf { it.dataSent }
        )
    }

    // ========== Private Helper Methods ==========

    private fun validateAndGetAnsibleHost(hostId: UUID?): AnsibleHost {
        if (hostId == null) {
            throw IllegalArgumentException("AnsibleHost ID is required for Ansible-based deployment")
        }

        val host = ansibleService.getHost(hostId)
        if (!host.enabled) {
            throw IllegalStateException("AnsibleHost is disabled: ${host.name}")
        }

        return host
    }

    private fun getServerWithAnsibleHost(serverId: UUID): WireGuardServer {
        val server = getServerById(serverId)
        if (server.hostId == null) {
            throw IllegalStateException("Server $serverId is not configured for Ansible deployment (no AnsibleHost assigned)")
        }
        return server
    }

    private fun generateInventoryForHost(host: AnsibleHost): String {
        val inventory = StringBuilder()
        inventory.appendLine("[wireguard_servers]")
        inventory.append(host.name)
        inventory.append(" ansible_host=").append(host.ipAddress)

        if (host.sshPort != 22) {
            inventory.append(" ansible_port=").append(host.sshPort)
        }
        if (host.sshUsername.isNotBlank()) {
            inventory.append(" ansible_user=").append(host.sshUsername)
        }

        inventory.appendLine()
        return inventory.toString()
    }

    private fun generateServerDeploymentVars(server: WireGuardServer): Map<String, Any> {
        val configContent = wireGuardTemplateService.generateServerConfig(server)
        return mapOf(
            "wg_interface_name" to server.interfaceName,
            "wg_config_content" to configContent,
            "wg_config_source" to "inline",
            "wg_restart_after_deploy" to true,
            "wg_enable_on_boot" to server.enabled
        )
    }

    /**
     * Deploy WireGuard server configuration to the remote host.
     * Generates the full server config including all clients and deploys it.
     */
    private fun deployRemoteServerConfiguration(
        server: WireGuardServer,
        targetHost: AnsibleHost,
        client: WireGuardClient
    ) {
        val configContent = wireGuardTemplateService.generateServerConfig(server)
        val inventoryContent = generateInventoryForHost(targetHost)
        val extraVars = mapOf(
            "wg_interface_name" to server.interfaceName,
            "wg_config_content" to configContent,
            "wg_config_source" to "inline",
            "wg_restart_after_deploy" to true
        )

        val job = ansiblePlaybookExecutor.executePlaybook(
            inventoryContent = inventoryContent,
            playbook = "wireguard-deploy-config.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireGuardManagementService",
            notes = "Deploy updated configuration with client '${client.name}' to server '${server.name}' on host '${targetHost.name}'"
        )

        if (!job.isSuccessful()) {
            throw RuntimeException("Failed to deploy client configuration: exit code ${job.exitCode}")
        }

        logger.info("Successfully deployed client '${client.name}' to server '${server.name}' configuration")
    }

    /**
     * Remove the client peer from the remote server configuration.
     * Regenerates server config without that client and deploys it.
     */
    private fun removeClientFromRemoteServerConfiguration(
        server: WireGuardServer,
        targetHost: AnsibleHost,
        client: WireGuardClient
    ) {
        // 1. Temporarily remove client and generate new config
        val wasRemoved = server.clients.remove(client)
        if (!wasRemoved) {
            logger.warn("Client ${client.name} was not found in server ${server.name} client list")
            return
        }

        val configContent = wireGuardTemplateService.generateServerConfig(server)
        val inventoryContent = generateInventoryForHost(targetHost)
        val extraVars = mapOf(
            "wg_interface_name" to server.interfaceName,
            "wg_config_content" to configContent,
            "wg_config_source" to "inline",
            "wg_restart_after_deploy" to true
        )

        // 2. Deploy config; restore client on failure
        try {
            val job = ansiblePlaybookExecutor.executePlaybook(
                inventoryContent = inventoryContent,
                playbook = "wireguard-deploy-config.yml",
                extraVars = extraVars,
                triggeredBy = "AnsibleWireGuardManagementService",
                notes = "Deploy updated configuration after removing client '${client.name}' from server '${server.name}' on host '${targetHost.name}'"
            )

            // 3. Check deploy result
            if (!job.isSuccessful()) {
                server.clients.add(client) // Restore client
                throw RuntimeException("Failed to deploy WireGuard configuration: exit code ${job.exitCode}")
            }

            logger.info("Successfully removed client '${client.name}' from server '${server.name}' configuration")

        } catch (e: Exception) {
            server.clients.add(client) // Restore client
            logger.error("Failed to remove client configuration: ${e.message}")
            throw e
        }
    }

    /**
     * Clean up WireGuard client configuration on the remote host
     * (stop interface, remove config files, disable service, etc.).
     */
    private fun removeRemoteClientConfiguration(
        client: WireGuardClient,
        clientTargetHost: AnsibleHost
    ) {
        val inventoryContent = generateInventoryForHost(clientTargetHost)
        val clientInterfaceName = "wg-${client.name.lowercase().replace(" ", "-")}"

        val extraVars = mapOf(
            "wg_client_interface_name" to clientInterfaceName,
            "wg_client_name" to client.name,
            "wg_action" to "cleanup_client"
        )

        val job = ansiblePlaybookExecutor.executePlaybook(
            inventoryContent = inventoryContent,
            playbook = "wireguard-client-cleanup.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireGuardManagementService",
            notes = "Cleanup WireGuard client '${client.name}' configuration on host '${clientTargetHost.name}'"
        )

        if (!job.isSuccessful()) {
            throw RuntimeException("Failed to cleanup remote client configuration: exit code ${job.exitCode}")
        }

        logger.info("Successfully cleaned up remote client configuration for '${client.name}' on host '${clientTargetHost.name}'")
    }

    /**
     * Deploy WireGuard client configuration to the remote host.
     */
    private fun deployRemoteClientConfiguration(
        client: WireGuardClient,
        clientTargetHost: AnsibleHost,
        server: WireGuardServer
    ) {
        val inventoryContent = generateInventoryForHost(clientTargetHost)
        val clientInterfaceName = "wg-${client.name.lowercase().replace(" ", "-")}"

        // Generate client configuration content
        val clientConfig = wireGuardTemplateService.generateClientConfigWithPrivateKey(
            clientPrivateKey = client.privateKey,
            client = client,
            server = server,
            allowAllTraffic = false
        )

        val extraVars = mapOf(
            "wg_client_interface_name" to clientInterfaceName,
            "wg_client_name" to client.name,
            "wg_config_content" to clientConfig,
            "wg_config_source" to "inline",
            "wg_restart_after_deploy" to true,
            "wg_enable_on_boot" to client.enabled,
            "wg_action" to "deploy_client"
        )

        val job = ansiblePlaybookExecutor.executePlaybook(
            inventoryContent = inventoryContent,
            playbook = "wireguard-client-deploy.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireGuardManagementService",
            notes = "Deploy WireGuard client '${client.name}' configuration to host '${clientTargetHost.name}'"
        )

        if (!job.isSuccessful()) {
            throw RuntimeException("Failed to deploy remote client configuration: exit code ${job.exitCode}")
        }

        logger.info("Successfully deployed remote client configuration for '${client.name}' on host '${clientTargetHost.name}'")
    }
}