package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.dto.*
import com.module.wgcontrolplane.model.*
import com.module.wgcontrolplane.repository.WireGuardServerRepository
import com.module.wgcontrolplane.repository.WireGuardClientRepository
import jakarta.persistence.EntityManager
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

    @Autowired
    private lateinit var entityManager: EntityManager

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
    fun `test addClientToServer`() {
        // Create server first
        val server = managementService.createServer(
            CreateServerRequest(
                name = "Client Test Server",
                networkAddress = "192.168.100.1/24",
                endpoint = "server.example.com:51820"
            )
        )

        // Execute test
        val client = managementService.addClientToServer(
            serverId = server.id,
            request = AddClientRequest(
                clientName = "Test Client",
                addresses = listOf(IPAddress("192.168.100.2/32"))
            )
        )

        // Verify result
        assertNotNull(client.id)
        assertEquals("Test Client", client.name)
        assertTrue(client.publicKey.isNotEmpty())
        assertTrue(client.privateKey.isNotEmpty())
        assertEquals(1, client.allowedIPs.size)
        assertEquals("192.168.100.2/32", client.allowedIPs.first().address)
        assertEquals(server.id, client.server?.id)
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

        val client = managementService.addClientToServer(
            serverId = server.id,
            request = AddClientRequest(
                clientName = "Client to Remove",
                addresses = listOf(IPAddress("10.10.0.2/32"))
            )
        )

        // Verify client exists
        assertEquals(1, managementService.getServerClients(server.id).size)

        // Execute test
        managementService.removeClientFromServer(server.id, client.id)

        // Flush and clear persistence context to force fresh query
        entityManager.flush()
        entityManager.clear()

        // Verify client is removed by querying the database
        val clientsAfter = clientRepository.findByServerId(server.id)
        assertEquals(0, clientsAfter.size)
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

        val client1 = managementService.addClientToServer(
            server.id,
            AddClientRequest(
                clientName = "Client 1",
                addresses = listOf(IPAddress("10.30.0.2/32"))
            )
        )
        val client2 = managementService.addClientToServer(
            server.id,
            AddClientRequest(
                clientName = "Client 2",
                addresses = listOf(IPAddress("10.30.0.3/32"))
            )
        )

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
    fun `test multiple clients on same server`() {
        // Create server
        val server = managementService.createServer(
            CreateServerRequest(
                name = "Multi Client Server",
                networkAddress = "172.16.0.1/24",
                endpoint = "multi.example.com:51820"
            )
        )

        // Create multiple clients
        val client1 = managementService.addClientToServer(
            server.id,
            AddClientRequest(
                clientName = "Client 1",
                addresses = listOf(IPAddress("172.16.0.2/32"))
            )
        )
        val client2 = managementService.addClientToServer(
            server.id,
            AddClientRequest(
                clientName = "Client 2",
                addresses = listOf(IPAddress("172.16.0.3/32"))
            )
        )
        val client3 = managementService.addClientToServer(
            server.id,
            AddClientRequest(
                clientName = "Client 3",
                addresses = listOf(IPAddress("172.16.0.4/32"))
            )
        )

        // Verify each client has correct data
        val clients = managementService.getServerClients(server.id)
        assertEquals(3, clients.size)

        // Verify all IPs are different
        val ips = clients.map { it.allowedIPs.first().address }.toSet()
        assertEquals(3, ips.size)

        println("Client IPs: $ips")
    }
}
