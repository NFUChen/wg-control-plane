package com.app.service

import com.app.security.config.ControlPlaneMode
import com.app.security.config.ControlPlaneProperties
import com.app.service.validation.WireGuardValidationService
import com.app.service.validation.ControlPlaneValidationException
import com.app.service.validation.HybridModeValidationStrategy
import com.app.service.validation.PureRemoteValidationStrategy
import com.app.view.CreateServerRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ControlPlaneIntegrationTest {

    @Test
    fun `HYBRID mode validation service should allow local operations`() {
        val properties = ControlPlaneProperties(mode = ControlPlaneMode.HYBRID)
        val strategy = HybridModeValidationStrategy(properties)
        val validationService = WireGuardValidationService(strategy, properties)

        val localServerRequest = CreateServerRequest(
            name = "local-test-server",
            interfaceName = "wg0",
            networkAddress = "10.0.0.1/24",
            listenPort = 51820,
            dnsServers = listOf("8.8.8.8"),
            hostId = null // Local deployment
        )

        // Should not throw exception
        val validatedRequest = validationService.validateAndEnrich(localServerRequest)
        assertEquals("local-test-server", validatedRequest.name)
        assertEquals(null, validatedRequest.hostId)
    }

    @Test
    fun `PURE_REMOTE mode validation service should reject local operations`() {
        val properties = ControlPlaneProperties(mode = ControlPlaneMode.PURE_REMOTE)
        val strategy = PureRemoteValidationStrategy(properties)
        val validationService = WireGuardValidationService(strategy, properties)

        val localServerRequest = CreateServerRequest(
            name = "invalid-local-server",
            interfaceName = "wg0",
            networkAddress = "10.0.0.1/24",
            listenPort = 51820,
            dnsServers = listOf("8.8.8.8"),
            hostId = null // Local deployment - should be rejected
        )

        val exception = assertThrows<ControlPlaneValidationException> {
            validationService.validateAndEnrich(localServerRequest)
        }

        assertNotNull(exception.message)
        assert(exception.message!!.contains("Pure control plane mode"))
        assert(exception.suggestions.isNotEmpty())
        assertNotNull(exception.warningMessage)
    }

    @Test
    fun `validation service should provide correct current mode`() {
        val hybridProperties = ControlPlaneProperties(mode = ControlPlaneMode.HYBRID)
        val hybridStrategy = HybridModeValidationStrategy(hybridProperties)
        val hybridValidationService = WireGuardValidationService(hybridStrategy, hybridProperties)

        assertEquals(ControlPlaneMode.HYBRID, hybridValidationService.getCurrentMode())
        assert(hybridValidationService.areLocalOperationsAllowed())

        val pureProperties = ControlPlaneProperties(mode = ControlPlaneMode.PURE_REMOTE)
        val pureStrategy = PureRemoteValidationStrategy(pureProperties)
        val pureValidationService = WireGuardValidationService(pureStrategy, pureProperties)

        assertEquals(ControlPlaneMode.PURE_REMOTE, pureValidationService.getCurrentMode())
        assert(!pureValidationService.areLocalOperationsAllowed())
    }
}