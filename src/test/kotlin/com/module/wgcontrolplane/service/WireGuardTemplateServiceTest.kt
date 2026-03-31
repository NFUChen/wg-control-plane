package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.model.IPAddress
import com.module.wgcontrolplane.model.WireGuardClient
import com.module.wgcontrolplane.model.WireGuardServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
class WireGuardTemplateServiceTest {

    @Autowired
    private lateinit var templateService: WireGuardTemplateService

    @Test
    fun `test generateServerConfig with clients`() {
        // Prepare test data
        val server = WireGuardServer(
            name = "Test Server",
            privateKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            publicKey = "ServerPublicKeyAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            listenPort = 51820,
            endpoint = "vpn.example.com:51820"
        )

        val client1 = WireGuardClient(
            name = "Test Client 1",
            privateKey = "Client1PrivateKeyAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            publicKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            allowedIPs = mutableListOf(IPAddress("10.0.0.2/32")),
            server = server
        )

        val client2 = WireGuardClient(
            name = "Test Client 2",
            privateKey = "Client2PrivateKeyAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            publicKey = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=",
            allowedIPs = mutableListOf(IPAddress("10.0.0.3/32")),
            server = server
        )

        server.addClient(client1)
        server.addClient(client2)

        // Execute test
        val config = templateService.generateServerConfig(server)

        // Verify result
        assertContains(config, "[Interface]")
        assertContains(config, "PrivateKey = ${server.privateKey}")
        assertContains(config, "Address = 10.0.0.1/24")
        assertContains(config, "ListenPort = 51820")

        assertContains(config, "[Peer]")
        assertContains(config, "PublicKey = BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
        assertContains(config, "AllowedIPs = 10.0.0.2/32")
        assertContains(config, "PublicKey = CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
        assertContains(config, "AllowedIPs = 10.0.0.3/32")

        println("Generated Server Config:")
        println(config)
    }

    @Test
    fun `test generateClientConfig`() {
        // Prepare test data
        val server = WireGuardServer(
            name = "Test Server",
            privateKey = "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=",
            publicKey = "ServerPublicKeyEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            listenPort = 51820,
            endpoint = "vpn.example.com:51820"
        )

        val client = WireGuardClient(
            name = "Test Client",
            privateKey = "ClientPrivateKeyDDDDDDDDDDDDDDDDDDDDDDDDDDDD=",
            publicKey = "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD=",
            allowedIPs = mutableListOf(IPAddress("10.0.0.2/32")),
            server = server
        )

        val clientPrivateKey = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF="

        // Execute test
        val config = templateService.generateClientConfigWithPrivateKey(
            clientPrivateKey = clientPrivateKey,
            client = client,
            server = server,
            allowAllTraffic = true
        )

        // Verify result
        assertContains(config, "[Interface]")
        assertContains(config, "PrivateKey = $clientPrivateKey")
        assertContains(config, "Address = 10.0.0.2/32")

        assertContains(config, "[Peer]")
        assertContains(config, "PublicKey = ${server.publicKey}")
        assertContains(config, "AllowedIPs = 0.0.0.0/0, ::/0")
        assertContains(config, "Endpoint = vpn.example.com:51820")

        println("Generated Client Config:")
        println(config)
    }

    @Test
    fun `test generateClientConfig with limited traffic`() {
        // Prepare test data
        val server = WireGuardServer(
            name = "Test Server",
            privateKey = "GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG=",
            publicKey = "ServerPublicKeyGGGGGGGGGGGGGGGGGGGGGGGGGGGGG=",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            endpoint = "vpn.example.com:51820"
        )

        val client = WireGuardClient(
            name = "Limited Client",
            privateKey = "ClientPrivateKeyHHHHHHHHHHHHHHHHHHHHHHHHHHHH=",
            publicKey = "HHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH=",
            allowedIPs = mutableListOf(IPAddress("10.0.0.5/32")),
            server = server
        )

        val clientPrivateKey = "IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII="

        // Execute test - only allow server network traffic
        val config = templateService.generateClientConfigWithPrivateKey(
            clientPrivateKey = clientPrivateKey,
            client = client,
            server = server,
            allowAllTraffic = false // Only server network
        )

        // Verify result
        assertContains(config, "AllowedIPs = 10.0.0.1/24")

        println("Generated Limited Client Config:")
        println(config)
    }

    @Test
    fun `test validateConfigFormat`() {
        // Test valid configuration
        val validConfig = """
            [Interface]
            PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
            Address = 10.0.0.1/24

            [Peer]
            PublicKey = BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
            AllowedIPs = 10.0.0.2/32
        """.trimIndent()

        val errors = templateService.validateConfigFormat(validConfig)
        assertTrue(errors.isEmpty(), "Valid config should have no errors")

        // Test invalid configuration
        val invalidConfig = """
            [Peer]
            PublicKey = SHORT
        """.trimIndent()

        val errorsInvalid = templateService.validateConfigFormat(invalidConfig)
        assertTrue(errorsInvalid.isNotEmpty(), "Invalid config should have errors")
        assertTrue(errorsInvalid.any { it.contains("Interface") })
    }

    @Test
    fun `test generateConfigHash`() {
        val config1 = "test config content"
        val config2 = "test config content"
        val config3 = "different config"

        val hash1 = templateService.generateConfigHash(config1)
        val hash2 = templateService.generateConfigHash(config2)
        val hash3 = templateService.generateConfigHash(config3)

        assertEquals(hash1, hash2, "Same content should generate same hash")
        assertTrue(hash1 != hash3, "Different content should generate different hash")
    }

    @Test
    fun `test IPAddress validation with plain IP addresses`() {
        // Test plain IPv4 address - should work and default to /32
        val ipv4PlainAddress = IPAddress("8.8.8.8")
        assertEquals("8.8.8.8", ipv4PlainAddress.IP)
        assertEquals(32, ipv4PlainAddress.prefixLength)

        // Test DNS server with plain IP - this should now work
        val dnsServer = IPAddress("1.1.1.1")
        assertEquals("1.1.1.1", dnsServer.IP)
        assertEquals(32, dnsServer.prefixLength)

        // Test CIDR format still works
        val cidrAddress = IPAddress("10.0.0.1/24")
        assertEquals("10.0.0.1", cidrAddress.IP)
        assertEquals(24, cidrAddress.prefixLength)

        // Test plain IPv6 address - should work and default to /128
        val ipv6PlainAddress = IPAddress("2001:db8::1")
        assertEquals("2001:db8::1", ipv6PlainAddress.IP)
        assertEquals(128, ipv6PlainAddress.prefixLength)

        println("✓ Plain IP address validation test passed")
    }
}