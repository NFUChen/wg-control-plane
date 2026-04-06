package com.app.service

import com.app.model.IPAddress
import com.app.model.WireGuardClient
import com.app.model.WireGuardServer
import com.app.repository.WireGuardClientRepository
import com.app.repository.WireGuardServerRepository
import com.app.security.config.WireGuardProperties
import com.app.utils.WireGuardKeyGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for DefaultWireGuardManagementService.getConfigurationByAgentToken()
 * Verifies configuration retrieval by agent token for both servers and clients
 */
@ExtendWith(MockitoExtension::class)
@ActiveProfiles("test")
class DefaultWireGuardManagementServiceGetConfigurationByAgentTokenTest {

    @Mock
    private lateinit var agentTokenGenerator: AgentTokenGenerator

    @Mock
    private lateinit var serverRepository: WireGuardServerRepository

    @Mock
    private lateinit var clientRepository: WireGuardClientRepository

    @Mock
    private lateinit var keyGenerator: WireGuardKeyGenerator

    @Mock
    private lateinit var wireGuardCommandService: WireGuardCommandService

    @Mock
    private lateinit var wireGuardTemplateService: WireGuardTemplateService

    @Mock
    private lateinit var ipConflictDetectionService: IPConflictDetectionService

    @Mock
    private lateinit var globalConfigurationService: GlobalConfigurationService

    @Mock
    private lateinit var wireGuardProperties: WireGuardProperties

    private lateinit var defaultWireGuardService: DefaultWireGuardManagementService

    private lateinit var testServer: WireGuardServer
    private lateinit var testClient: WireGuardClient

    // Test constants
    private val serverAgentToken = "wg-server-token-123"
    private val clientAgentToken = "wgc-client-token-456"
    private val invalidAgentToken = "invalid-token-789"
    private val emptyAgentToken = ""

    private val expectedServerConfig = """
        [Interface]
        PrivateKey = server-private-key
        Address = 10.0.0.1/24
        ListenPort = 51820
    """.trimIndent()

    private val expectedClientConfig = """
        [Interface]
        PrivateKey = client-private-key
        Address = 10.0.0.2/32

        [Peer]
        PublicKey = server-public-key
        Endpoint = vpn.example.com:51820
        AllowedIPs = 0.0.0.0/0
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        defaultWireGuardService = DefaultWireGuardManagementService(
            agentTokenGenerator = agentTokenGenerator,
            serverRepository = serverRepository,
            clientRepository = clientRepository,
            keyGenerator = keyGenerator,
            wireGuardCommandService = wireGuardCommandService,
            wireGuardTemplateService = wireGuardTemplateService,
            ipConflictDetectionService = ipConflictDetectionService,
            globalConfigurationService = globalConfigurationService,
            wireGuardProperties = wireGuardProperties
        )

        // Setup test server
        testServer = WireGuardServer(
            name = "Test Server",
            privateKey = "server-private-key",
            publicKey = "server-public-key",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            listenPort = 51820,
            agentToken = serverAgentToken
        )

        // Setup test client
        testClient = WireGuardClient(
            name = "Test Client",
            privateKey = "client-private-key",
            publicKey = "client-public-key",
            allowedIPs = mutableListOf(IPAddress("10.0.0.2/32")),
            server = testServer,
            agentToken = clientAgentToken
        )
    }

    // ========== Server Configuration Tests ==========

    @Test
    fun `should return server configuration when agent token matches server`() {
        // Given
        whenever(serverRepository.findByAgentToken(serverAgentToken)).thenReturn(testServer)
        whenever(wireGuardTemplateService.generateServerConfig(testServer)).thenReturn(expectedServerConfig)

        // When
        val result = defaultWireGuardService.getConfigurationByAgentToken(serverAgentToken)

        // Then
        assertEquals(expectedServerConfig, result)
        verify(serverRepository).findByAgentToken(serverAgentToken)
        verify(wireGuardTemplateService).generateServerConfig(testServer)
        // Verify that client repository is not called when server is found
        verify(clientRepository, never()).findByAgentToken(any())
        verify(wireGuardTemplateService, never()).generateClientConfig(any(), any(), any())
    }

    @Test
    fun `should handle server configuration generation when server token is found`() {
        // Given
        val serverToken = "wg-another-server-token"
        val anotherServer = WireGuardServer(
            name = "Another Server",
            privateKey = "another-server-private-key",
            publicKey = "another-server-public-key",
            addresses = mutableListOf(IPAddress("192.168.1.1/24")),
            listenPort = 51821,
            agentToken = serverToken
        )
        val expectedConfig = "[Interface]\nPrivateKey = another-server-private-key\n"

        whenever(serverRepository.findByAgentToken(serverToken)).thenReturn(anotherServer)
        whenever(wireGuardTemplateService.generateServerConfig(anotherServer)).thenReturn(expectedConfig)

        // When
        val result = defaultWireGuardService.getConfigurationByAgentToken(serverToken)

        // Then
        assertEquals(expectedConfig, result)
        verify(serverRepository).findByAgentToken(serverToken)
        verify(wireGuardTemplateService).generateServerConfig(anotherServer)
    }

    // ========== Client Configuration Tests ==========

    @Test
    fun `should return client configuration when agent token matches client and no server found`() {
        // Given
        whenever(serverRepository.findByAgentToken(clientAgentToken)).thenReturn(null)
        whenever(clientRepository.findByAgentToken(clientAgentToken)).thenReturn(testClient)
        whenever(wireGuardTemplateService.generateClientConfig(testClient, testServer, false))
            .thenReturn(expectedClientConfig)

        // When
        val result = defaultWireGuardService.getConfigurationByAgentToken(clientAgentToken)

        // Then
        assertEquals(expectedClientConfig, result)
        verify(serverRepository).findByAgentToken(clientAgentToken)
        verify(clientRepository).findByAgentToken(clientAgentToken)
        verify(wireGuardTemplateService).generateClientConfig(testClient, testServer, false)
    }

    @Test
    fun `should handle client configuration generation for different client`() {
        // Given
        val differentClientToken = "wgc-different-client-token"
        val differentServer = WireGuardServer(
            name = "Different Server",
            privateKey = "different-server-private-key",
            publicKey = "different-server-public-key",
            addresses = mutableListOf(IPAddress("172.16.0.1/24")),
            listenPort = 51822,
            agentToken = "wg-different-server-token"
        )
        val differentClient = WireGuardClient(
            name = "Different Client",
            privateKey = "different-client-private-key",
            publicKey = "different-client-public-key",
            allowedIPs = mutableListOf(IPAddress("172.16.0.10/32")),
            server = differentServer,
            agentToken = differentClientToken
        )
        val expectedDifferentConfig = "[Interface]\nPrivateKey = different-client-private-key\n"

        whenever(serverRepository.findByAgentToken(differentClientToken)).thenReturn(null)
        whenever(clientRepository.findByAgentToken(differentClientToken)).thenReturn(differentClient)
        whenever(wireGuardTemplateService.generateClientConfig(differentClient, differentServer, false))
            .thenReturn(expectedDifferentConfig)

        // When
        val result = defaultWireGuardService.getConfigurationByAgentToken(differentClientToken)

        // Then
        assertEquals(expectedDifferentConfig, result)
        verify(wireGuardTemplateService).generateClientConfig(differentClient, differentServer, false)
    }

    // ========== Error Cases Tests ==========

    @Test
    fun `should throw IllegalArgumentException when agent token not found for any server or client`() {
        // Given
        whenever(serverRepository.findByAgentToken(invalidAgentToken)).thenReturn(null)
        whenever(clientRepository.findByAgentToken(invalidAgentToken)).thenReturn(null)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            defaultWireGuardService.getConfigurationByAgentToken(invalidAgentToken)
        }

        assertEquals("No server or client found for the provided agent token", exception.message)
        verify(serverRepository).findByAgentToken(invalidAgentToken)
        verify(clientRepository).findByAgentToken(invalidAgentToken)
        verify(wireGuardTemplateService, never()).generateServerConfig(any())
        verify(wireGuardTemplateService, never()).generateClientConfig(any(), any(), any())
    }

    @Test
    fun `should throw IllegalArgumentException when empty agent token is provided`() {
        // Given
        whenever(serverRepository.findByAgentToken(emptyAgentToken)).thenReturn(null)
        whenever(clientRepository.findByAgentToken(emptyAgentToken)).thenReturn(null)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            defaultWireGuardService.getConfigurationByAgentToken(emptyAgentToken)
        }

        assertEquals("No server or client found for the provided agent token", exception.message)
        verify(serverRepository).findByAgentToken(emptyAgentToken)
        verify(clientRepository).findByAgentToken(emptyAgentToken)
    }

    @Test
    fun `should handle null token gracefully`() {
        // Given
        val nullToken = ""  // Since Kotlin doesn't allow null String in non-nullable parameter, test with empty string
        whenever(serverRepository.findByAgentToken(nullToken)).thenReturn(null)
        whenever(clientRepository.findByAgentToken(nullToken)).thenReturn(null)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            defaultWireGuardService.getConfigurationByAgentToken(nullToken)
        }

        assertTrue(exception.message!!.contains("No server or client found"))
    }

    // ========== Priority Tests ==========

    @Test
    fun `should prioritize server over client when both have same agent token`() {
        // Given - This is an edge case that shouldn't happen in production,
        // but we test to ensure server lookup has priority
        val sameToken = "shared-token-123"

        whenever(serverRepository.findByAgentToken(sameToken)).thenReturn(testServer)
        whenever(wireGuardTemplateService.generateServerConfig(testServer)).thenReturn(expectedServerConfig)

        // When
        val result = defaultWireGuardService.getConfigurationByAgentToken(sameToken)

        // Then
        assertEquals(expectedServerConfig, result)
        verify(serverRepository).findByAgentToken(sameToken)
        verify(wireGuardTemplateService).generateServerConfig(testServer)
        // Client repository should not be called since server was found first
        verify(clientRepository, never()).findByAgentToken(any())
        verify(wireGuardTemplateService, never()).generateClientConfig(any(), any(), any())
    }

    // ========== Integration-style Tests ==========

    @Test
    fun `should complete full workflow for server token lookup`() {
        // Given
        whenever(serverRepository.findByAgentToken(serverAgentToken)).thenReturn(testServer)
        whenever(wireGuardTemplateService.generateServerConfig(testServer)).thenReturn(expectedServerConfig)

        // When
        val result = defaultWireGuardService.getConfigurationByAgentToken(serverAgentToken)

        // Then
        assertEquals(expectedServerConfig, result)

        // Verify the exact sequence of operations
        val inOrder = inOrder(serverRepository, wireGuardTemplateService)
        inOrder.verify(serverRepository).findByAgentToken(serverAgentToken)
        inOrder.verify(wireGuardTemplateService).generateServerConfig(testServer)
    }

    @Test
    fun `should complete full workflow for client token lookup when no server found`() {
        // Given
        whenever(serverRepository.findByAgentToken(clientAgentToken)).thenReturn(null)
        whenever(clientRepository.findByAgentToken(clientAgentToken)).thenReturn(testClient)
        whenever(wireGuardTemplateService.generateClientConfig(testClient, testServer, false))
            .thenReturn(expectedClientConfig)

        // When
        val result = defaultWireGuardService.getConfigurationByAgentToken(clientAgentToken)

        // Then
        assertEquals(expectedClientConfig, result)

        // Verify the exact sequence of operations
        val inOrder = inOrder(serverRepository, clientRepository, wireGuardTemplateService)
        inOrder.verify(serverRepository).findByAgentToken(clientAgentToken)
        inOrder.verify(clientRepository).findByAgentToken(clientAgentToken)
        inOrder.verify(wireGuardTemplateService).generateClientConfig(testClient, testServer)
    }
}