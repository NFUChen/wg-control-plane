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
@Service("ansibleWireguardManagementService")
@Transactional
class AnsibleWireGuardManagementService(
    private val serverRepository: WireGuardServerRepository,
    private val clientRepository: WireGuardClientRepository,
    private val keyGenerator: WireGuardKeyGenerator,
    private val ansiblePlaybookExecutor: AnsiblePlaybookExecutor,
    private val ansibleService: AnsibleService
) : WireGuardManagementService {

    companion object {
        private val logger = LoggerFactory.getLogger(AnsibleWireGuardManagementService::class.java)
    }

    // ========== Server Management ==========

    override fun createServer(request: CreateServerRequest): WireGuardServer {
        logger.info("Creating WireGuard server for Ansible deployment: ${request.name}")

        // 驗證 AnsibleHost 是否存在和可用
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
            postDown = request.postDown?.trim()?.takeIf { it.isNotEmpty() }
        )
        // 設置 hostId
        server.hostId = request.hostId

        return serverRepository.save(server)
    }

    override fun updateServer(serverId: UUID, request: UpdateServerRequest): WireGuardServer? {
        logger.info("Updating WireGuard server: $serverId")

        val server = serverRepository.findById(serverId).orElse(null) ?: return null

        // 更新基本屬性（不允許更改部署位置 hostId）
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

        // 生成 Ansible inventory（只包含目標主機）
        val inventoryContent = generateInventoryForHost(targetHost)

        // 生成 WireGuard 配置變量
        val extraVars = generateServerDeploymentVars(server)

        // 通過 Ansible 部署 WireGuard 服務器
        val job = ansiblePlaybookExecutor.executePlaybookAsync(
            inventoryContent = inventoryContent,
            playbook = "wireguard-install.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireguardManagementService",
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
            triggeredBy = "AnsibleWireguardManagementService",
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
            triggeredBy = "AnsibleWireguardManagementService",
            notes = "Check status of WireGuard server '${server.name}' on host '${targetHost.name}'"
        )

        return job.isSuccessful() && job.exitCode == 0
    }

    // ========== Client Management ==========

    override fun addClientToServer(serverId: UUID, request: AddClientRequest): WireGuardClient {
        logger.info("Adding client to WireGuard server via Ansible: $serverId")

        val server = getServerWithAnsibleHost(serverId)
        val targetHost = validateAndGetAnsibleHost(server.hostId!!)

        val client = WireGuardClient(
            name = request.clientName,
            publicKey = request.clientPublicKey ?: keyGenerator.generateKeyPair().second,
            privateKey = keyGenerator.generateKeyPair().first,
            allowedIPs = request.addresses.toMutableList(),
            presharedKey = request.presharedKey,
            server = server
        )

        val savedClient = clientRepository.save(client)

        // 如果服務器在線，通過 Ansible 部署客戶端配置
        if (isServerInterfaceOnline(serverId)) {
            deployClientConfigurationViaAnsible(server, targetHost, savedClient)
        }

        return savedClient
    }

    override fun updateClient(serverId: UUID, clientId: UUID, request: UpdateClientRequest): WireGuardClient {
        logger.info("Updating client via Ansible: $clientId on server: $serverId")

        val client = clientRepository.findById(clientId).orElseThrow {
            IllegalArgumentException("Client not found: $clientId")
        }

        val server = getServerWithAnsibleHost(serverId)
        val targetHost = validateAndGetAnsibleHost(server.hostId!!)

        // 更新客戶端屬性
        request.clientName?.let { client.name = it }
        request.addresses?.let { client.allowedIPs = it.toMutableList() }
        request.presharedKey?.let { client.presharedKey = it }
        request.persistentKeepalive?.let { client.persistentKeepalive = it }
        request.enabled?.let { client.enabled = it }

        val updatedClient = client

        val savedClient = clientRepository.save(updatedClient)

        // 通過 Ansible 更新客戶端配置
        if (isServerInterfaceOnline(serverId)) {
            deployClientConfigurationViaAnsible(server, targetHost, savedClient)
        }

        return savedClient
    }

    override fun removeClientFromServer(serverId: UUID, clientId: UUID) {
        logger.info("Removing client via Ansible: $clientId from server: $serverId")

        val client = clientRepository.findById(clientId).orElseThrow {
            IllegalArgumentException("Client not found: $clientId")
        }

        val server = getServerWithAnsibleHost(serverId)
        val targetHost = validateAndGetAnsibleHost(server.hostId!!)

        // 通過 Ansible 移除客戶端配置
        if (isServerInterfaceOnline(serverId)) {
            removeClientConfigurationViaAnsible(server, targetHost, client)
        }

        clientRepository.delete(client)
    }

    // ========== Query Methods (相同的實現) ==========

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
        return mapOf(
            "wg_action" to "deploy_server",
            "wg_server_name" to server.name,
            "wg_interface" to server.interfaceName,
            "wg_listen_port" to server.listenPort,
            "wg_private_key" to server.privateKey,
            "wg_public_key" to server.publicKey,
            "wg_server_enabled" to server.enabled
        )
    }

    private fun deployClientConfigurationViaAnsible(
        server: WireGuardServer,
        targetHost: AnsibleHost,
        client: WireGuardClient
    ) {
        val inventoryContent = generateInventoryForHost(targetHost)
        val extraVars = mapOf(
            "wg_action" to "add_client",
            "wg_interface" to server.interfaceName,
            "wg_client_name" to client.name,
            "wg_client_public_key" to client.publicKey,
            "wg_client_ips" to client.allowedIPs.map { it.address },
            "wg_client_psk" to (client.presharedKey ?: ""),
            "wg_client_keepalive" to client.persistentKeepalive,
            "wg_client_enabled" to client.enabled
        )

        ansiblePlaybookExecutor.executePlaybookAsync(
            inventoryContent = inventoryContent,
            playbook = "wireguard-client-add.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireguardManagementService",
            notes = "Deploy client '${client.name}' to server '${server.name}' on host '${targetHost.name}'"
        )
    }

    private fun removeClientConfigurationViaAnsible(
        server: WireGuardServer,
        targetHost: AnsibleHost,
        client: WireGuardClient
    ) {
        val inventoryContent = generateInventoryForHost(targetHost)
        val extraVars = mapOf(
            "wg_action" to "remove_client",
            "wg_interface" to server.interfaceName,
            "wg_client_name" to client.name,
            "wg_client_public_key" to client.publicKey
        )

        ansiblePlaybookExecutor.executePlaybookAsync(
            inventoryContent = inventoryContent,
            playbook = "wireguard-client-remove.yml",
            extraVars = extraVars,
            triggeredBy = "AnsibleWireguardManagementService",
            notes = "Remove client '${client.name}' from server '${server.name}' on host '${targetHost.name}'"
        )
    }
}