package com.app.service

import com.app.model.*
import com.app.view.AddClientRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.junit.jupiter.MockitoExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.ActiveProfiles
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for ClientDeploymentModeResolver
 */
@ExtendWith(MockitoExtension::class)
@ActiveProfiles("test")
class ClientDeploymentModeResolverTest {

    private lateinit var resolver: ClientDeploymentModeResolver
    private lateinit var testServer: WireGuardServer
    private lateinit var testHost: AnsibleHost

    @BeforeEach
    fun setUp() {
        resolver = ClientDeploymentModeResolver()

        // Create test host
        testHost = AnsibleHost(
            id = UUID.randomUUID(),
            hostname = "test-host",
            ipAddress = "192.168.1.100",
            sshUsername = "test-user",
            sshPrivateKey = PrivateKey(
                id = UUID.randomUUID(),
                name = "test-key",
                content = "test-private-key",
                enabled = true
            )
        )

        // Setup test server with ansible host
        testServer = WireGuardServer(
            name = "Test Server",
            interfaceName = "wg0",
            privateKey = "server-private-key",
            publicKey = "server-public-key",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            listenPort = 51820,
            ansibleHost = testHost
        )
    }

    @Test
    fun `resolve should return LOCAL mode when no hostId or useAgentMode specified`() {
        val request = AddClientRequest(
            clientName = "test-client",
            peerIPs = listOf(IPAddress("10.0.0.2/32"))
        )

        val result = resolver.resolve(request, testServer)

        assertEquals(ClientDeploymentMode.LOCAL, result)
    }

    @Test
    fun `resolve should return ANSIBLE mode when hostId is specified`() {
        val differentHostId = UUID.randomUUID()
        val request = AddClientRequest(
            clientName = "test-client",
            peerIPs = listOf(IPAddress("10.0.0.2/32")),
            hostId = differentHostId
        )

        val result = resolver.resolve(request, testServer)

        assertEquals(ClientDeploymentMode.ANSIBLE, result)
    }

    @Test
    fun `resolve should return AGENT mode when useAgentMode is true`() {
        val request = AddClientRequest(
            clientName = "test-client",
            peerIPs = listOf(IPAddress("10.0.0.2/32")),
            useAgentMode = true
        )

        val result = resolver.resolve(request, testServer)

        assertEquals(ClientDeploymentMode.AGENT, result)
    }

    @Test
    fun `resolve should throw exception when both hostId and useAgentMode are specified`() {
        val request = AddClientRequest(
            clientName = "test-client",
            peerIPs = listOf(IPAddress("10.0.0.2/32")),
            hostId = UUID.randomUUID(),
            useAgentMode = true
        )

        val exception = assertThrows<IllegalArgumentException> {
            resolver.resolve(request, testServer)
        }

        assertTrue(exception.message!!.contains("Cannot specify both hostId"))
    }

    @Test
    fun `resolve should throw exception when client and server have same hostId - remote host case`() {
        val serverHostId = testServer.hostId!! // testServer has testHost assigned
        val request = AddClientRequest(
            clientName = "test-client",
            peerIPs = listOf(IPAddress("10.0.0.2/32")),
            hostId = serverHostId
        )

        val exception = assertThrows<IllegalArgumentException> {
            resolver.resolve(request, testServer)
        }

        assertTrue(exception.message!!.contains("Client cannot be deployed to the same host"))
        assertTrue(exception.message!!.contains("network topology conflict"))
        assertTrue(exception.message!!.contains("remote host"))
    }

    @Test
    fun `resolve should throw exception when client and server have same hostId - local host case`() {
        // Create server without ansible host (local server)
        val localServer = WireGuardServer(
            name = "Local Server",
            interfaceName = "wg0",
            privateKey = "server-private-key",
            publicKey = "server-public-key",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            listenPort = 51820,
            ansibleHost = null // No ansible host (runs on control plane)
        )

        val request = AddClientRequest(
            clientName = "test-client",
            peerIPs = listOf(IPAddress("10.0.0.2/32")),
            hostId = null // Client also wants to be local (same as server)
        )

        val exception = assertThrows<IllegalArgumentException> {
            resolver.resolve(request, localServer)
        }

        assertTrue(exception.message!!.contains("Client cannot be deployed to the same host"))
        assertTrue(exception.message!!.contains("control plane host"))
        assertTrue(exception.message!!.contains("network topology conflict"))
    }

    @Test
    fun `resolve should allow client on different host than server`() {
        // Create server without ansible host (local server)
        val localServer = WireGuardServer(
            name = "Local Server",
            interfaceName = "wg0",
            privateKey = "server-private-key",
            publicKey = "server-public-key",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            listenPort = 51820,
            ansibleHost = null // No ansible host (runs on control plane)
        )

        val request = AddClientRequest(
            clientName = "test-client",
            peerIPs = listOf(IPAddress("10.0.0.2/32")),
            hostId = UUID.randomUUID() // Client on different remote host
        )

        // This should be allowed: server on control plane, client on remote host
        val result = resolver.resolve(request, localServer)

        assertEquals(ClientDeploymentMode.ANSIBLE, result)
    }

    @Test
    fun `validateCompatibility should throw exception for ANSIBLE mode with null server hostId`() {
        val localServer = WireGuardServer(
            name = "Local Server",
            interfaceName = "wg0",
            privateKey = "server-private-key",
            publicKey = "server-public-key",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            listenPort = 51820,
            ansibleHost = null
        )

        val exception = assertThrows<IllegalStateException> {
            resolver.validateCompatibility(
                ClientDeploymentMode.ANSIBLE,
                localServer,
                false
            )
        }

        assertTrue(exception.message!!.contains("Server 'Local Server' is not configured for Ansible deployment"))
    }

    @Test
    fun `validateCompatibility should throw exception for ANSIBLE mode with non-ansible service`() {
        val exception = assertThrows<UnsupportedOperationException> {
            resolver.validateCompatibility(
                ClientDeploymentMode.ANSIBLE,
                testServer,
                false
            )
        }

        assertTrue(exception.message!!.contains("ANSIBLE mode clients must be created through AnsibleWireGuardManagementService"))
    }

    @Test
    fun `validateCompatibility should pass for LOCAL mode with local server`() {
        val localServer = WireGuardServer(
            name = "Local Server",
            interfaceName = "wg0",
            privateKey = "server-private-key",
            publicKey = "server-public-key",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            listenPort = 51820,
            ansibleHost = null
        )

        // This should not throw any exception
        resolver.validateCompatibility(
            ClientDeploymentMode.LOCAL,
            localServer,
            false
        )
    }
}