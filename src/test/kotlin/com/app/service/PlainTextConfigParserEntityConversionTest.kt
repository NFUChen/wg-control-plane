package com.app.service

import com.app.model.WireGuardClient
import com.app.model.WireGuardServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for PlainTextConfigParser entity conversion functionality
 * Demonstrates converting parsed configurations to WireGuard entities
 */
@ExtendWith(MockitoExtension::class)
@ActiveProfiles("test")
class PlainTextConfigParserEntityConversionTest {

    private val parser = PlainTextConfigParser()

    // ========== Server Config to Entity Tests ==========

    @Test
    fun `should convert server config to WireGuardServer entity`() {
        // Given
        val serverConfigText = """
            [Interface]
            PrivateKey = server-private-key-12345
            Address = 10.0.0.1/24
            ListenPort = 51820
            PostUp = iptables -A FORWARD -i %i -j ACCEPT
            PostDown = iptables -D FORWARD -i %i -j ACCEPT
            MTU = 1420

            [Peer]
            PublicKey = client1-public-key
            AllowedIPs = 10.0.0.2/32

            [Peer]
            PublicKey = client2-public-key
            AllowedIPs = 10.0.0.3/32
            PresharedKey = shared-secret-123
        """.trimIndent()

        // When
        val parsedConfig = parser.parseConfigAs<ParsedConfig.ServerConfig>(serverConfigText)
        val serverEntity = parsedConfig.toWireGuardServer(
            name = "Production VPN Server",
            interfaceName = "wg0"
        )

        // Then
        assertEquals("Production VPN Server", serverEntity.name)
        assertEquals("wg0", serverEntity.interfaceName)
        assertEquals("server-private-key-12345", serverEntity.privateKey)
        assertEquals("placeholder-public-key-derived-from-private", serverEntity.publicKey)
        assertEquals(1, serverEntity.addresses.size)
        assertEquals("10.0.0.1/24", serverEntity.addresses[0].address)
        assertEquals(51820, serverEntity.listenPort)
        assertEquals("iptables -A FORWARD -i %i -j ACCEPT", serverEntity.postUp)
        assertEquals("iptables -D FORWARD -i %i -j ACCEPT", serverEntity.postDown)
        assertEquals(1420, serverEntity.mtu)
    }

    @Test
    fun `should convert server config peers to WireGuardClient entities`() {
        // Given
        val serverConfigText = """
            [Interface]
            PrivateKey = server-private-key
            Address = 10.0.0.1/24
            ListenPort = 51820

            [Peer]
            PublicKey = client1-public-key-abcde
            AllowedIPs = 10.0.0.2/32

            [Peer]
            PublicKey = client2-public-key-fghij
            AllowedIPs = 10.0.0.3/32, 10.0.0.4/32
            PresharedKey = shared-secret-456
            PersistentKeepalive = 30
        """.trimIndent()

        // When
        val parsedConfig = parser.parseConfigAs<ParsedConfig.ServerConfig>(serverConfigText)
        val serverEntity = parsedConfig.toWireGuardServer(name = "Test Server")
        val clientEntities = parsedConfig.toWireGuardClients(serverEntity, "VPN Client")

        // Then
        assertEquals(2, clientEntities.size)

        // First client
        val client1 = clientEntities[0]
        assertEquals("VPN Client 1", client1.name)
        assertEquals("client1-public-key-abcde", client1.publicKey)
        assertEquals(1, client1.allowedIPs.size)
        assertEquals("10.0.0.2/32", client1.allowedIPs[0].address)
        assertEquals(25, client1.persistentKeepalive) // Default value
        assertEquals(null, client1.presharedKey)

        // Second client
        val client2 = clientEntities[1]
        assertEquals("VPN Client 2", client2.name)
        assertEquals("client2-public-key-fghij", client2.publicKey)
        assertEquals(2, client2.allowedIPs.size)
        assertEquals("10.0.0.3/32", client2.allowedIPs[0].address)
        assertEquals("10.0.0.4/32", client2.allowedIPs[1].address)
        assertEquals(30, client2.persistentKeepalive)
        assertEquals("shared-secret-456", client2.presharedKey)

        // Verify server reference
        assertEquals(serverEntity, client1.server)
        assertEquals(serverEntity, client2.server)
    }

    @Test
    fun `should create complete server setup with clients in one call`() {
        // Given
        val serverConfigText = """
            [Interface]
            PrivateKey = server-private-key
            Address = 192.168.100.1/24
            ListenPort = 51820

            [Peer]
            PublicKey = mobile-client-key
            AllowedIPs = 192.168.100.10/32

            [Peer]
            PublicKey = laptop-client-key
            AllowedIPs = 192.168.100.20/32
        """.trimIndent()

        // When
        val parsedConfig = parser.parseConfigAs<ParsedConfig.ServerConfig>(serverConfigText)
        val completeSetup = parsedConfig.toCompleteWireGuardSetup(
            serverName = "Company VPN",
            clientNamePrefix = "Employee Device"
        )

        // Then
        assertEquals("Company VPN", completeSetup.name)
        assertEquals(2, completeSetup.clients.size)
        assertEquals("Employee Device 1", completeSetup.clients[0].name)
        assertEquals("Employee Device 2", completeSetup.clients[1].name)

        // Verify bidirectional relationship
        assertTrue(completeSetup.clients.all { it.server == completeSetup })
    }

    // ========== Client Config to Entity Tests ==========

    @Test
    fun `should convert client config to WireGuardClient entity with existing server`() {
        // Given
        val clientConfigText = """
            [Interface]
            PrivateKey = client-private-key-67890
            Address = 10.0.0.5/32
            PersistentKeepalive = 20

            [Peer]
            PublicKey = server-public-key-xyz
            Endpoint = vpn.company.com:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PresharedKey = client-shared-secret
        """.trimIndent()

        // Create an existing server
        val existingServer = WireGuardServer(
            name = "Existing Server",
            privateKey = "existing-server-private-key",
            publicKey = "server-public-key-xyz",
            agentToken = "existing-server-token"
        )

        // When
        val parsedConfig = parser.parseConfigAs<ParsedConfig.ClientConfig>(clientConfigText)
        val clientEntity = parsedConfig.toWireGuardClient(
            server = existingServer,
            name = "Mobile Phone",
            interfaceName = "wg0"
        )

        // Then
        assertEquals("Mobile Phone", clientEntity.name)
        assertEquals("wg0", clientEntity.interfaceName)
        assertEquals("client-private-key-67890", clientEntity.privateKey)
        assertEquals("server-public-key-xyz", clientEntity.publicKey)
        assertEquals("10.0.0.5/32", clientEntity.primaryPeerIP)
        assertEquals(2, clientEntity.allowedIPs.size)
        assertEquals("0.0.0.0/0", clientEntity.allowedIPs[0].address)
        assertEquals("::/0", clientEntity.allowedIPs[1].address)
        assertEquals(20, clientEntity.persistentKeepalive) // From interface section
        assertEquals("client-shared-secret", clientEntity.presharedKey)
        assertEquals(existingServer, clientEntity.server)
    }

    @Test
    fun `should create stub server from client config`() {
        // Given
        val clientConfigText = """
            [Interface]
            PrivateKey = client-private-key
            Address = 172.16.0.100/32

            [Peer]
            PublicKey = unknown-server-public-key
            Endpoint = remote-server.example.com:51821
            AllowedIPs = 172.16.0.0/24
        """.trimIndent()

        // When
        val parsedConfig = parser.parseConfigAs<ParsedConfig.ClientConfig>(clientConfigText)
        val stubServer = parsedConfig.createStubServerFromPeer(
            serverName = "Remote Server (From Client Config)"
        )

        // Then
        assertEquals("Remote Server (From Client Config)", stubServer.name)
        assertEquals("unknown-server-public-key", stubServer.publicKey)
        assertEquals(51821, stubServer.listenPort) // Extracted from endpoint
        assertEquals("placeholder-private-key", stubServer.privateKey)
        assertTrue(stubServer.addresses.isEmpty()) // Not available from client config
    }

    @Test
    fun `should extract port from endpoint correctly`() {
        // Given
        val clientConfigWithCustomPort = """
            [Interface]
            PrivateKey = client-key
            Address = 10.0.0.10/32

            [Peer]
            PublicKey = server-key
            Endpoint = vpn.example.org:12345
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        // When
        val parsedConfig = parser.parseConfigAs<ParsedConfig.ClientConfig>(clientConfigWithCustomPort)
        val stubServer = parsedConfig.createStubServerFromPeer()

        // Then
        assertEquals(12345, stubServer.listenPort)
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `should throw exception when required fields are missing in server config`() {
        // Given - Config without PrivateKey (multiple peers to make it a server config)
        val invalidServerConfig = """
            [Interface]
            Address = 10.0.0.1/24
            ListenPort = 51820

            [Peer]
            PublicKey = client-key-1
            AllowedIPs = 10.0.0.2/32

            [Peer]
            PublicKey = client-key-2
            AllowedIPs = 10.0.0.3/32
        """.trimIndent()

        // When & Then
        val parsedConfig = parser.parseConfigAs<ParsedConfig.ServerConfig>(invalidServerConfig)
        val exception = assertThrows<IllegalArgumentException> {
            parsedConfig.toWireGuardServer()
        }

        assertTrue(exception.message!!.contains("PrivateKey is required in Interface section"))
    }

    @Test
    fun `should throw exception when required fields are missing in client config`() {
        // Given - Config without PublicKey in peer section
        val invalidClientConfig = """
            [Interface]
            PrivateKey = client-private-key
            Address = 10.0.0.5/32

            [Peer]
            Endpoint = server.example.com:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        val existingServer = WireGuardServer(
            name = "Test Server",
            privateKey = "server-key",
            publicKey = "server-public-key",
            agentToken = "server-token"
        )

        // When & Then
        val parsedConfig = parser.parseConfigAs<ParsedConfig.ClientConfig>(invalidClientConfig)
        val exception = assertThrows<IllegalArgumentException> {
            parsedConfig.toWireGuardClient(existingServer)
        }

        assertTrue(exception.message!!.contains("PublicKey is required"))
    }

    // ========== Integration Tests ==========

    @Test
    fun `should handle full workflow from config text to entities`() {
        // Given
        val serverConfigText = """
            [Interface]
            PrivateKey = production-server-key
            Address = 10.8.0.1/24
            ListenPort = 51820

            [Peer]
            PublicKey = mobile-device-key
            AllowedIPs = 10.8.0.100/32

            [Peer]
            PublicKey = laptop-device-key
            AllowedIPs = 10.8.0.101/32
        """.trimIndent()

        // When - Complete workflow
        val serverEntity = parser.parseConfigAs<ParsedConfig.ServerConfig>(serverConfigText)
            .toCompleteWireGuardSetup(
                serverName = "Production VPN Server",
                clientNamePrefix = "User Device"
            )

        // Then - Verify complete setup
        assertNotNull(serverEntity)
        assertEquals("Production VPN Server", serverEntity.name)
        assertEquals("production-server-key", serverEntity.privateKey)
        assertEquals(2, serverEntity.clients.size)

        val mobileClient = serverEntity.clients.find { it.publicKey == "mobile-device-key" }
        val laptopClient = serverEntity.clients.find { it.publicKey == "laptop-device-key" }

        assertNotNull(mobileClient)
        assertNotNull(laptopClient)
        assertEquals("10.8.0.100/32", mobileClient.allowedIPs[0].address)
        assertEquals("10.8.0.101/32", laptopClient.allowedIPs[0].address)
    }
}