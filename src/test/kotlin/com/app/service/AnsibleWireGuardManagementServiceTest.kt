package com.app.service

import com.app.model.*
import com.app.repository.WireGuardClientRepository
import com.app.repository.WireGuardServerRepository
import com.app.service.ansible.*
import com.app.utils.WireGuardKeyGenerator
import com.app.view.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@ExtendWith(MockitoExtension::class)
@ActiveProfiles("test")
class AnsibleWireGuardManagementServiceTest {

    @Mock
    private lateinit var agentTokenGenerator: AgentTokenGenerator

    @Mock
    private lateinit var serverRepository: WireGuardServerRepository

    @Mock
    private lateinit var clientRepository: WireGuardClientRepository

    @Mock
    private lateinit var keyGenerator: WireGuardKeyGenerator

    @Mock
    private lateinit var ansiblePlaybookExecutor: AnsiblePlaybookExecutor

    @Mock
    private lateinit var ansibleInventoryGenerator: AnsibleInventoryGenerator

    @Mock
    private lateinit var ansibleService: AnsibleService

    @Mock
    private lateinit var wireGuardTemplateService: WireGuardTemplateService

    @Mock
    private lateinit var ipConflictDetectionService: IPConflictDetectionService

    @Mock
    private lateinit var globalConfigurationService: GlobalConfigurationService

    private lateinit var ansibleWireGuardService: AnsibleWireGuardManagementService

    // Test data
    private lateinit var testHost: AnsibleHost
    private lateinit var testServer: WireGuardServer
    private lateinit var testClient: WireGuardClient
    private lateinit var testJob: AnsibleExecutionJob
    private lateinit var testGlobalConfig: GlobalConfiguration

    @BeforeEach
    fun setUp() {
        ansibleWireGuardService = AnsibleWireGuardManagementService(
            agentTokenGenerator = agentTokenGenerator,
            serverRepository = serverRepository,
            clientRepository = clientRepository,
            keyGenerator = keyGenerator,
            ansiblePlaybookExecutor = ansiblePlaybookExecutor,
            ansibleInventoryGenerator = ansibleInventoryGenerator,
            ansibleService = ansibleService,
            wireGuardTemplateService = wireGuardTemplateService,
            ipConflictDetectionService = ipConflictDetectionService,
            globalConfigurationService = globalConfigurationService
        )

        // Setup test data
        val testPrivateKey = PrivateKey(
            name = "test-key",
            content = "test-ssh-private-key-content",
            enabled = true
        )

        testHost = AnsibleHost(
            hostname = "test-host.example.com",
            ipAddress = "192.168.1.100",
            sshUsername = "test-user",
            sshPrivateKey = testPrivateKey,
            enabled = true
        )

        testServer = WireGuardServer(
            name = "Test Server",
            interfaceName = "wg0",
            privateKey = "server-private-key",
            publicKey = "server-public-key",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            listenPort = 51820,
            agentToken = "wg-test-token"
        ).apply {
            ansibleHost = testHost
        }

        testClient = WireGuardClient(
            name = "Test Client",
            interfaceName = "wg1",
            privateKey = "client-private-key",
            publicKey = "client-public-key",
            peerIP = mutableListOf(IPAddress("10.0.0.2/32")),
            allowedIPs = mutableListOf(IPAddress("10.0.0.2/32")),
            server = testServer,
            agentToken = "wgc-test-token"
        ).apply {
            ansibleHost = testHost
        }

        testJob = AnsibleExecutionJob(
            playbook = "test-playbook.yml",
            status = AnsibleExecutionStatus.SUCCESS,
            exitCode = 0,
            triggeredBy = "test",
            inventoryContent = "test-inventory",
            successful = true
        )

        testGlobalConfig = GlobalConfiguration(
            version = 1L,
            config = GlobalConfig(defaultPersistentKeepalive = 25)
        )
    }

    // ========== Server Management Tests ==========

    @Test
    fun `createServer should create new WireGuard server successfully`() {
        // Given
        val request = CreateServerRequest(
            name = "New Server",
            interfaceName = "wg0",
            networkAddress = "10.0.0.1/24",
            listenPort = 51820,
            hostId = testHost.id,
            dnsServers = listOf("8.8.8.8", "1.1.1.1")
        )

        whenever(serverRepository.existsByName(request.name)).thenReturn(false)
        whenever(serverRepository.existsByListenPort(request.listenPort)).thenReturn(false)
        whenever(ansibleService.getHost(testHost.id)).thenReturn(testHost)
        whenever(keyGenerator.generateKeyPair()).thenReturn("private-key" to "public-key")
        whenever(agentTokenGenerator.generateToken("wg")).thenReturn("wg-generated-token")
        whenever(serverRepository.save(any<WireGuardServer>())).thenReturn(testServer)

        // When
        val result = ansibleWireGuardService.createServer(request)

        // Then
        assertNotNull(result)
        assertEquals("Test Server", result.name)
        assertEquals("wg0", result.interfaceName)
        assertEquals(51820, result.listenPort)

        verify(serverRepository).existsByName(request.name)
        verify(serverRepository).existsByListenPort(request.listenPort)
        verify(ansibleService).getHost(testHost.id)
        verify(keyGenerator).generateKeyPair()
        verify(agentTokenGenerator).generateToken("wg")
        verify(serverRepository).save(any<WireGuardServer>())
    }

    @Test
    fun `createServer should throw exception when server name already exists`() {
        // Given
        val request = CreateServerRequest(
            name = "Existing Server",
            interfaceName = "wg0",
            networkAddress = "10.0.0.1/24",
            listenPort = 51820,
            hostId = testHost.id
        )

        whenever(serverRepository.existsByName(request.name)).thenReturn(true)

        // When & Then
        assertThrows<IllegalArgumentException> {
            ansibleWireGuardService.createServer(request)
        }

        verify(serverRepository).existsByName(request.name)
        verify(serverRepository, never()).save(any<WireGuardServer>())
    }

    @Test
    fun `getServerById should return server when found`() {
        // Given
        val serverId = testServer.id

        whenever(serverRepository.findById(serverId)).thenReturn(Optional.of(testServer))

        // When
        val result = ansibleWireGuardService.getServerById(serverId)

        // Then
        assertNotNull(result)
        assertEquals(testServer.id, result.id)
        assertEquals(testServer.name, result.name)

        verify(serverRepository).findById(serverId)
    }

    @Test
    fun `getServerById should throw exception when server not found`() {
        // Given
        val serverId = UUID.randomUUID()

        whenever(serverRepository.findById(serverId)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<IllegalArgumentException> {
            ansibleWireGuardService.getServerById(serverId)
        }

        verify(serverRepository).findById(serverId)
    }

    @Test
    fun `getClientById should return client when found`() {
        // Given
        val clientId = testClient.id

        whenever(clientRepository.findById(clientId)).thenReturn(Optional.of(testClient))

        // When
        val result = ansibleWireGuardService.getClientById(clientId)

        // Then
        assertNotNull(result)
        assertEquals(testClient.id, result.id)
        assertEquals(testClient.name, result.name)

        verify(clientRepository).findById(clientId)
    }

    @Test
    fun `getAllServers should return all servers`() {
        // Given
        val servers = listOf(testServer)
        whenever(serverRepository.findAll()).thenReturn(servers)

        // When
        val result = ansibleWireGuardService.getAllServers()

        // Then
        assertEquals(1, result.size)
        assertEquals(testServer.id, result[0].id)
        verify(serverRepository).findAll()
    }

    @Test
    fun `getActiveServers should return only enabled servers`() {
        // Given
        val activeServer = WireGuardServer(
            name = "Active Server",
            interfaceName = "wg0",
            privateKey = "key1",
            publicKey = "pub1",
            agentToken = "token1"
        ).apply { enabled = true }

        val inactiveServer = WireGuardServer(
            name = "Inactive Server",
            interfaceName = "wg1",
            privateKey = "key2",
            publicKey = "pub2",
            agentToken = "token2"
        ).apply { enabled = false }

        val servers = listOf(activeServer, inactiveServer)

        whenever(serverRepository.findAll()).thenReturn(servers)

        // When
        val result = ansibleWireGuardService.getActiveServers()

        // Then
        assertEquals(1, result.size)
        assertTrue(result[0].enabled)
        verify(serverRepository).findAll()
    }

    @Test
    fun `updateClientStats should update client statistics`() {
        // Given
        val clientId = testClient.id
        val lastHandshake = LocalDateTime.now()
        val dataReceived = 1024L
        val dataSent = 2048L

        whenever(clientRepository.findById(clientId)).thenReturn(Optional.of(testClient))
        whenever(clientRepository.save(any<WireGuardClient>())).thenReturn(testClient)

        // When
        val result = ansibleWireGuardService.updateClientStats(clientId, lastHandshake, dataReceived, dataSent)

        // Then
        assertNotNull(result)
        assertEquals(lastHandshake, result.lastHandshake)
        assertEquals(dataReceived, result.dataReceived)
        assertEquals(dataSent, result.dataSent)

        verify(clientRepository).findById(clientId)
        verify(clientRepository).save(testClient)
    }

    @Test
    fun `addClientToServer should throw exception when server not found`() {
        // Given
        val serverId = UUID.randomUUID()
        val request = AddClientRequest(
            clientName = "New Client",
            interfaceName = "wg1",
            peerIPs = listOf(IPAddress("10.0.0.3/32")),
            allowedIPs = listOf(IPAddress("10.0.0.3/32")),
            hostId = testHost.id
        )

        whenever(serverRepository.findByIdWithClients(serverId)).thenReturn(null)

        // When & Then
        assertThrows<IllegalArgumentException> {
            ansibleWireGuardService.addClientToServer(serverId, request)
        }

        verify(serverRepository).findByIdWithClients(serverId)
        verify(serverRepository, never()).save(any<WireGuardServer>())
    }
}