package com.app.service

import com.app.model.WireGuardClient
import com.app.model.WireGuardServer
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

/**
 * Routes WireGuard operations to [DefaultWireGuardManagementService] (local interfaces) or
 * [AnsibleWireGuardManagementService] (remote Ansible hosts) based on [WireGuardServer.hostId].
 */
@Service
@Primary
@Transactional
class DelegatingWireGuardManagementService(
    private val defaultWireGuardManagementService: DefaultWireGuardManagementService,
    private val ansibleWireGuardManagementService: AnsibleWireGuardManagementService,
    private val serverRepository: WireGuardServerRepository,
) : WireGuardManagementService {

    companion object {
        private val logger = LoggerFactory.getLogger(DelegatingWireGuardManagementService::class.java)
    }

    private fun isAnsibleManaged(server: WireGuardServer): Boolean = server.hostId != null

    private fun implForServer(serverId: UUID): Pair<WireGuardManagementService, WireGuardServer> {
        val server = serverRepository.findById(serverId).orElse(null)
            ?: throw IllegalArgumentException("Server not found: $serverId")
        val impl = if (isAnsibleManaged(server)) ansibleWireGuardManagementService else defaultWireGuardManagementService
        return impl to server
    }

    override fun createServer(request: CreateServerRequest): WireGuardServer {
        return if (request.hostId != null) {
            logger.debug("createServer: Ansible-managed (hostId={})", request.hostId)
            ansibleWireGuardManagementService.createServer(request)
        } else {
            defaultWireGuardManagementService.createServer(request)
        }
    }

    override fun updateServer(serverId: UUID, request: UpdateServerRequest): WireGuardServer? {
        val (impl, _) = implForServer(serverId)
        return impl.updateServer(serverId, request)
    }

    override fun addClientToServer(serverId: UUID, request: AddClientRequest): WireGuardClient {
        val (impl, _) = implForServer(serverId)
        return impl.addClientToServer(serverId, request)
    }

    override fun updateClient(serverId: UUID, clientId: UUID, request: UpdateClientRequest): WireGuardClient {
        val (impl, _) = implForServer(serverId)
        return impl.updateClient(serverId, clientId, request)
    }

    override fun removeClientFromServer(serverId: UUID, clientId: UUID) {
        val (impl, _) = implForServer(serverId)
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
}
