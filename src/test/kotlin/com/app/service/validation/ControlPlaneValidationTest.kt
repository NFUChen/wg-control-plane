package com.app.service.validation

import com.app.security.config.ControlPlaneMode
import com.app.security.config.ControlPlaneProperties
import com.app.view.AddClientRequest
import com.app.view.CreateServerRequest
import com.app.model.IPAddress
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlPlaneValidationTest {

    @Test
    fun `HYBRID mode should allow all operations`() {
        val properties = ControlPlaneProperties(mode = ControlPlaneMode.HYBRID)
        val strategy = HybridModeValidationStrategy()
        val validationService = WireGuardValidationService(strategy, properties)

        // Test local server creation (hostId = null)
        val localServerRequest = CreateServerRequest(
            name = "test-server",
            interfaceName = "wg0",
            networkAddress = "10.0.0.1/24",
            listenPort = 51820,
            dnsServers = listOf("8.8.8.8"),
            hostId = null
        )

        val validatedRequest = validationService.validateAndEnrich(localServerRequest)
        assertEquals("test-server", validatedRequest.name)

        // Test local client creation
        val localClientRequest = AddClientRequest(
            clientName = "test-client",
            interfaceName = "wg0",
            peerIPs = listOf(IPAddress("10.0.0.2")),
            allowedIPs = listOf(IPAddress("10.0.0.2/32")),
            hostId = null,
            useAgentMode = false
        )

        val validatedClientRequest = validationService.validateAndEnrich(localClientRequest)
        assertEquals("test-client", validatedClientRequest.clientName)

        assertTrue(validationService.areLocalOperationsAllowed())
    }

    @Test
    fun `PURE_REMOTE mode should reject local operations`() {
        val properties = ControlPlaneProperties(mode = ControlPlaneMode.PURE_REMOTE)
        val strategy = PureRemoteValidationStrategy(properties)
        val validationService = WireGuardValidationService(strategy, properties)

        // Test local server creation should fail
        val localServerRequest = CreateServerRequest(
            name = "test-server",
            interfaceName = "wg0",
            networkAddress = "10.0.0.1/24",
            listenPort = 51820,
            dnsServers = listOf("8.8.8.8"),
            hostId = null
        )

        val exception = assertThrows<ControlPlaneValidationException> {
            validationService.validateAndEnrich(localServerRequest)
        }

        assertTrue(exception.message?.contains("Pure control plane mode requires") == true)
        assertTrue(exception.suggestions.isNotEmpty())

        assertFalse(validationService.areLocalOperationsAllowed())
    }

    @Test
    fun `PURE_REMOTE mode should accept remote server operations`() {
        val properties = ControlPlaneProperties(mode = ControlPlaneMode.PURE_REMOTE)
        val strategy = PureRemoteValidationStrategy(properties)
        val validationService = WireGuardValidationService(strategy, properties)

        // Test remote server creation should pass
        val hostId = UUID.randomUUID()
        val remoteServerRequest = CreateServerRequest(
            name = "remote-server",
            interfaceName = "wg0",
            networkAddress = "10.0.0.1/24",
            listenPort = 51820,
            dnsServers = listOf("8.8.8.8"),
            hostId = hostId
        )

        val validatedRequest = validationService.validateAndEnrich(remoteServerRequest)
        assertEquals("remote-server", validatedRequest.name)
        assertEquals(hostId, validatedRequest.hostId)
    }

    @Test
    fun `PURE_REMOTE mode should accept agent mode clients`() {
        val properties = ControlPlaneProperties(mode = ControlPlaneMode.PURE_REMOTE)
        val strategy = PureRemoteValidationStrategy(properties)
        val validationService = WireGuardValidationService(strategy, properties)

        // Test agent mode client creation should pass
        val agentClientRequest = AddClientRequest(
            clientName = "agent-client",
            interfaceName = "wg0",
            peerIPs = listOf(IPAddress("10.0.0.2")),
            allowedIPs = listOf(IPAddress("10.0.0.2/32")),
            hostId = null,
            useAgentMode = true
        )

        val validatedRequest = validationService.validateAndEnrich(agentClientRequest)
        assertEquals("agent-client", validatedRequest.clientName)
        assertEquals(true, validatedRequest.useAgentMode)
    }
}