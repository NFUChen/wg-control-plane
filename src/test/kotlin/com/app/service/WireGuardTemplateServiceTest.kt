package com.app.service

import com.app.model.IPAddress
import com.app.model.WireGuardClient
import com.app.model.WireGuardServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
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
            agentToken = "test-token"
        )

        val client1 = WireGuardClient(
            name = "Test Client 1",
            privateKey = "Client1PrivateKeyAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            publicKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            peerIPs = mutableListOf(IPAddress("10.0.0.2/32")),
            allowedIPs = mutableListOf(IPAddress("10.0.0.2/32")),
            server = server,
            agentToken = "test-client1-token"
        )

        val client2 = WireGuardClient(
            name = "Test Client 2",
            privateKey = "Client2PrivateKeyAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            publicKey = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=",
            peerIPs = mutableListOf(IPAddress("10.0.0.3/32")),
            allowedIPs = mutableListOf(IPAddress("10.0.0.3/32")),
            server = server,
            agentToken = "test-client2-token"
        )

        server.addClient(client1)
        server.addClient(client2)

        // Execute test
        val config = templateService.generateServerConfig(server)

        // Verify result using structured WireGuard config validation
        val configSections = parseWireGuardConfig(config)

        // Verify Interface section
        val interfaceSection = configSections["Interface"]
        assertNotNull(interfaceSection, "Interface section should be present")
        assertEquals(server.privateKey, interfaceSection["PrivateKey"])
        assertEquals("10.0.0.1/24", interfaceSection["Address"])
        assertEquals("51820", interfaceSection["ListenPort"])

        // Verify both Peer sections exist
        val peerSections = configSections.filter { (key, _) -> key.startsWith("Peer") }
        assertEquals(2, peerSections.size, "Should have exactly 2 Peer sections")

        // Verify client1 peer configuration
        val client1Peer = peerSections.values.find { peerSection ->
            peerSection["PublicKey"] == "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
        }
        assertNotNull(client1Peer, "Client1 peer configuration should be present")
        assertEquals("10.0.0.2/32, 10.0.0.2/32", client1Peer["AllowedIPs"])

        // Verify client2 peer configuration
        val client2Peer = peerSections.values.find { peerSection ->
            peerSection["PublicKey"] == "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC="
        }
        assertNotNull(client2Peer, "Client2 peer configuration should be present")
        assertEquals("10.0.0.3/32, 10.0.0.3/32", client2Peer["AllowedIPs"])

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
            agentToken = "test-server-token"
        )

        val client = WireGuardClient(
            name = "Test Client",
            privateKey = "ClientPrivateKeyDDDDDDDDDDDDDDDDDDDDDDDDDDDD=",
            publicKey = "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD=",
            peerIPs = mutableListOf(IPAddress("10.0.0.2/32")),
            allowedIPs = mutableListOf(IPAddress("10.0.0.2/32")),
            server = server,
            agentToken = "test-client-token"
        )

        val clientPrivateKey = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF="

        // Execute test
        val config = templateService.generateClientConfigWithPrivateKey(
            clientPrivateKey = clientPrivateKey,
            client = client,
            server = server
        )

        // Verify result using structured WireGuard config validation
        val configSections = parseWireGuardConfig(config)

        // Verify Interface section
        val interfaceSection = configSections["Interface"]
        assertNotNull(interfaceSection, "Interface section should be present")
        assertEquals(clientPrivateKey, interfaceSection["PrivateKey"])
        assertEquals("10.0.0.2/32", interfaceSection["Address"])

        // Verify single Peer section for client config
        val peerSections = configSections.filter { (key, _) -> key.startsWith("Peer") }
        assertEquals(1, peerSections.size, "Client config should have exactly 1 Peer section")

        val peerSection = peerSections.values.first()
        assertEquals(server.publicKey, peerSection["PublicKey"])
        // AllowedIPs should include server addresses and client allowedIPs
        assertTrue(peerSection.containsKey("AllowedIPs"), "AllowedIPs should be present in peer section")
        assertTrue(peerSection.containsKey("Endpoint"), "Endpoint should be present in peer section")

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
            agentToken = "test-server2-token"
        )

        val client = WireGuardClient(
            name = "Limited Client",
            privateKey = "ClientPrivateKeyHHHHHHHHHHHHHHHHHHHHHHHHHHHH=",
            publicKey = "HHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH=",
            peerIPs = mutableListOf(IPAddress("10.0.0.5/32")),
            allowedIPs = mutableListOf(IPAddress("10.0.0.5/32")),
            server = server,
            agentToken = "test-limited-client-token"
        )

        val clientPrivateKey = "IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII="

        // Execute test - only allow server network traffic
        val config = templateService.generateClientConfigWithPrivateKey(
            clientPrivateKey = clientPrivateKey,
            client = client,
            server = server
        )

        // Verify result using structured validation
        val configSections = parseWireGuardConfig(config)

        // Verify Interface section
        val interfaceSection = configSections["Interface"]
        assertNotNull(interfaceSection, "Interface section should be present")
        assertEquals(clientPrivateKey, interfaceSection["PrivateKey"])
        assertEquals("10.0.0.5/32", interfaceSection["Address"])

        // Verify Peer section
        val peerSections = configSections.filter { (key, _) -> key.startsWith("Peer") }
        assertEquals(1, peerSections.size, "Client config should have exactly 1 Peer section")

        val peerSection = peerSections.values.first()
        // AllowedIPs should include server addresses and client allowedIPs
        assertTrue(peerSection.containsKey("AllowedIPs"), "AllowedIPs should be present in peer section")

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

    /**
     * Parse WireGuard configuration into sections for structured validation
     * Returns a map where keys are section names and values are maps of property-value pairs
     */
    private fun parseWireGuardConfig(config: String): Map<String, Map<String, String>> {
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        var currentSection: String? = null
        var peerCount = 0

        config.lines().forEach { line ->
            val trimmedLine = line.trim()

            when {
                trimmedLine.isEmpty() || trimmedLine.startsWith("#") -> {
                    // Skip empty lines and comments
                }
                trimmedLine.startsWith("[") && trimmedLine.endsWith("]") -> {
                    // Section header
                    val sectionName = trimmedLine.removeSurrounding("[", "]")
                    currentSection = if (sectionName == "Peer") {
                        // Handle multiple Peer sections
                        "Peer$peerCount"
                    } else {
                        sectionName
                    }
                    if (sectionName == "Peer") peerCount++
                    sections[currentSection!!] = mutableMapOf()
                }
                trimmedLine.contains("=") && currentSection != null -> {
                    // Property line
                    val parts = trimmedLine.split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        sections[currentSection]!![key] = value
                    }
                }
            }
        }

        return sections
    }
}