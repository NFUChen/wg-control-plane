package com.app.service

import com.app.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.junit.jupiter.MockitoExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertTrue

/**
 * Unit tests for IPConflictDetectionService demonstrating various Spring testing patterns
 */
@ExtendWith(MockitoExtension::class)
@ActiveProfiles("test")
class IPConflictDetectionServiceTest {

    private lateinit var ipConflictService: IPConflictDetectionService

    private lateinit var testServer: WireGuardServer
    private lateinit var testClient1: WireGuardClient
    private lateinit var testClient2: WireGuardClient

    @BeforeEach
    fun setUp() {
        ipConflictService = IPConflictDetectionService()

        // Setup test data
        testServer = WireGuardServer(
            name = "Test Server",
            interfaceName = "wg0",
            privateKey = "server-private-key",
            publicKey = "server-public-key",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            listenPort = 51820,
            agentToken = "test-token"
        )

        testClient1 = WireGuardClient(
            name = "Client 1",
            interfaceName = "wg1",
            privateKey = "client1-private-key",
            publicKey = "client1-public-key",
            peerIPs = mutableListOf(IPAddress("10.0.0.2/32")),
            allowedIPs = mutableListOf(IPAddress("10.0.0.2/32")),
            server = testServer,
            agentToken = "client1-token"
        )

        testClient2 = WireGuardClient(
            name = "Client 2",
            interfaceName = "wg1",
            privateKey = "client2-private-key",
            publicKey = "client2-public-key",
            peerIPs = mutableListOf(IPAddress("10.0.0.3/32")),
            allowedIPs = mutableListOf(IPAddress("10.0.0.3/32")),
            server = testServer,
            agentToken = "client2-token"
        )

        testServer.clients.addAll(listOf(testClient1, testClient2))
    }

    // ========== Basic IP Conflict Detection Tests ==========

    @Test
    fun `should allow new client with unique IP addresses`() {
        // Given
        val newClientIPs = listOf(IPAddress("10.0.0.4/32"))

        // When & Then - should not throw exception
        ipConflictService.validateNewClientIPs(testServer, newClientIPs)
    }

    @Test
    fun `should detect IP conflict with existing client`() {
        // Given
        val conflictingIPs = listOf(IPAddress("10.0.0.2/32")) // Same as testClient1

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            ipConflictService.validateNewClientIPs(testServer, conflictingIPs)
        }

        assertTrue(exception.message!!.contains("already assigned") || exception.message!!.contains("conflict"))
    }

    @Test
    fun `should allow IP update for existing client to different IP`() {
        // Given
        val clientId = testClient1.id
        val newIPs = listOf(IPAddress("10.0.0.5/32"))

        // When & Then - should not throw exception
        ipConflictService.validateUpdatedClientIPs(testServer, clientId, newIPs)
    }

    @Test
    fun `should detect server IP conflict`() {
        // Given
        val conflictingIPs = listOf(IPAddress("10.0.0.1/32")) // Same as server

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            ipConflictService.validateNewClientIPs(testServer, conflictingIPs)
        }

        assertTrue(exception.message!!.contains("server") || exception.message!!.contains("conflict"))
    }

    // ========== Parameterized Tests ==========

    @ParameterizedTest
    @ValueSource(strings = ["10.0.0.10/32", "10.0.0.100/32", "10.0.0.250/32"])
    fun `should allow various unique IP addresses in server network range`(ipAddress: String) {
        // Given
        val newClientIPs = listOf(IPAddress(ipAddress))

        // When & Then - should not throw exception
        ipConflictService.validateNewClientIPs(testServer, newClientIPs)
    }

    @ParameterizedTest
    @CsvSource(
        "10.0.0.2/32",
        "10.0.0.3/32"
    )
    fun `should detect conflicts with existing client addresses`(conflictIP: String) {
        // Given
        val conflictingIPs = listOf(IPAddress(conflictIP))

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            ipConflictService.validateNewClientIPs(testServer, conflictingIPs)
        }

        assertTrue(exception.message!!.contains("already") || exception.message!!.contains("conflict"))
    }

    // ========== Multiple IP Address Tests ==========

    @Test
    fun `should handle multiple IP addresses for single client`() {
        // Given
        val multipleIPs = listOf(
            IPAddress("10.0.0.10/32"),
            IPAddress("10.0.0.11/32"),
            IPAddress("10.0.0.12/32")
        )

        // When & Then - should not throw exception
        ipConflictService.validateNewClientIPs(testServer, multipleIPs)
    }

    @Test
    fun `should detect conflict in multiple IP addresses`() {
        // Given
        val mixedIPs = listOf(
            IPAddress("10.0.0.10/32"), // Valid
            IPAddress("10.0.0.2/32"),  // Conflicts with testClient1
            IPAddress("10.0.0.11/32")  // Valid
        )

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            ipConflictService.validateNewClientIPs(testServer, mixedIPs)
        }

        assertTrue(exception.message!!.contains("already") || exception.message!!.contains("conflict"))
    }

    // ========== Edge Cases ==========

    @Test
    fun `should handle empty client list`() {
        // Given
        val emptyServer = WireGuardServer(
            name = "Empty Server",
            interfaceName = "wg0",
            privateKey = "key",
            publicKey = "pub-key",
            addresses = mutableListOf(IPAddress("192.168.1.1/24")),
            listenPort = 51820,
            agentToken = "empty-token"
        )
        // Server starts with empty clients list by default

        val newClientIPs = listOf(IPAddress("192.168.1.10/32"))

        // When & Then - should not throw exception
        ipConflictService.validateNewClientIPs(emptyServer, newClientIPs)
    }

    @Test
    fun `should allow same IP when updating same client`() {
        // Given
        val clientId = testClient1.id
        val sameIPs = listOf(IPAddress("10.0.0.2/32")) // Same IP as testClient1 already has

        // When & Then - should not throw exception since it's the same client
        ipConflictService.validateUpdatedClientIPs(testServer, clientId, sameIPs)
    }
}