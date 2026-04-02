package com.app.service.ansible

import com.app.model.AnsibleHost
import com.app.model.AnsibleInventoryGroup
import com.app.model.PrivateKey
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnsibleInventoryGeneratorTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var generator: AnsibleInventoryGenerator

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper()
        generator = AnsibleInventoryGenerator(objectMapper)
    }

    @Test
    fun `should generate inventory with ungrouped hosts`() {
        val privateKey = PrivateKey(
            id = UUID.randomUUID(),
            name = "test-key",
            content = "test-private-key",
            enabled = true
        )

        val hosts = listOf(
            AnsibleHost(
                name = "server1",
                hostname = "server1.example.com",
                ipAddress = "192.168.1.10",
                sshUsername = "ubuntu",
                sshPrivateKey = privateKey
            ),
            AnsibleHost(
                name = "server2",
                hostname = "server2.example.com",
                ipAddress = "192.168.1.11",
                sshUsername = "admin",
                sshPort = 2222,
                sshPrivateKey = privateKey
            )
        )

        val inventory = generator.generateInventory(hosts, emptyList())

        assertContains(inventory, "[ungrouped]")
        assertContains(inventory, "server1.example.com")
        assertContains(inventory, "ansible_host=192.168.1.10")
        assertContains(inventory, "ansible_user=ubuntu")
        assertContains(inventory, "ansible_port=2222")
        assertContains(inventory, "ansible_user=admin")
    }

    @Test
    fun `should generate inventory with grouped hosts`() {
        val group = AnsibleInventoryGroup(
            name = "webservers",
            description = "Web servers group",
            variables = """{"nginx_version": "1.20", "ssl_enabled": true}"""
        )

        val privateKey = PrivateKey(
            id = UUID.randomUUID(),
            name = "web-key",
            content = "web-private-key",
            enabled = true
        )

        val hosts = listOf(
            AnsibleHost(
                name = "web1",
                hostname = "web1.example.com",
                ipAddress = "192.168.1.20",
                sshUsername = "ubuntu",
                sshPrivateKey = privateKey,
                ansibleInventoryGroup = group
            )
        )

        val inventory = generator.generateInventory(hosts, listOf(group))

        assertContains(inventory, "[webservers]")
        assertContains(inventory, "[webservers:vars]")
        assertContains(inventory, "nginx_version=1.20")
        assertContains(inventory, "ssl_enabled=true")
        assertContains(inventory, "web1.example.com")
    }

    @Test
    fun `should handle hosts with sudo configuration`() {
        val privateKey = PrivateKey(
            id = UUID.randomUUID(),
            name = "sudo-key",
            content = "sudo-private-key",
            enabled = true
        )

        val hosts = listOf(
            AnsibleHost(
                name = "secure-server",
                hostname = "secure.example.com",
                ipAddress = "192.168.1.30",
                sshUsername = "admin",
                sshPrivateKey = privateKey,
                sudoRequired = true,
                sudoPassword = "sudo-pass"
            )
        )

        val inventory = generator.generateInventory(hosts, emptyList())

        assertContains(inventory, "ansible_become=true")
        assertContains(inventory, "ansible_become_method=sudo")
        assertContains(inventory, "ansible_become_password=sudo-pass")
    }

    @Test
    fun `should exclude disabled hosts`() {
        val privateKey = PrivateKey(
            id = UUID.randomUUID(),
            name = "test-key",
            content = "test-private-key",
            enabled = true
        )

        val hosts = listOf(
            AnsibleHost(
                name = "enabled-server",
                hostname = "enabled.example.com",
                ipAddress = "192.168.1.50",
                sshUsername = "ubuntu",
                sshPrivateKey = privateKey,
                enabled = true
            ),
            AnsibleHost(
                name = "disabled-server",
                hostname = "disabled.example.com",
                ipAddress = "192.168.1.51",
                sshUsername = "ubuntu",
                sshPrivateKey = privateKey,
                enabled = false
            )
        )

        val inventory = generator.generateInventory(hosts, emptyList())

        assertContains(inventory, "enabled.example.com")
        assertFalse(inventory.contains("disabled.example.com"))
    }

    @Test
    fun `should handle empty input gracefully`() {
        val inventory = generator.generateInventory(emptyList(), emptyList())
        assertEquals("", inventory)
    }

    @Test
    fun `should validate inventory content correctly`() {
        val validInventory = """
            [webservers]
            web1.example.com ansible_host=192.168.1.10

            [webservers:vars]
            nginx_version=1.20
        """.trimIndent()

        val errors = generator.validateInventoryContent(validInventory)
        assertTrue(errors.isEmpty(), "Valid inventory should not have errors")
    }

    @Test
    fun `should detect empty inventory`() {
        val errors = generator.validateInventoryContent("")
        assertTrue(errors.isNotEmpty(), "Empty inventory should have errors")
        assertContains(errors[0], "cannot be empty")
    }
}