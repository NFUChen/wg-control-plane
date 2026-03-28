package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.model.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.*
import kotlin.test.assertTrue
import kotlin.test.assertContains

@SpringBootTest
@ActiveProfiles("test")
class TemplateServiceTest {

    @Autowired
    private lateinit var templateService: TemplateService

    @Test
    fun `test generateServerConfig with peers`() {
        // Prepare test data
        val serverPrivateKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val serverAddress = "10.100.0.1/16"
        val serverPort = 51820

        val peers = listOf(
            PeerTemplateData(
                publicKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
                allowedIPs = "10.100.0.2/32",
                endpoint = "peer1.example.com:51820"
            ),
            PeerTemplateData(
                publicKey = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=",
                allowedIPs = "10.100.0.3/32",
                endpoint = "peer2.example.com:51820"
            )
        )

        // Execute test
        val config = templateService.generateServerConfig(
            serverPrivateKey = serverPrivateKey,
            serverAddress = serverAddress,
            serverPort = serverPort,
            registeredPeers = peers
        )

        // Verify result
        assertContains(config, "[Interface]")
        assertContains(config, "PrivateKey = $serverPrivateKey")
        assertContains(config, "Address = $serverAddress")
        assertContains(config, "ListenPort = $serverPort")

        assertContains(config, "[Peer]")
        assertContains(config, "PublicKey = BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
        assertContains(config, "AllowedIPs = 10.100.0.2/32")
        assertContains(config, "Endpoint = peer1.example.com:51820")

        println("Generated Server Config:")
        println(config)
    }

    @Test
    fun `test generateClientConfig`() {
        // Prepare test data
        val clientPrivateKey = "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD="
        val clientAddress = "10.100.0.2/32"
        val serverPublicKey = "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE="
        val serverEndpoint = "vpn.example.com:51820"

        // Execute test
        val config = templateService.generateClientConfig(
            clientPrivateKey = clientPrivateKey,
            clientAddress = clientAddress,
            serverPublicKey = serverPublicKey,
            serverEndpoint = serverEndpoint
        )

        // Verify result
        assertContains(config, "[Interface]")
        assertContains(config, "PrivateKey = $clientPrivateKey")
        assertContains(config, "Address = $clientAddress")

        assertContains(config, "[Peer]")
        assertContains(config, "PublicKey = $serverPublicKey")
        assertContains(config, "AllowedIPs = 0.0.0.0/0")
        assertContains(config, "Endpoint = $serverEndpoint")

        println("Generated Client Config:")
        println(config)
    }

    @Test
    fun `test createPeerTemplateData from WgPeer`() {
        // Prepare test data
        val peers = listOf(
            WgPeer(
                id = UUID.randomUUID(),
                publicKey = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF=",
                allowedIPs = listOf(IPAddress("10.100.0.10/32")),
                endpoint = "test.example.com:51820",
                persistentKeepalive = 25
            )
        )

        // Execute test
        val peerTemplateData = templateService.createPeerTemplateData(peers)

        // Verify result
        assertTrue(peerTemplateData.size == 1)
        val peer = peerTemplateData.first()
        assertTrue(peer.publicKey == "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF=")
        assertTrue(peer.allowedIPs == "10.100.0.10/32")
        assertTrue(peer.endpoint == "test.example.com:51820")
        assertTrue(peer.persistentKeepalive == 25)
    }

    @Test
    fun `test validateConfigFormat`() {
        // Test valid configuration
        val validConfig = """
            [Interface]
            PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
            Address = 10.100.0.1/16

            [Peer]
            PublicKey = BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
            AllowedIPs = 10.100.0.2/32
            Endpoint = peer1.example.com:51820
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

        assertTrue(hash1 == hash2, "Same content should generate same hash")
        assertTrue(hash1 != hash3, "Different content should generate different hash")
    }
}