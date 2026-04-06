package com.app.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for PlainTextConfigParser demonstrating different return types
 */
@ExtendWith(MockitoExtension::class)
@ActiveProfiles("test")
class PlainTextConfigParserTest {

    private val parser = PlainTextConfigParser()

    // ========== Server Configuration Tests ==========

    @Test
    fun `should parse server configuration with multiple peers`() {
        // Given
        val serverConfigText = """
            [Interface]
            PrivateKey = server-private-key
            Address = 10.0.0.1/24
            ListenPort = 51820

            [Peer]
            PublicKey = client1-public-key
            AllowedIPs = 10.0.0.2/32

            [Peer]
            PublicKey = client2-public-key
            AllowedIPs = 10.0.0.3/32
        """.trimIndent()

        // When
        val result = parser.parseConfig(serverConfigText)

        // Then
        assertTrue(result is ParsedConfig.ServerConfig, "Should be parsed as ServerConfig")
        val serverConfig = result as ParsedConfig.ServerConfig

        assertEquals("server-private-key", serverConfig.interfaceSection["PrivateKey"])
        assertEquals("10.0.0.1/24", serverConfig.interfaceSection["Address"])
        assertEquals("51820", serverConfig.interfaceSection["ListenPort"])

        assertEquals(2, serverConfig.peers.size)
        assertEquals("client1-public-key", serverConfig.peers[0]["PublicKey"])
        assertEquals("10.0.0.2/32", serverConfig.peers[0]["AllowedIPs"])
        assertEquals("client2-public-key", serverConfig.peers[1]["PublicKey"])
        assertEquals("10.0.0.3/32", serverConfig.peers[1]["AllowedIPs"])
    }

    @Test
    fun `should parse server configuration using explicit type`() {
        // Given
        val serverConfigText = """
            [Interface]
            PrivateKey = server-key
            Address = 192.168.1.1/24

            [Peer]
            PublicKey = peer1-key
            AllowedIPs = 192.168.1.10/32

            [Peer]
            PublicKey = peer2-key
            AllowedIPs = 192.168.1.11/32
        """.trimIndent()

        // When
        val result = parser.parseConfigAs<ParsedConfig.ServerConfig>(serverConfigText)

        // Then
        assertEquals(2, result.peers.size)
        assertEquals("server-key", result.interfaceSection["PrivateKey"])
    }

    // ========== Client Configuration Tests ==========

    @Test
    fun `should parse client configuration with single peer`() {
        // Given
        val clientConfigText = """
            [Interface]
            PrivateKey = client-private-key
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = server-public-key
            Endpoint = vpn.example.com:51820
            AllowedIPs = 0.0.0.0/0, ::/0
        """.trimIndent()

        // When
        val result = parser.parseConfig(clientConfigText)

        // Then
        assertTrue(result is ParsedConfig.ClientConfig, "Should be parsed as ClientConfig")
        val clientConfig = result as ParsedConfig.ClientConfig

        assertEquals("client-private-key", clientConfig.interfaceSection["PrivateKey"])
        assertEquals("10.0.0.2/32", clientConfig.interfaceSection["Address"])

        assertEquals("server-public-key", clientConfig.peer["PublicKey"])
        assertEquals("vpn.example.com:51820", clientConfig.peer["Endpoint"])
        assertEquals("0.0.0.0/0, ::/0", clientConfig.peer["AllowedIPs"])
    }

    @Test
    fun `should parse client configuration using explicit type`() {
        // Given
        val clientConfigText = """
            [Interface]
            PrivateKey = client-key
            Address = 172.16.0.10/32

            [Peer]
            PublicKey = server-key
            Endpoint = server.example.com:51820
            AllowedIPs = 172.16.0.0/24
        """.trimIndent()

        // When
        val result = parser.parseConfigAs<ParsedConfig.ClientConfig>(clientConfigText)

        // Then
        assertEquals("client-key", result.interfaceSection["PrivateKey"])
        assertEquals("server-key", result.peer["PublicKey"])
        assertEquals("server.example.com:51820", result.peer["Endpoint"])
    }

    // ========== Type Mismatch Tests ==========

    @Test
    fun `should throw exception when expecting wrong type`() {
        // Given - Server config (2 peers)
        val serverConfigText = """
            [Interface]
            PrivateKey = server-key
            Address = 10.0.0.1/24

            [Peer]
            PublicKey = peer1
            AllowedIPs = 10.0.0.2/32

            [Peer]
            PublicKey = peer2
            AllowedIPs = 10.0.0.3/32
        """.trimIndent()

        // When & Then - Try to parse as ClientConfig
        val exception = assertThrows<IllegalArgumentException> {
            parser.parseConfigAs<ParsedConfig.ClientConfig>(serverConfigText)
        }

        assertTrue(exception.message!!.contains("Expected ClientConfig but got ServerConfig"))
    }

    // ========== Extension Functions Tests ==========

    @Test
    fun `should use extension functions for server config`() {
        // Given
        val serverConfigText = """
            [Interface]
            PrivateKey = server-private-key
            Address = 10.0.0.1/24

            [Peer]
            PublicKey = AAAAA
            AllowedIPs = 10.0.0.2/32

            [Peer]
            PublicKey = BBBBB
            AllowedIPs = 10.0.0.3/32
        """.trimIndent()

        val config = parser.parseConfigAs<ParsedConfig.ServerConfig>(serverConfigText)

        // When & Then
        assertEquals(2, config.getClientCount())
        assertEquals("10.0.0.1/24", config.getInterfaceAddress())
        assertEquals("server-private-key", config.getPrivateKey())

        val client = config.getClientByPublicKey("AAAAA")
        assertNotNull(client)
        assertEquals("10.0.0.2/32", client["AllowedIPs"])

        val nonExistentClient = config.getClientByPublicKey("CCCCC")
        assertNull(nonExistentClient)
    }

    @Test
    fun `should use extension functions for client config`() {
        // Given
        val clientConfigText = """
            [Interface]
            PrivateKey = client-private-key
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = server-public-key
            Endpoint = vpn.example.com:51820
            AllowedIPs = 0.0.0.0/0, ::/0
        """.trimIndent()

        val config = parser.parseConfigAs<ParsedConfig.ClientConfig>(clientConfigText)

        // When & Then
        assertEquals("10.0.0.2/32", config.getInterfaceAddress())
        assertEquals("client-private-key", config.getPrivateKey())
        assertEquals("vpn.example.com:51820", config.getServerEndpoint())

        val allowedIPs = config.getAllowedIPs()
        assertEquals(2, allowedIPs.size)
        assertTrue(allowedIPs.contains("0.0.0.0/0"))
        assertTrue(allowedIPs.contains("::/0"))
    }

    // ========== Generic Section Parsing Tests ==========

    @Test
    fun `should parse into generic sections without type inference`() {
        // Given
        val configText = """
            [Interface]
            PrivateKey = test-key
            Address = 10.0.0.1/24

            [Peer]
            PublicKey = peer-key
            AllowedIPs = 10.0.0.2/32
        """.trimIndent()

        // When
        val sections = parser.parseIntoSections(configText)

        // Then
        assertEquals(2, sections.size)
        assertTrue(sections.containsKey("Interface"))
        assertTrue(sections.containsKey("Peer0"))

        assertEquals("test-key", sections["Interface"]!!["PrivateKey"])
        assertEquals("peer-key", sections["Peer0"]!!["PublicKey"])
    }

    // ========== Error Cases Tests ==========

    @Test
    fun `should throw exception for missing Interface section`() {
        // Given
        val invalidConfig = """
            [Peer]
            PublicKey = peer-key
            AllowedIPs = 10.0.0.2/32
        """.trimIndent()

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            parser.parseConfig(invalidConfig)
        }

        assertEquals("Interface section is required", exception.message)
    }

    @Test
    fun `should throw exception for missing Peer section`() {
        // Given
        val invalidConfig = """
            [Interface]
            PrivateKey = test-key
            Address = 10.0.0.1/24
        """.trimIndent()

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            parser.parseConfig(invalidConfig)
        }

        assertEquals("At least one Peer section is required", exception.message)
    }

    // ========== When Expression Usage Tests ==========

    @Test
    fun `should handle different config types with when expression`() {
        // Given
        val serverConfig = """
            [Interface]
            PrivateKey = server-key
            Address = 10.0.0.1/24

            [Peer]
            PublicKey = peer1
            AllowedIPs = 10.0.0.2/32

            [Peer]
            PublicKey = peer2
            AllowedIPs = 10.0.0.3/32
        """.trimIndent()

        val clientConfig = """
            [Interface]
            PrivateKey = client-key
            Address = 10.0.0.5/32

            [Peer]
            PublicKey = server-key
            Endpoint = server.com:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        // When & Then
        val serverResult = parser.parseConfig(serverConfig)
        val clientResult = parser.parseConfig(clientConfig)

        val serverDescription = when (serverResult) {
            is ParsedConfig.ServerConfig -> "Server with ${serverResult.getClientCount()} clients"
            is ParsedConfig.ClientConfig -> "Client configuration"
        }

        val clientDescription = when (clientResult) {
            is ParsedConfig.ServerConfig -> "Server with ${clientResult.getClientCount()} clients"
            is ParsedConfig.ClientConfig -> "Client connecting to ${clientResult.getServerEndpoint()}"
        }

        assertEquals("Server with 2 clients", serverDescription)
        assertEquals("Client connecting to server.com:51820", clientDescription)
    }
}