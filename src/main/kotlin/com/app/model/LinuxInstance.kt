package com.app.model

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.*

/**
 * Represents a remote Linux instance for Ansible-based WireGuard management
 */
@Entity
@Table(name = "linux_instances")
data class LinuxInstance(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, unique = true)
    val name: String,

    @Column(name = "hostname", nullable = false)
    val hostname: String,

    @Column(name = "ip_address", nullable = false)
    val ipAddress: String,

    @Column(name = "ssh_port", nullable = false)
    val sshPort: Int = 22,

    @Column(name = "ssh_username", nullable = false)
    val sshUsername: String,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ssh_private_key_id")
    val sshPrivateKey: PrivateKey? = null,

    @Column(name = "ssh_password_fallback")
    val sshPasswordFallback: String? = null,

    @Column(name = "ansible_inventory_group")
    val ansibleInventoryGroup: String? = null,

    @Column(name = "sudo_required", nullable = false)
    val sudoRequired: Boolean = true,

    @Column(name = "sudo_password")
    val sudoPassword: String? = null,

    @Column(name = "python_interpreter")
    val pythonInterpreter: String? = "/usr/bin/python3",

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Column(name = "tags", columnDefinition = "TEXT")
    val tags: String? = null, // JSON array of tags

    @Column(name = "description")
    val description: String? = null,

    @Column(name = "annotation", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    val annotation: Map<String, Any> = mutableMapOf(),

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {

    /**
     * Validate instance configuration
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (name.isBlank()) {
            errors.add("Instance name cannot be blank")
        }

        if (hostname.isBlank()) {
            errors.add("Hostname cannot be blank")
        }

        if (ipAddress.isBlank()) {
            errors.add("IP address cannot be blank")
        }

        // Basic IP address format validation
        if (!isValidIpAddress(ipAddress)) {
            errors.add("Invalid IP address format: $ipAddress")
        }

        if (sshPort !in 1..65535) {
            errors.add("SSH port must be between 1 and 65535")
        }

        if (sshUsername.isBlank()) {
            errors.add("SSH username cannot be blank")
        }

        // Either SSH private key or password fallback must be provided
        if (sshPrivateKey == null && sshPasswordFallback.isNullOrBlank()) {
            errors.add("Either SSH private key or SSH password fallback must be provided")
        }

        // Validate SSH private key if provided
        sshPrivateKey?.let { key ->
            if (!key.enabled) {
                errors.add("SSH private key is disabled")
            }
        }

        return errors
    }

    private fun isValidIpAddress(ip: String): Boolean {
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return false
            parts.all { part ->
                val num = part.toIntOrNull()
                num != null && num in 0..255
            }
        } catch (e: Exception) {
            false
        }
    }
}