package com.app.service

import com.app.view.AddClientRequest
import com.app.view.CreateServerRequest
import com.app.view.UpdateClientRequest
import com.app.view.UpdateServerRequest
import com.app.view.ServerStatisticsResponse
import com.app.model.AnsibleHost
import com.app.model.ClientDeploymentStatus
import com.app.model.IPAddress
import com.app.model.WireGuardClient
import com.app.model.WireGuardServer
import com.app.model.isValidWireGuardInterfaceName
import com.app.repository.WireGuardClientRepository
import com.app.repository.WireGuardServerRepository
import com.app.service.ansible.AnsibleInventoryGenerator
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
    private val agentTokenGenerator: AgentTokenGenerator,
    private val serverRepository: WireGuardServerRepository,
    private val clientRepository: WireGuardClientRepository,
    private val keyGenerator: WireGuardKeyGenerator,
    private val ansiblePlaybookExecutor: AnsiblePlaybookExecutor,
    private val ansibleInventoryGenerator: AnsibleInventoryGenerator,
    private val ansibleService: AnsibleService,
    private val wireGuardTemplateService: WireGuardTemplateService,
    private val ipConflictDetectionService: IPConflictDetectionService,
    private val globalConfigurationService: GlobalConfigurationService,
) : WireGuardManagementService {

    companion object {
        private val logger = LoggerFactory.getLogger(AnsibleWireGuardManagementService::class.java)

        /** Must match the group name in [AnsibleInventoryGenerator.inventoryForSinglePlaybookTarget]. */
        private const val ANSIBLE_INVENTORY_GROUP = "wireguard_servers"
    }

    val SERVER_TOKEN_PREFIX = "wg"
    val CLIENT_TOKEN_PREFIX = "wgc"

    // ========== Server Management ==========

    override fun createServer(request: CreateServerRequest): WireGuardServer {
        logger.info("Creating WireGuard server for Ansible deployment: ${request.name}")

        require(!serverRepository.existsByName(request.name)) { "Server with name '${request.name}' already exists" }
        require(!serverRepository.existsByListenPort(request.listenPort)) { "Port ${request.listenPort} is already in use" }

        val ansibleHost = validateAndGetAnsibleHost(request.hostId)

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
            ansibleHost = ansibleHost,
            agentToken = agentTokenGenerator.generateToken(SERVER_TOKEN_PREFIX)
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
        val inventoryContent = ansibleInventoryGenerator.inventoryForSinglePlaybookTarget(targetHost, ANSIBLE_INVENTORY_GROUP)

        // Generate WireGuard configuration variables
        val extraVars = generateServerDeploymentVars(server)

        // Install packages (wg_install) then write config and restart wg-quick (wg_deploy) — see wireguard-server-launch.yml
        logger.info("Started WireGuard server deployment job for server: ${server.name}")
        ansiblePlaybookExecutor.executePlaybook(
            inventoryContent = inventoryContent,
            playbook = "wireguard-server-launch.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireGuardManagementService",
            notes = "Deploy WireGuard server '${server.name}' to host '${targetHost.hostname}'"
        )


    }

    override fun stopServer(serverId: UUID) {
        logger.info("Stopping WireGuard server via Ansible: $serverId")

        val server = getServerWithAnsibleHost(serverId)
        val targetHost = validateAndGetAnsibleHost(server.hostId!!)

        val inventoryContent = ansibleInventoryGenerator.inventoryForSinglePlaybookTarget(targetHost, ANSIBLE_INVENTORY_GROUP)
        val extraVars = mapOf(
            "wg_target_hosts" to ANSIBLE_INVENTORY_GROUP,
            "wg_interface_name" to server.interfaceName,
            "wg_stop_remove_config" to false
        )

        logger.info("Started WireGuard server stop job for server: ${server.name}")
        ansiblePlaybookExecutor.executePlaybook(
            inventoryContent = inventoryContent,
            playbook = "wireguard-stop.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireGuardManagementService",
            notes = "Stop WireGuard server '${server.name}' on host '${targetHost.hostname}'"
        )

    }

    override fun isServerInterfaceOnline(serverId: UUID): Boolean {
        logger.info("Checking WireGuard server status via Ansible: $serverId")

        val server = getServerWithAnsibleHost(serverId)
        val targetHost = validateAndGetAnsibleHost(server.hostId!!)

        val inventoryContent = ansibleInventoryGenerator.inventoryForSinglePlaybookTarget(targetHost, ANSIBLE_INVENTORY_GROUP)
        val extraVars = mapOf(
            "wg_target_hosts" to ANSIBLE_INVENTORY_GROUP,
            "wg_interface_name" to server.interfaceName,
            "wg_expected_state" to "up",
            "wg_listen_port" to server.listenPort,
            "wg_verify_check_listen" to true
        )

        val job = ansiblePlaybookExecutor.executePlaybook(
            inventoryContent = inventoryContent,
            playbook = "wireguard-verify.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireGuardManagementService",
            notes = "Check status of WireGuard server '${server.name}' on host '${targetHost.hostname}'"
        )

        return job.isSuccessful() && job.exitCode == 0
    }

    // ========== Client Management ==========

    override fun addClientToServer(serverId: UUID, request: AddClientRequest): WireGuardClient {
        logger.info("Adding client to WireGuard server via Ansible: $serverId")

        val server = serverRepository.findByIdWithClients(serverId)
            ?: throw IllegalArgumentException("Server not found: $serverId")
        if (server.hostId == null) {
            throw IllegalStateException("Server $serverId is not configured for Ansible deployment (no AnsibleHost assigned)")
        }
        val serverTargetHost = validateAndGetAnsibleHost(server.hostId!!)

        // Check for IP conflicts before creating the client
        val clientIPs = request.peerIPs.toMutableList().apply { addAll(request.allowedIPs) }
        ipConflictDetectionService.validateNewClientIPs(server, clientIPs)

        val interfaceName = request.interfaceName.trim()
        require(interfaceName.isValidWireGuardInterfaceName()) {
            "Client interface name must be wg0 through wg99"
        }
        if (request.hostId != null) {
            require(!clientRepository.existsByAnsibleHostIdAndInterfaceName(request.hostId, interfaceName)) {
                "Interface '$interfaceName' is already in use on the selected Ansible host"
            }
        }



        val trimmedPublic = request.clientPublicKey?.trim()?.takeIf { it.isNotEmpty() }
        val (privateKey, publicKey) = if (trimmedPublic == null) {
            keyGenerator.generateKeyPair()
        } else {
            // User supplied an existing public key (BYOK). Stored private key is only a DB placeholder
            // and does not match; the server peer uses [publicKey]. Do not use stored privateKey for client configs.
            Pair(keyGenerator.generatePrivateKey(), trimmedPublic)
        }
        val ansibleHost = validateAndGetAnsibleHost(request.hostId)

        val globalConfig = globalConfigurationService.getCurrentConfig()
        val client = WireGuardClient(
            name = request.clientName,
            interfaceName = interfaceName,
            publicKey = publicKey,
            privateKey = privateKey,
            peerIPs = request.peerIPs.toMutableList(),
            allowedIPs = request.allowedIPs.toMutableList(),
            presharedKey = request.presharedKey,
            server = server,
            ansibleHost = ansibleHost,
            agentToken = agentTokenGenerator.generateToken(CLIENT_TOKEN_PREFIX)
        ).apply {
            persistentKeepalive = globalConfig.defaultPersistentKeepalive
        }

        server.addClient(client)

        val savedServer = serverRepository.save(server)
        val savedClient = savedServer.clients.find { it.id == client.id }
            ?: throw IllegalStateException("Failed to persist client")

        // If server is online, deploy configuration
        if (isServerInterfaceOnline(serverId)) {
            val updatedServer = serverRepository.findByIdWithClients(serverId)
                ?: throw IllegalStateException("Server not found after save: $serverId")

            // Step 1: update server config with client peer
            deployRemoteServerConfiguration(updatedServer, serverTargetHost, savedClient)

            // Step 2: if client is also deployed to a remote host, deploy client config
            if (savedClient.hostId != null) {
                try {
                    val clientTargetHost = validateAndGetAnsibleHost(savedClient.hostId!!)
                    deployRemoteClientConfiguration(savedClient, clientTargetHost, updatedServer)
                    savedClient.deploymentStatus = ClientDeploymentStatus.DEPLOYED
                    clientRepository.save(savedClient)
                    logger.info("Successfully deployed remote client configuration for '${savedClient.name}'")
                } catch (e: Exception) {
                    logger.error("Failed to deploy remote client configuration: ${e.message}")
                    savedClient.deploymentStatus = ClientDeploymentStatus.DEPLOY_FAILED
                    clientRepository.save(savedClient)
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
        require(client.server.id == serverId) { "Client does not belong to server $serverId" }

        val server = getServerWithAnsibleHost(serverId)
        val serverTargetHost = validateAndGetAnsibleHost(server.hostId!!)

        if (request.hostId != null && request.hostId != client.hostId) {
            throw IllegalArgumentException("Client Ansible host cannot be changed after the client is created")
        }

        val oldInterfaceName = client.interfaceName
        val newInterfaceName = request.interfaceName?.trim()?.takeIf { it.isNotEmpty() } ?: client.interfaceName
        if (request.interfaceName != null) {
            require(newInterfaceName.isValidWireGuardInterfaceName()) {
                "Client interface name must be wg0 through wg99"
            }
            if (client.hostId != null) {
                require(
                    !clientRepository.existsByAnsibleHostIdAndInterfaceNameAndIdNot(
                        client.hostId!!,
                        newInterfaceName,
                        clientId,
                    )
                ) {
                    "Interface '$newInterfaceName' is already in use on this client's Ansible host"
                }
            }
        }

        val interfaceChanging =
            request.interfaceName != null && newInterfaceName != oldInterfaceName

        if (interfaceChanging && client.hostId != null) {
            val clientTargetHost = validateAndGetAnsibleHost(client.hostId!!)
            removeRemoteClientConfiguration(client, clientTargetHost, cleanupInterfaceName = oldInterfaceName)
        }
        // Update client properties (hostId is set only when adding the client)
        request.clientName?.let { client.name = it }
        request.interfaceName?.let { client.interfaceName = newInterfaceName }
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
        request.presharedKey?.let { client.presharedKey = it }
        request.persistentKeepalive?.let { client.persistentKeepalive = it }
        request.enabled?.let { client.enabled = it }

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
                    savedClient.deploymentStatus = ClientDeploymentStatus.DEPLOYED
                    clientRepository.save(savedClient)
                    logger.info("Successfully deployed updated client configuration to '${clientTargetHost.hostname}'")
                } catch (e: Exception) {
                    logger.error("Failed to deploy updated client configuration: ${e.message}")
                    savedClient.deploymentStatus = ClientDeploymentStatus.DEPLOY_FAILED
                    clientRepository.save(savedClient)
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
        require(client.server.id == serverId) { "Client does not belong to server $serverId" }

        val server = getServerWithAnsibleHost(serverId)
        val serverTargetHost = validateAndGetAnsibleHost(server.hostId!!)

        // If server is offline, delete DB record only (no remote config to clean up either)
        if (!isServerInterfaceOnline(serverId)) {
            if (client.hostId != null) {
                client.enabled = false
                client.deploymentStatus = ClientDeploymentStatus.PENDING_REMOVAL
                clientRepository.save(client)
                logger.warn("Server offline — marked client '${client.name}' as PENDING_REMOVAL for later cleanup")
            } else {
                clientRepository.delete(client)
            }
            return
        }

        // Step 1: update server config, remove client peer
        removeClientFromRemoteServerConfiguration(server, serverTargetHost, client)

        // Step 2: if client is on a remote host, clean up client config
        var clientCleanupFailed = false
        if (client.hostId != null) {
            try {
                val clientTargetHost = validateAndGetAnsibleHost(client.hostId!!)
                removeRemoteClientConfiguration(client, clientTargetHost)
                logger.info("Successfully cleaned up remote client configuration for '${client.name}'")
            } catch (e: Exception) {
                logger.error("Failed to clean up remote client configuration: ${e.message}")
                clientCleanupFailed = true
            }
        }

        // Step 3: persist — keep record as PENDING_REMOVAL if cleanup failed
        if (clientCleanupFailed) {
            server.clients.add(client)
            client.enabled = false
            client.deploymentStatus = ClientDeploymentStatus.PENDING_REMOVAL
            serverRepository.save(server)
            logger.warn("Client '${client.name}' marked as PENDING_REMOVAL — retry later to finish cleanup")
        } else {
            serverRepository.save(server)
        }
    }

    override fun retryClientDeployment(serverId: UUID, clientId: UUID): WireGuardClient? {
        val client = clientRepository.findById(clientId).orElseThrow {
            IllegalArgumentException("Client not found: $clientId")
        }
        require(client.server.id == serverId) { "Client does not belong to server $serverId" }
        require(client.hostId != null) { "Client '${client.name}' has no remote host — nothing to retry" }

        return when (client.deploymentStatus) {
            ClientDeploymentStatus.DEPLOY_FAILED -> retryDeploy(client)
            ClientDeploymentStatus.PENDING_REMOVAL -> retryRemovalCleanup(client)
            else -> throw IllegalStateException(
                "Client '${client.name}' deployment status is ${client.deploymentStatus} — nothing to retry"
            )
        }
    }

    override fun getConfigurationByAgentToken(agentToken: String): String {
        throw UnsupportedOperationException("Retrieving configuration by agent token is not supported for Ansible-managed servers/clients")
    }

    private fun retryDeploy(client: WireGuardClient): WireGuardClient {
        val server = getServerWithAnsibleHost(client.server.id)
        val clientTargetHost = validateAndGetAnsibleHost(client.hostId!!)

        logger.info("Retrying deployment of client '${client.name}' to host '${clientTargetHost.hostname}'")
        deployRemoteClientConfiguration(client, clientTargetHost, server)

        client.deploymentStatus = ClientDeploymentStatus.DEPLOYED
        return clientRepository.save(client)
    }

    /**
     * Retry cleanup of a client whose remote config was not removed.
     * On success the DB record is deleted and `null` is returned.
     */
    private fun retryRemovalCleanup(client: WireGuardClient): WireGuardClient? {
        val clientTargetHost = validateAndGetAnsibleHost(client.hostId!!)

        logger.info("Retrying removal cleanup of client '${client.name}' on host '${clientTargetHost.hostname}'")
        removeRemoteClientConfiguration(client, clientTargetHost)

        val server = serverRepository.findByIdWithClients(client.server.id)
            ?: throw IllegalStateException("Server not found: ${client.server.id}")
        server.clients.remove(client)
        serverRepository.save(server)
        logger.info("Client '${client.name}' cleanup succeeded — record deleted")
        return null
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
            throw IllegalStateException("AnsibleHost is disabled: ${host.hostname}")
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

    private fun generateServerDeploymentVars(server: WireGuardServer): Map<String, Any> {
        val configContent = wireGuardTemplateService.generateServerConfig(server)
        return mapOf(
            "wg_target_hosts" to ANSIBLE_INVENTORY_GROUP,
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
        val inventoryContent = ansibleInventoryGenerator.inventoryForSinglePlaybookTarget(targetHost, ANSIBLE_INVENTORY_GROUP)
        val extraVars = mapOf(
            "wg_target_hosts" to ANSIBLE_INVENTORY_GROUP,
            "wg_interface_name" to server.interfaceName,
            "wg_config_content" to configContent,
            "wg_config_source" to "inline",
            "wg_restart_after_deploy" to true,
            "wg_enable_on_boot" to server.enabled
        )

        val job = ansiblePlaybookExecutor.executePlaybook(
            inventoryContent = inventoryContent,
            playbook = "wireguard-deploy-config.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireGuardManagementService",
            notes = "Deploy updated configuration with client '${client.name}' to server '${server.name}' on host '${targetHost.hostname}'"
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
        val inventoryContent = ansibleInventoryGenerator.inventoryForSinglePlaybookTarget(targetHost, ANSIBLE_INVENTORY_GROUP)
        val extraVars = mapOf(
            "wg_target_hosts" to ANSIBLE_INVENTORY_GROUP,
            "wg_interface_name" to server.interfaceName,
            "wg_config_content" to configContent,
            "wg_config_source" to "inline",
            "wg_restart_after_deploy" to true,
            "wg_enable_on_boot" to server.enabled
        )

        // 2. Deploy config; restore client on failure
        try {
            val job = ansiblePlaybookExecutor.executePlaybook(
                inventoryContent = inventoryContent,
                playbook = "wireguard-deploy-config.yml",
                extraVars = extraVars,
                triggeredBy = "AnsibleWireGuardManagementService",
                notes = "Deploy updated configuration after removing client '${client.name}' from server '${server.name}' on host '${targetHost.hostname}'"
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
        clientTargetHost: AnsibleHost,
        cleanupInterfaceName: String = client.interfaceName,
    ) {
        val inventoryContent = ansibleInventoryGenerator.inventoryForSinglePlaybookTarget(clientTargetHost, ANSIBLE_INVENTORY_GROUP)

        val extraVars = mapOf(
            "wg_target_hosts" to ANSIBLE_INVENTORY_GROUP,
            "wg_client_interface_name" to cleanupInterfaceName,
            "wg_client_name" to client.name
        )

        val job = ansiblePlaybookExecutor.executePlaybook(
            inventoryContent = inventoryContent,
            playbook = "wireguard-client-cleanup.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireGuardManagementService",
            notes = "Cleanup WireGuard client '${client.name}' configuration on host '${clientTargetHost.hostname}'"
        )

        if (!job.isSuccessful()) {
            throw RuntimeException("Failed to cleanup remote client configuration: exit code ${job.exitCode}")
        }

        logger.info("Successfully cleaned up remote client configuration for '${client.name}' on host '${clientTargetHost.hostname}'")
    }

    /**
     * Deploy WireGuard client configuration to the remote host.
     */
    private fun deployRemoteClientConfiguration(
        client: WireGuardClient,
        clientTargetHost: AnsibleHost,
        server: WireGuardServer
    ) {
        val inventoryContent = ansibleInventoryGenerator.inventoryForSinglePlaybookTarget(clientTargetHost, ANSIBLE_INVENTORY_GROUP)

        // Generate client configuration content
        val clientConfig = wireGuardTemplateService.generateClientConfigWithPrivateKey(
            clientPrivateKey = client.privateKey,
            client = client,
            server = server
        )

        val extraVars = mapOf(
            "wg_target_hosts" to ANSIBLE_INVENTORY_GROUP,
            "wg_client_interface_name" to client.interfaceName,
            "wg_client_name" to client.name,
            "wg_config_content" to clientConfig,
            "wg_config_source" to "inline",
            "wg_restart_after_deploy" to true,
            "wg_enable_on_boot" to client.enabled
        )

        val job = ansiblePlaybookExecutor.executePlaybook(
            inventoryContent = inventoryContent,
            playbook = "wireguard-client-deploy.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireGuardManagementService",
            notes = "Deploy WireGuard client '${client.name}' configuration to host '${clientTargetHost.hostname}'"
        )

        if (!job.isSuccessful()) {
            throw RuntimeException("Failed to deploy remote client configuration: exit code ${job.exitCode}")
        }

        logger.info("Successfully deployed remote client configuration for '${client.name}' on host '${clientTargetHost.hostname}'")
    }
}