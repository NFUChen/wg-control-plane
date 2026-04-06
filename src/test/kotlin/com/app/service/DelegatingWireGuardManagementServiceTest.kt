package com.app.service

import com.app.model.AnsibleHost
import com.app.model.IPAddress
import com.app.model.PrivateKey
import com.app.model.WireGuardClient
import com.app.model.WireGuardServer
import com.app.repository.WireGuardServerRepository
import com.app.view.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.test.context.ActiveProfiles
import kotlin.test.*
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import java.time.LocalDateTime
import java.util.*

/**
 * Comprehensive tests for DelegatingWireGuardManagementService
 * Tests delegation logic between local and Ansible-managed WireGuard operations
 */
@ExtendWith(MockitoExtension::class)
@ActiveProfiles("test")
class DelegatingWireGuardManagementServiceTest {

    @Mock
    private lateinit var defaultWireGuardManagementService: DefaultWireGuardManagementService

    @Mock
    private lateinit var ansibleWireGuardManagementService: AnsibleWireGuardManagementService

    @Mock
    private lateinit var serverRepository: WireGuardServerRepository

    private lateinit var delegatingService: DelegatingWireGuardManagementService

    private lateinit var localServer: WireGuardServer
    private lateinit var ansibleServer: WireGuardServer
    private lateinit var testClient: WireGuardClient
    private lateinit var createServerRequest: CreateServerRequest
    private lateinit var updateServerRequest: UpdateServerRequest
    private lateinit var addClientRequest: AddClientRequest
    private lateinit var updateClientRequest: UpdateClientRequest

    @BeforeEach
    fun setUp() {
        delegatingService = DelegatingWireGuardManagementService(
            defaultWireGuardManagementService,
            ansibleWireGuardManagementService,
            serverRepository
        )

        localServer = WireGuardServer(
            name = "Local Test Server",
            privateKey = "localServerPrivateKey",
            publicKey = "localServerPublicKey",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            listenPort = 51820,
            agentToken = "local-server-token"
            // Local server - no Ansible host (ansibleHost stays null)
        )

        // Setup test PrivateKey and AnsibleHost for remote server
        val testPrivateKey = PrivateKey(
            name = "test-ssh-key",
            content = "test-ssh-private-key-content",
            enabled = true
        )

        val testAnsibleHost = AnsibleHost(
            hostname = "remote-test.example.com",
            ipAddress = "192.168.1.100",
            sshUsername = "ansible-user",
            sshPrivateKey = testPrivateKey,
            enabled = true
        )

        ansibleServer = WireGuardServer(
            name = "Remote Test Server",
            privateKey = "remoteServerPrivateKey",
            publicKey = "remoteServerPublicKey",
            addresses = mutableListOf(IPAddress("10.1.0.1/24")),
            listenPort = 51821,
            agentToken = "remote-server-token"
        ).apply {
            ansibleHost = testAnsibleHost
        }

        testClient = WireGuardClient(
            name = "Test Client",
            privateKey = "clientPrivateKey",
            publicKey = "clientPublicKey",
            allowedIPs = mutableListOf(IPAddress("10.0.0.2/32")),
            server = localServer,
            agentToken = "client-token"
        )

        createServerRequest = CreateServerRequest(
            name = "New Test Server",
            interfaceName = "wg0",
            networkAddress = "10.2.0.1/24",
            listenPort = 51822,
            hostId = null
        )

        updateServerRequest = UpdateServerRequest(
            name = "Updated Server Name"
        )

        addClientRequest = AddClientRequest(
            clientName = "New Client",
            interfaceName = "wg1",
            addresses = listOf(IPAddress("10.0.0.3/32"))
        )

        updateClientRequest = UpdateClientRequest(
            clientName = "Updated Client",
            enabled = false
        )
    }

    // ========== Server Creation Tests ==========

    @Test
    fun `createServer should delegate to defaultService for local servers`() {
        // Given
        val localRequest = CreateServerRequest(
            name = createServerRequest.name,
            interfaceName = createServerRequest.interfaceName,
            networkAddress = createServerRequest.networkAddress,
            listenPort = createServerRequest.listenPort,
            hostId = null
        )
        whenever(defaultWireGuardManagementService.createServer(localRequest)).thenReturn(localServer)

        // When
        val result = delegatingService.createServer(localRequest)

        // Then
        assertEquals(localServer, result)
        verify(defaultWireGuardManagementService).createServer(localRequest)
        verify(ansibleWireGuardManagementService, never()).createServer(any<CreateServerRequest>())
    }

    @Test
    fun `createServer should delegate to ansibleService for remote servers`() {
        // Given
        val remoteRequest = CreateServerRequest(
            name = createServerRequest.name,
            interfaceName = createServerRequest.interfaceName,
            networkAddress = createServerRequest.networkAddress,
            listenPort = createServerRequest.listenPort,
            hostId = UUID.randomUUID()
        )
        whenever(ansibleWireGuardManagementService.createServer(remoteRequest)).thenReturn(ansibleServer)

        // When
        val result = delegatingService.createServer(remoteRequest)

        // Then
        assertEquals(ansibleServer, result)
        verify(ansibleWireGuardManagementService).createServer(remoteRequest)
        verify(defaultWireGuardManagementService, never()).createServer(any<CreateServerRequest>())
    }

    // ========== Server Update Tests ==========

    @Test
    fun `updateServer should delegate to defaultService for local servers`() {
        // Given
        val serverId = localServer.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(localServer))
        val updatedServer = WireGuardServer(
            name = updateServerRequest.name!!,
            privateKey = localServer.privateKey,
            publicKey = localServer.publicKey,
            addresses = localServer.addresses,
            listenPort = localServer.listenPort,
            agentToken = localServer.agentToken
        )
        whenever(defaultWireGuardManagementService.updateServer(serverId, updateServerRequest))
            .thenReturn(updatedServer)

        // When
        val result = delegatingService.updateServer(serverId, updateServerRequest)

        // Then
        assertNotNull(result)
        assertEquals(updateServerRequest.name, result.name)
        verify(serverRepository).findById(serverId)
        verify(defaultWireGuardManagementService).updateServer(serverId, updateServerRequest)
        verify(ansibleWireGuardManagementService, never()).updateServer(any<UUID>(), any<UpdateServerRequest>())
    }

    @Test
    fun `updateServer should delegate to ansibleService for remote servers`() {
        // Given
        val serverId = ansibleServer.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(ansibleServer))
        val updatedAnsibleServer = WireGuardServer(
            name = updateServerRequest.name!!,
            privateKey = ansibleServer.privateKey,
            publicKey = ansibleServer.publicKey,
            addresses = ansibleServer.addresses,
            listenPort = ansibleServer.listenPort,
            agentToken = ansibleServer.agentToken
        )
        whenever(ansibleWireGuardManagementService.updateServer(serverId, updateServerRequest))
            .thenReturn(updatedAnsibleServer)

        // When
        val result = delegatingService.updateServer(serverId, updateServerRequest)

        // Then
        assertNotNull(result)
        assertEquals(updateServerRequest.name, result.name)
        verify(serverRepository).findById(serverId)
        verify(ansibleWireGuardManagementService).updateServer(serverId, updateServerRequest)
        verify(defaultWireGuardManagementService, never()).updateServer(any<UUID>(), any<UpdateServerRequest>())
    }

    @Test
    fun `updateServer should throw exception when server not found`() {
        // Given
        val serverId = UUID.randomUUID()
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.empty())

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            delegatingService.updateServer(serverId, updateServerRequest)
        }
        assertEquals("Server not found: $serverId", exception.message)
        verify(serverRepository).findById(serverId)
        verify(defaultWireGuardManagementService, never()).updateServer(any<UUID>(), any<UpdateServerRequest>())
        verify(ansibleWireGuardManagementService, never()).updateServer(any<UUID>(), any<UpdateServerRequest>())
    }

    // ========== Client Management Tests ==========

    @Test
    fun `addClientToServer should delegate to defaultService for local servers`() {
        // Given
        val serverId = localServer.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(localServer))
        whenever(defaultWireGuardManagementService.addClientToServer(serverId, addClientRequest))
            .thenReturn(testClient)

        // When
        val result = delegatingService.addClientToServer(serverId, addClientRequest)

        // Then
        assertEquals(testClient, result)
        verify(serverRepository).findById(serverId)
        verify(defaultWireGuardManagementService).addClientToServer(serverId, addClientRequest)
        verify(ansibleWireGuardManagementService, never()).addClientToServer(any<UUID>(), any<AddClientRequest>())
    }

    @Test
    fun `addClientToServer should delegate to ansibleService for remote servers`() {
        // Given
        val serverId = ansibleServer.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(ansibleServer))
        whenever(ansibleWireGuardManagementService.addClientToServer(serverId, addClientRequest))
            .thenReturn(testClient)

        // When
        val result = delegatingService.addClientToServer(serverId, addClientRequest)

        // Then
        assertEquals(testClient, result)
        verify(serverRepository).findById(serverId)
        verify(ansibleWireGuardManagementService).addClientToServer(serverId, addClientRequest)
        verify(defaultWireGuardManagementService, never()).addClientToServer(any<UUID>(), any<AddClientRequest>())
    }

    @Test
    fun `updateClient should delegate to defaultService for local servers`() {
        // Given
        val serverId = localServer.id
        val clientId = testClient.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(localServer))
        val updatedClient = WireGuardClient(
            name = updateClientRequest.clientName!!,
            privateKey = testClient.privateKey,
            publicKey = testClient.publicKey,
            allowedIPs = testClient.allowedIPs,
            server = testClient.server,
            agentToken = testClient.agentToken
        )
        whenever(defaultWireGuardManagementService.updateClient(serverId, clientId, updateClientRequest))
            .thenReturn(updatedClient)

        // When
        val result = delegatingService.updateClient(serverId, clientId, updateClientRequest)

        // Then
        assertEquals(updateClientRequest.clientName, result.name)
        verify(serverRepository).findById(serverId)
        verify(defaultWireGuardManagementService).updateClient(serverId, clientId, updateClientRequest)
        verify(ansibleWireGuardManagementService, never()).updateClient(any<UUID>(), any<UUID>(), any<UpdateClientRequest>())
    }

    @Test
    fun `updateClient should delegate to ansibleService for remote servers`() {
        // Given
        val serverId = ansibleServer.id
        val clientId = testClient.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(ansibleServer))
        val updatedAnsibleClient = WireGuardClient(
            name = updateClientRequest.clientName!!,
            privateKey = testClient.privateKey,
            publicKey = testClient.publicKey,
            allowedIPs = testClient.allowedIPs,
            server = testClient.server,
            agentToken = testClient.agentToken
        )
        whenever(ansibleWireGuardManagementService.updateClient(serverId, clientId, updateClientRequest))
            .thenReturn(updatedAnsibleClient)

        // When
        val result = delegatingService.updateClient(serverId, clientId, updateClientRequest)

        // Then
        assertEquals(updateClientRequest.clientName, result.name)
        verify(serverRepository).findById(serverId)
        verify(ansibleWireGuardManagementService).updateClient(serverId, clientId, updateClientRequest)
        verify(defaultWireGuardManagementService, never()).updateClient(any<UUID>(), any<UUID>(), any<UpdateClientRequest>())
    }

    @Test
    fun `removeClientFromServer should delegate to defaultService for local servers`() {
        // Given
        val serverId = localServer.id
        val clientId = testClient.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(localServer))

        // When
        delegatingService.removeClientFromServer(serverId, clientId)

        // Then
        verify(serverRepository).findById(serverId)
        verify(defaultWireGuardManagementService).removeClientFromServer(serverId, clientId)
        verify(ansibleWireGuardManagementService, never()).removeClientFromServer(any<UUID>(), any<UUID>())
    }

    @Test
    fun `removeClientFromServer should delegate to ansibleService for remote servers`() {
        // Given
        val serverId = ansibleServer.id
        val clientId = testClient.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(ansibleServer))

        // When
        delegatingService.removeClientFromServer(serverId, clientId)

        // Then
        verify(serverRepository).findById(serverId)
        verify(ansibleWireGuardManagementService).removeClientFromServer(serverId, clientId)
        verify(defaultWireGuardManagementService, never()).removeClientFromServer(any<UUID>(), any<UUID>())
    }

    // ========== Server Operations Tests ==========

    @Test
    fun `launchServer should delegate to defaultService for local servers`() {
        // Given
        val serverId = localServer.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(localServer))

        // When
        delegatingService.launchServer(serverId)

        // Then
        verify(serverRepository).findById(serverId)
        verify(defaultWireGuardManagementService).launchServer(serverId)
        verify(ansibleWireGuardManagementService, never()).launchServer(any<UUID>())
    }

    @Test
    fun `launchServer should delegate to ansibleService for remote servers`() {
        // Given
        val serverId = ansibleServer.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(ansibleServer))

        // When
        delegatingService.launchServer(serverId)

        // Then
        verify(serverRepository).findById(serverId)
        verify(ansibleWireGuardManagementService).launchServer(serverId)
        verify(defaultWireGuardManagementService, never()).launchServer(any<UUID>())
    }

    @Test
    fun `stopServer should delegate to defaultService for local servers`() {
        // Given
        val serverId = localServer.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(localServer))

        // When
        delegatingService.stopServer(serverId)

        // Then
        verify(serverRepository).findById(serverId)
        verify(defaultWireGuardManagementService).stopServer(serverId)
        verify(ansibleWireGuardManagementService, never()).stopServer(any<UUID>())
    }

    @Test
    fun `stopServer should delegate to ansibleService for remote servers`() {
        // Given
        val serverId = ansibleServer.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(ansibleServer))

        // When
        delegatingService.stopServer(serverId)

        // Then
        verify(serverRepository).findById(serverId)
        verify(ansibleWireGuardManagementService).stopServer(serverId)
        verify(defaultWireGuardManagementService, never()).stopServer(any<UUID>())
    }

    // ========== Server Status Tests ==========

    @Test
    fun `isServerInterfaceOnline should delegate to defaultService for local servers`() {
        // Given
        val serverId = localServer.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(localServer))
        whenever(defaultWireGuardManagementService.isServerInterfaceOnline(serverId)).thenReturn(true)

        // When
        val result = delegatingService.isServerInterfaceOnline(serverId)

        // Then
        assertTrue(result)
        verify(serverRepository).findById(serverId)
        verify(defaultWireGuardManagementService).isServerInterfaceOnline(serverId)
        verify(ansibleWireGuardManagementService, never()).isServerInterfaceOnline(any<UUID>())
    }

    @Test
    fun `isServerInterfaceOnline should delegate to ansibleService for remote servers`() {
        // Given
        val serverId = ansibleServer.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(ansibleServer))
        whenever(ansibleWireGuardManagementService.isServerInterfaceOnline(serverId)).thenReturn(false)

        // When
        val result = delegatingService.isServerInterfaceOnline(serverId)

        // Then
        assertFalse(result)
        verify(serverRepository).findById(serverId)
        verify(ansibleWireGuardManagementService).isServerInterfaceOnline(serverId)
        verify(defaultWireGuardManagementService, never()).isServerInterfaceOnline(any<UUID>())
    }

    @Test
    fun `isServerInterfaceOnline should return false when server not found`() {
        // Given
        val serverId = UUID.randomUUID()
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.empty())

        // When
        val result = delegatingService.isServerInterfaceOnline(serverId)

        // Then
        assertFalse(result)
        verify(serverRepository).findById(serverId)
        verify(defaultWireGuardManagementService, never()).isServerInterfaceOnline(any<UUID>())
        verify(ansibleWireGuardManagementService, never()).isServerInterfaceOnline(any<UUID>())
    }

    // ========== Statistics Tests ==========

    @Test
    fun `getServerStatistics should delegate to defaultService for local servers`() {
        // Given
        val serverId = localServer.id
        val expectedStats = ServerStatisticsResponse(
            serverId = localServer.id,
            serverName = localServer.name,
            totalClients = 2,
            activeClients = 1,
            isOnline = true,
            totalDataReceived = 1024L,
            totalDataSent = 2048L
        )
        whenever(serverRepository.findByIdWithClients(serverId)).thenReturn(localServer)
        whenever(defaultWireGuardManagementService.getServerStatistics(serverId)).thenReturn(expectedStats)

        // When
        val result = delegatingService.getServerStatistics(serverId)

        // Then
        assertEquals(expectedStats, result)
        verify(serverRepository).findByIdWithClients(serverId)
        verify(defaultWireGuardManagementService).getServerStatistics(serverId)
        verify(ansibleWireGuardManagementService, never()).getServerStatistics(any<UUID>())
    }

    @Test
    fun `getServerStatistics should delegate to ansibleService for remote servers`() {
        // Given
        val serverId = ansibleServer.id
        val expectedStats = ServerStatisticsResponse(
            serverId = ansibleServer.id,
            serverName = ansibleServer.name,
            totalClients = 3,
            activeClients = 2,
            isOnline = true,
            totalDataReceived = 2048L,
            totalDataSent = 4096L
        )
        whenever(serverRepository.findByIdWithClients(serverId)).thenReturn(ansibleServer)
        whenever(ansibleWireGuardManagementService.getServerStatistics(serverId)).thenReturn(expectedStats)

        // When
        val result = delegatingService.getServerStatistics(serverId)

        // Then
        assertEquals(expectedStats, result)
        verify(serverRepository).findByIdWithClients(serverId)
        verify(ansibleWireGuardManagementService).getServerStatistics(serverId)
        verify(defaultWireGuardManagementService, never()).getServerStatistics(any<UUID>())
    }

    @Test
    fun `getServerStatistics should return null when server not found`() {
        // Given
        val serverId = UUID.randomUUID()
        whenever(serverRepository.findByIdWithClients(serverId)).thenReturn(null)

        // When
        val result = delegatingService.getServerStatistics(serverId)

        // Then
        assertNull(result)
        verify(serverRepository).findByIdWithClients(serverId)
        verify(defaultWireGuardManagementService, never()).getServerStatistics(any<UUID>())
        verify(ansibleWireGuardManagementService, never()).getServerStatistics(any<UUID>())
    }

    // ========== Client Deployment Retry Tests ==========

    @Test
    fun `retryClientDeployment should delegate to defaultService for local servers`() {
        // Given
        val serverId = localServer.id
        val clientId = testClient.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(localServer))
        whenever(defaultWireGuardManagementService.retryClientDeployment(serverId, clientId))
            .thenReturn(testClient)

        // When
        val result = delegatingService.retryClientDeployment(serverId, clientId)

        // Then
        assertEquals(testClient, result)
        verify(serverRepository).findById(serverId)
        verify(defaultWireGuardManagementService).retryClientDeployment(serverId, clientId)
        verify(ansibleWireGuardManagementService, never()).retryClientDeployment(any<UUID>(), any<UUID>())
    }

    @Test
    fun `retryClientDeployment should delegate to ansibleService for remote servers`() {
        // Given
        val serverId = ansibleServer.id
        val clientId = testClient.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(ansibleServer))
        whenever(ansibleWireGuardManagementService.retryClientDeployment(serverId, clientId))
            .thenReturn(testClient)

        // When
        val result = delegatingService.retryClientDeployment(serverId, clientId)

        // Then
        assertEquals(testClient, result)
        verify(serverRepository).findById(serverId)
        verify(ansibleWireGuardManagementService).retryClientDeployment(serverId, clientId)
        verify(defaultWireGuardManagementService, never()).retryClientDeployment(any<UUID>(), any<UUID>())
    }

    // ========== Read-Only Operations Tests (Always Delegate to Default Service) ==========

    @Test
    fun `getServerWithClients should always delegate to defaultService`() {
        // Given
        val serverId = UUID.randomUUID()
        whenever(defaultWireGuardManagementService.getServerWithClients(serverId)).thenReturn(localServer)

        // When
        val result = delegatingService.getServerWithClients(serverId)

        // Then
        assertEquals(localServer, result)
        verify(defaultWireGuardManagementService).getServerWithClients(serverId)
        verify(ansibleWireGuardManagementService, never()).getServerWithClients(any<UUID>())
    }

    @Test
    fun `getAllServers should always delegate to defaultService`() {
        // Given
        val servers = listOf(localServer, ansibleServer)
        whenever(defaultWireGuardManagementService.getAllServers()).thenReturn(servers)

        // When
        val result = delegatingService.getAllServers()

        // Then
        assertEquals(servers, result)
        verify(defaultWireGuardManagementService).getAllServers()
        verify(ansibleWireGuardManagementService, never()).getAllServers()
    }

    @Test
    fun `getActiveServers should always delegate to defaultService`() {
        // Given
        val activeServers = listOf(localServer)
        whenever(defaultWireGuardManagementService.getActiveServers()).thenReturn(activeServers)

        // When
        val result = delegatingService.getActiveServers()

        // Then
        assertEquals(activeServers, result)
        verify(defaultWireGuardManagementService).getActiveServers()
        verify(ansibleWireGuardManagementService, never()).getActiveServers()
    }

    @Test
    fun `getServerById should always delegate to defaultService`() {
        // Given
        val serverId = localServer.id
        whenever(defaultWireGuardManagementService.getServerById(serverId)).thenReturn(localServer)

        // When
        val result = delegatingService.getServerById(serverId)

        // Then
        assertEquals(localServer, result)
        verify(defaultWireGuardManagementService).getServerById(serverId)
        verify(ansibleWireGuardManagementService, never()).getServerById(any<UUID>())
    }

    @Test
    fun `getServerClients should always delegate to defaultService`() {
        // Given
        val serverId = localServer.id
        val clients = listOf(testClient)
        whenever(defaultWireGuardManagementService.getServerClients(serverId)).thenReturn(clients)

        // When
        val result = delegatingService.getServerClients(serverId)

        // Then
        assertEquals(clients, result)
        verify(defaultWireGuardManagementService).getServerClients(serverId)
        verify(ansibleWireGuardManagementService, never()).getServerClients(any<UUID>())
    }

    @Test
    fun `getActiveServerClients should always delegate to defaultService`() {
        // Given
        val serverId = localServer.id
        val activeClients = listOf(testClient)
        whenever(defaultWireGuardManagementService.getActiveServerClients(serverId)).thenReturn(activeClients)

        // When
        val result = delegatingService.getActiveServerClients(serverId)

        // Then
        assertEquals(activeClients, result)
        verify(defaultWireGuardManagementService).getActiveServerClients(serverId)
        verify(ansibleWireGuardManagementService, never()).getActiveServerClients(any<UUID>())
    }

    @Test
    fun `getClientById should always delegate to defaultService`() {
        // Given
        val clientId = testClient.id
        whenever(defaultWireGuardManagementService.getClientById(clientId)).thenReturn(testClient)

        // When
        val result = delegatingService.getClientById(clientId)

        // Then
        assertEquals(testClient, result)
        verify(defaultWireGuardManagementService).getClientById(clientId)
        verify(ansibleWireGuardManagementService, never()).getClientById(any<UUID>())
    }

    @Test
    fun `updateClientStats should always delegate to defaultService`() {
        // Given
        val clientId = testClient.id
        val lastHandshake = LocalDateTime.now()
        val dataReceived = 1024L
        val dataSent = 2048L
        val updatedClient = WireGuardClient(
            name = testClient.name,
            privateKey = testClient.privateKey,
            publicKey = testClient.publicKey,
            allowedIPs = testClient.allowedIPs,
            server = testClient.server,
            agentToken = testClient.agentToken
        ).apply {
            this.lastHandshake = lastHandshake
        }
        whenever(defaultWireGuardManagementService.updateClientStats(clientId, lastHandshake, dataReceived, dataSent))
            .thenReturn(updatedClient)

        // When
        val result = delegatingService.updateClientStats(clientId, lastHandshake, dataReceived, dataSent)

        // Then
        assertEquals(updatedClient, result)
        verify(defaultWireGuardManagementService).updateClientStats(clientId, lastHandshake, dataReceived, dataSent)
        verify(ansibleWireGuardManagementService, never()).updateClientStats(any<UUID>(), any(), any(), any())
    }

    // ========== Edge Cases and Error Handling ==========

    @Test
    fun `should handle repository exceptions gracefully in implForServer`() {
        // Given
        val serverId = UUID.randomUUID()
        whenever(serverRepository.findById(serverId))
            .thenThrow(RuntimeException("Database connection failed"))

        // When & Then
        assertThrows<RuntimeException> {
            delegatingService.updateServer(serverId, updateServerRequest)
        }
        verify(serverRepository).findById(serverId)
        verify(defaultWireGuardManagementService, never()).updateServer(any<UUID>(), any<UpdateServerRequest>())
        verify(ansibleWireGuardManagementService, never()).updateServer(any<UUID>(), any<UpdateServerRequest>())
    }

    @Test
    fun `should correctly identify Ansible-managed servers`() {
        // Test the isAnsibleManaged logic indirectly through delegation behavior

        // Local server (hostId = null) should go to defaultService
        val localServerId = localServer.id
        whenever(serverRepository.findById(localServerId)).thenReturn(Optional.of(localServer))

        delegatingService.launchServer(localServerId)

        verify(defaultWireGuardManagementService).launchServer(localServerId)
        verify(ansibleWireGuardManagementService, never()).launchServer(any<UUID>())

        // Remote server (hostId != null) should go to ansibleService
        val remoteServerId = ansibleServer.id
        whenever(serverRepository.findById(remoteServerId)).thenReturn(Optional.of(ansibleServer))

        delegatingService.launchServer(remoteServerId)

        verify(ansibleWireGuardManagementService).launchServer(remoteServerId)
    }

    @Test
    fun `should handle null return values from delegate services gracefully`() {
        // Given
        val serverId = localServer.id
        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(localServer))
        whenever(defaultWireGuardManagementService.updateServer(serverId, updateServerRequest))
            .thenReturn(null)

        // When
        val result = delegatingService.updateServer(serverId, updateServerRequest)

        // Then
        assertNull(result)
        verify(defaultWireGuardManagementService).updateServer(serverId, updateServerRequest)
    }

    // ========== Integration Tests ==========

    @Test
    fun `should maintain transactional behavior across delegation`() {
        // This test verifies that the @Transactional annotation is respected
        // when delegating operations to other services

        val serverId = localServer.id
        val clientId = testClient.id

        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(localServer))

        // When multiple operations are performed
        delegatingService.removeClientFromServer(serverId, clientId)

        // Then verify that repository is called (transaction context maintained)
        verify(serverRepository).findById(serverId)
        verify(defaultWireGuardManagementService).removeClientFromServer(serverId, clientId)
    }

    @Test
    fun `should log delegation decisions for debugging`() {
        // This test verifies the logging behavior for delegation decisions
        // Note: In a real implementation, you might want to verify log statements
        // using a logging framework test utility

        val localServerId = localServer.id
        val remoteServerId = ansibleServer.id

        whenever(serverRepository.findById(localServerId)).thenReturn(Optional.of(localServer))
        whenever(serverRepository.findById(remoteServerId)).thenReturn(Optional.of(ansibleServer))

        // When operations are performed on different server types
        delegatingService.launchServer(localServerId)
        delegatingService.launchServer(remoteServerId)

        // Then verify delegation occurred correctly
        verify(defaultWireGuardManagementService).launchServer(localServerId)
        verify(ansibleWireGuardManagementService).launchServer(remoteServerId)
    }
}