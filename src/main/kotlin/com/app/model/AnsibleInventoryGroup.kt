package com.app.model

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import java.util.*

/**
 * Represents an Ansible inventory group for organizing Linux instances
 */
@Entity
@Table(name = "ansible_inventory_groups")
data class AnsibleInventoryGroup(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, unique = true)
    val name: String,

    @Column(name = "description")
    val description: String? = null,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Column(name = "variables", columnDefinition = "TEXT")
    val variables: String? = null, // JSON object of group variables

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {

    /**
     * Validate inventory group configuration
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (name.isBlank()) {
            errors.add("Inventory group name cannot be blank")
        }

        // Validate that name follows Ansible inventory naming conventions
        if (!isValidInventoryGroupName(name)) {
            errors.add("Invalid inventory group name: $name. Must contain only letters, numbers, underscores, and hyphens")
        }

        return errors
    }

    private fun isValidInventoryGroupName(name: String): Boolean {
        // Ansible inventory group names should only contain alphanumeric characters, underscores, and hyphens
        // and cannot start with a number
        return name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_-]*$"))
    }
}