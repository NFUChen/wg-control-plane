package com.app.repository

import com.app.model.AnsibleInventoryGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository interface for AnsibleInventoryGroup entities
 */
@Repository
interface AnsibleInventoryGroupRepository : JpaRepository<AnsibleInventoryGroup, UUID> {

    /**
     * Find all enabled inventory groups
     */
    fun findByEnabledTrue(): List<AnsibleInventoryGroup>

    /**
     * Find inventory group by name
     */
    fun findByName(name: String): AnsibleInventoryGroup?

    /**
     * Check if inventory group with name exists
     */
    fun existsByName(name: String): Boolean

    /**
     * Find all inventory groups by name pattern (case insensitive)
     */
    fun findByNameContainingIgnoreCase(namePattern: String): List<AnsibleInventoryGroup>
}