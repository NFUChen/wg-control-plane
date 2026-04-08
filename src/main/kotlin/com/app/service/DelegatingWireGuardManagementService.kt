package com.app.service

import com.app.model.ClientDeploymentMode
import com.app.model.WireGuardClient
import com.app.model.WireGuardServer
import com.app.repository.WireGuardClientRepository
import com.app.repository.WireGuardServerRepository
import com.app.view.AddClientRequest
import com.app.view.CreateServerRequest
import com.app.view.ServerStatisticsResponse
import com.app.view.UpdateClientRequest
import com.app.view.UpdateServerRequest
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Routes WireGuard operations to [DefaultWireGuardManagementService] (local interfaces) or
 * [AnsibleWireGuardManagementService] (remote Ansible hosts) based on [WireGuardServer.ansibleHost] / [WireGuardServer.hostId].
 */
@Service
@Primary
@Transactional
class DelegatingWireGuardManagementService(
    private val defaultWireGuardManagementService: DefaultWireGuardManagementService,
    private val ansibleWireGuardManagementService: AnsibleWireGuardManagementService,
    private val serverRepository: WireGuardServerRepository,
    private val clientRepository: WireGuardClientRepository,
) : WireGuardManagementService {

    companion object {
        private val logger = LoggerFactory.getLogger(DelegatingWireGuardManagementService::class.java)
    }

    private fun isAnsibleManaged(server: WireGuardServer): Boolean = server.hostId != null

    /**
     * Route to service implementation based on server location
     */
    private fun implForServer(serverId: UUID): Pair<WireGuardManagementService, WireGuardServer> {
        val server = serverRepository.findById(serverId).getOrNull() ?: throw IllegalArgumentException("Server not found: $serverId")
        val impl = if (isAnsibleManaged(server)) ansibleWireGuardManagementService else defaultWireGuardManagementService
        return impl to server
    }

    /**
     * Route to service implementation based on client deployment mode and server location.
     *
     * Routing logic:
     * - ANSIBLE mode client → AnsibleWireGuardManagementService
     * - AGENT/LOCAL mode client on Ansible server → AnsibleWireGuardManagementService
     * - AGENT/LOCAL mode client on local server → DefaultWireGuardManagementService
     */
    private fun implForClient(clientId: UUID): Pair<WireGuardManagementService, WireGuardClient> {
        val client = clientRepository.findById(clientId).orElse(null)
            ?: throw IllegalArgumentException("Client not found: $clientId")

        val impl = when {
            // ANSIBLE mode clients are always managed by AnsibleWireGuardManagementService
            client.deploymentMode == ClientDeploymentMode.ANSIBLE -> {
                logger.debug("Routing ANSIBLE mode client $clientId to AnsibleWireGuardManagementService")
                ansibleWireGuardManagementService
            }
            // AGENT/LOCAL mode clients: route based on server location
            isAnsibleManaged(client.server) -> {
                logger.debug("Routing ${client.deploymentMode} mode client $clientId to AnsibleWireGuardManagementService (server is Ansible-managed)")
                ansibleWireGuardManagementService
            }
            else -> {
                logger.debug("Routing {} mode client {} to DefaultWireGuardManagementService",
                    client.deploymentMode, clientId)
                defaultWireGuardManagementService
            }
        }

        return impl to client
    }

    override fun createServer(request: CreateServerRequest): WireGuardServer {
        return if (request.hostId != null) {
            logger.debug("createServer: Ansible-managed (hostId=${request.hostId})", )
            ansibleWireGuardManagementService.createServer(request)
        } else {
            defaultWireGuardManagementService.createServer(request)
        }
    }

    override fun updateServer(serverId: UUID, request: UpdateServerRequest): WireGuardServer? {
        val (impl, _) = implForServer(serverId)
        return impl.updateServer(serverId, request)
    }

    override fun deleteServer(serverId: UUID) {
        val (impl, _) = implForServer(serverId)
        impl.deleteServer(serverId)
    }

    override fun addClientToServer(serverId: UUID, request: AddClientRequest): WireGuardClient {
        val (impl, _) = implForServer(serverId)
        return impl.addClientToServer(serverId, request)
    }

    override fun updateClient(serverId: UUID, clientId: UUID, request: UpdateClientRequest): WireGuardClient {
        // Route based on client deployment mode
        val (impl, client) = implForClient(clientId)
        logger.debug("updateClient: client '${client.name}' (mode=${client.deploymentMode}) routed to ${impl.javaClass.simpleName}")
        return impl.updateClient(serverId, clientId, request)
    }

    override fun removeClientFromServer(serverId: UUID, clientId: UUID) {
        // Route based on client deployment mode
        val (impl, client) = implForClient(clientId)
        logger.debug("removeClientFromServer: client '${client.name}' (mode=${client.deploymentMode}) routed to ${impl.javaClass.simpleName}")
        impl.removeClientFromServer(serverId, clientId)
    }

    override fun getServerWithClients(serverId: UUID): WireGuardServer? {
        return defaultWireGuardManagementService.getServerWithClients(serverId)
    }

    override fun getAllServers(): List<WireGuardServer> {
        return defaultWireGuardManagementService.getAllServers()
    }

    override fun getActiveServers(): List<WireGuardServer> {
        return defaultWireGuardManagementService.getActiveServers()
    }

    override fun getServerById(serverId: UUID): WireGuardServer {
        return defaultWireGuardManagementService.getServerById(serverId)
    }

    override fun getServerClients(serverId: UUID): List<WireGuardClient> {
        return defaultWireGuardManagementService.getServerClients(serverId)
    }

    override fun getActiveServerClients(serverId: UUID): List<WireGuardClient> {
        return defaultWireGuardManagementService.getActiveServerClients(serverId)
    }

    override fun getClientById(clientId: UUID): WireGuardClient {
        return defaultWireGuardManagementService.getClientById(clientId)
    }

    override fun updateClientStats(
        clientId: UUID,
        lastHandshake: LocalDateTime,
        dataReceived: Long,
        dataSent: Long,
    ): WireGuardClient {
        return defaultWireGuardManagementService.updateClientStats(clientId, lastHandshake, dataReceived, dataSent)
    }

    override fun getServerStatistics(serverId: UUID): ServerStatisticsResponse? {
        val server = serverRepository.findByIdWithClients(serverId) ?: return null
        return if (isAnsibleManaged(server)) {
            ansibleWireGuardManagementService.getServerStatistics(serverId)
        } else {
            defaultWireGuardManagementService.getServerStatistics(serverId)
        }
    }

    override fun launchServer(serverId: UUID) {
        val (impl, _) = implForServer(serverId)
        impl.launchServer(serverId)
    }

    override fun stopServer(serverId: UUID) {
        val (impl, _) = implForServer(serverId)
        impl.stopServer(serverId)
    }

    override fun isServerInterfaceOnline(serverId: UUID): Boolean {
        val server = serverRepository.findById(serverId).orElse(null) ?: return false
        return if (isAnsibleManaged(server)) {
            ansibleWireGuardManagementService.isServerInterfaceOnline(serverId)
        } else {
            defaultWireGuardManagementService.isServerInterfaceOnline(serverId)
        }
    }

    override fun retryClientDeployment(serverId: UUID, clientId: UUID): WireGuardClient? {
        // Route based on client deployment mode
        val (impl, client) = implForClient(clientId)
        logger.debug("retryClientDeployment: client '${client.name}' (mode=${client.deploymentMode}) routed to ${impl.javaClass.simpleName}")
        return impl.retryClientDeployment(serverId, clientId)
    }

    override fun getConfigurationByAgentToken(agentToken: String): AgentConfigurationResponse {
        return defaultWireGuardManagementService.getConfigurationByAgentToken(agentToken)
    }
}
