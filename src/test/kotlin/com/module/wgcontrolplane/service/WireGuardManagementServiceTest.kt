package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.dto.*
import com.module.wgcontrolplane.model.*
import com.module.wgcontrolplane.repository.WireGuardServerRepository
import com.module.wgcontrolplane.repository.WireGuardClientRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WireGuardManagementServiceTest {

    @Autowired
    private lateinit var managementService: WireGuardManagementService

    @Autowired
    private lateinit var serverRepository: WireGuardServerRepository

    @Autowired
    private lateinit var clientRepository: WireGuardClientRepository

    @BeforeEach
    fun setUp() {
        // Clean up before each test
        clientRepository.deleteAll()
        serverRepository.deleteAll()
    }

    @Test
    fun `test createServer`() {
        // Execute test
        val server = managementService.createServer(
            CreateServerRequest(
                name = "Test VPN Server",
                networkAddress = "10.0.0.1/24",
                listenPort = 51820,
                endpoint = "test-vpn.example.com:51820"
            )
        )

        // Verify result
        assertNotNull(server.id)
        assertEquals("Test VPN Server", server.name)
        assertEquals(51820, server.listenPort)
        assertEquals("test-vpn.example.com:51820", server.endpoint)
        assertTrue(server.privateKey.isNotEmpty())
        assertTrue(server.publicKey.isNotEmpty())
        assertEquals(1, server.addresses.size)
        assertEquals("10.0.0.1/24", server.addresses.first().address)
    }

    @Test
    fun `test createServer with duplicate name should fail`() {
        // Create first server
        managementService.createServer(
            CreateServerRequest(
                name = "Duplicate Server",
                networkAddress = "10.0.0.1/24",
                endpoint = "test1.example.com:51820"
            )
        )

        // Try to create server with same name
        assertFailsWith<IllegalArgumentException> {
            managementService.createServer(
                CreateServerRequest(
                    name = "Duplicate Server",
                    networkAddress = "10.1.0.1/24",
                    endpoint = "test2.example.com:51820"
                )
            )
        }
    }

    @Test
    fun `test createServer with duplicate port should fail`() {
        // Create first server
        managementService.createServer(
            CreateServerRequest(
                name = "Server 1",
                networkAddress = "10.0.0.1/24",
                listenPort = 51820,
                endpoint = "test1.example.com:51820"
            )
        )

        // Try to create server with same port
        assertFailsWith<IllegalArgumentException> {
            managementService.createServer(
                CreateServerRequest(
                    name = "Server 2",
                    networkAddress = "10.1.0.1/24",
                    listenPort = 51820,
                    endpoint = "test2.example.com:51820"
                )
            )
        }
    }

    @Test
    fun `test createClientForServer`() {
        // Create server first
        val server = managementService.createServer(
            CreateServerRequest(
                name = "Client Test Server",
                networkAddress = "192.168.100.1/24",
                endpoint = "server.example.com:51820"
            )
        )

        // Execute test
        val (client, privateKey) = managementService.createClientForServer(
            serverId = server.id,
            request = CreateClientRequest(name = "Test Client")
        )

        // Verify result
        assertNotNull(client.id)
        assertEquals("Test Client", client.name)
        assertTrue(client.publicKey.isNotEmpty())
        assertTrue(privateKey!!.isNotEmpty())
        assertEquals(1, client.allowedIPs.size)
        assertTrue(client.allowedIPs.first().address.startsWith("192.168.100."))
        assertEquals(server.id, client.server?.id)
    }

    @Test
    fun `test addClientToServer with existing public key should fail`() {
        // Create server
        val server = managementService.createServer(
            CreateServerRequest(
                name = "Duplicate Client Server",
                networkAddress = "10.5.0.1/24",
                endpoint = "dup.example.com:51820"
            )
        )

        val existingPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

        // Add first client
        managementService.addClientToServer(
            serverId = server.id,
            request = AddClientRequest(
                clientName = "First Client",
                clientPublicKey = existingPublicKey
            )
        )

        // Try to add client with same public key
        assertFailsWith<IllegalArgumentException> {
            managementService.addClientToServer(
                serverId = server.id,
                request = AddClientRequest(
                    clientName = "Second Client",
                    clientPublicKey = existingPublicKey
                )
            )
        }
    }

    @Test
    fun `test removeClientFromServer`() {
        // Create server and client
        val server = managementService.createServer(
            CreateServerRequest(
                name = "Remove Test Server",
                networkAddress = "10.10.0.1/24",
                endpoint = "remove.example.com:51820"
            )
        )

        val (client, _) = managementService.createClientForServer(
            serverId = server.id,
            request = CreateClientRequest(name = "Client to Remove")
        )

        // Verify client exists
        val clientsBefore = managementService.getServerClients(server.id)
        assertEquals(1, clientsBefore.size)

        // Execute test
        managementService.removeClientFromServer(server.id, client.id)

        // Verify client is removed
        val clientsAfter = managementService.getServerClients(server.id)
        assertEquals(0, clientsAfter.size)
    }

    @Test
    fun `test updateClientStatus`() {
        // Create server and client
        val server = managementService.createServer(
            CreateServerRequest(
                name = "Status Test Server",
                networkAddress = "10.20.0.1/24",
                endpoint = "status.example.com:51820"
            )
        )

        val (client, _) = managementService.createClientForServer(
            serverId = server.id,
            request = CreateClientRequest(name = "Status Test Client")
        )

        // Verify client is enabled by default
        assertTrue(client.enabled)

        // Execute test - disable client
        val disabledClient = managementService.updateClientStatus(client.id, false)

        // Verify result
        assertEquals(false, disabledClient.enabled)
        assertEquals(client.id, disabledClient.id)
        assertEquals(client.name, disabledClient.name)
    }

    @Test
    fun `test getServerStatistics`() {
        // Create server with clients
        val server = managementService.createServer(
            CreateServerRequest(
                name = "Stats Test Server",
                networkAddress = "10.30.0.1/24",
                endpoint = "stats.example.com:51820"
            )
        )

        val (client1, _) = managementService.createClientForServer(server.id, CreateClientRequest("Client 1"))
        val (client2, _) = managementService.createClientForServer(server.id, CreateClientRequest("Client 2"))

        // Update client statistics
        managementService.updateClientStats(
            client1.id,
            LocalDateTime.now(),
            1000,
            2000
        )

        managementService.updateClientStats(
            client2.id,
            LocalDateTime.now().minusMinutes(10), // Offline
            500,
            1500
        )

        // Execute test
        val stats = managementService.getServerStatistics(server.id)

        // Verify result
        assertNotNull(stats)
        assertEquals("Stats Test Server", stats["serverName"])
        assertEquals("stats.example.com:51820", stats["endpoint"])
        assertEquals(2, stats["totalClients"])
        assertEquals(1, stats["onlineClients"])
        assertEquals(1, stats["offlineClients"])
        assertEquals(1500L, stats["totalDataReceived"])
        assertEquals(3500L, stats["totalDataSent"])
    }

    @Test
    fun `test automatic IP allocation`() {
        // Create server
        val server = managementService.createServer(
            CreateServerRequest(
                name = "IP Allocation Server",
                networkAddress = "172.16.0.1/24",
                endpoint = "ip-test.example.com:51820"
            )
        )

        // Create multiple clients and verify IP allocation
        val (client1, _) = managementService.createClientForServer(server.id, CreateClientRequest("Client 1"))
        val (client2, _) = managementService.createClientForServer(server.id, CreateClientRequest("Client 2"))
        val (client3, _) = managementService.createClientForServer(server.id, CreateClientRequest("Client 3"))

        // Verify each client gets a unique IP
        val ip1 = client1.allowedIPs.firstOrNull()?.address
        val ip2 = client2.allowedIPs.firstOrNull()?.address
        val ip3 = client3.allowedIPs.firstOrNull()?.address

        assertNotNull(ip1)
        assertNotNull(ip2)
        assertNotNull(ip3)

        // All IPs should be different
        assertTrue(setOf(ip1, ip2, ip3).size == 3)

        // All IPs should be in the server's network range
        assertTrue(ip1!!.startsWith("172.16.0."))
        assertTrue(ip2!!.startsWith("172.16.0."))
        assertTrue(ip3!!.startsWith("172.16.0."))

        println("Allocated IPs: $ip1, $ip2, $ip3")
    }
}