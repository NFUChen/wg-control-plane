package com.app.service.ansible

import com.app.model.AnsibleHost
import com.app.model.AnsibleInventoryGroup
import com.app.view.ansible.CreateAnsibleHostRequest
import com.app.view.ansible.CreateAnsibleInventoryGroupRequest
import com.app.view.ansible.UpdateAnsibleHostRequest
import com.app.view.ansible.UpdateAnsibleInventoryGroupRequest
import java.time.LocalDateTime
import java.util.*

/**
 * Service interface that integrates all Ansible management components.
 * Provides a unified facade for managing Ansible hosts, groups, and inventory files.
 */
interface AnsibleManagementService {

    // ========== Host and Group Management ==========

    /**
     * Create a new Ansible host
     */
    fun createHost(request: CreateAnsibleHostRequest): AnsibleHost

    /**
     * Update an existing Ansible host
     */
    fun updateHost(id: UUID, request: UpdateAnsibleHostRequest): AnsibleHost

    /**
     * Delete host and cleanup any related inventory files
     */
    fun deleteHost(id: UUID)

    /**
     * Create a new inventory group
     */
    fun createGroup(request: CreateAnsibleInventoryGroupRequest): AnsibleInventoryGroup

    /**
     * Update an existing inventory group
     */
    fun updateGroup(id: UUID, request: UpdateAnsibleInventoryGroupRequest): AnsibleInventoryGroup

    /**
     * Delete group and cleanup any related inventory files
     */
    fun deleteGroup(id: UUID)

    // ========== Inventory Generation and Management ==========

    /**
     * Generate and save complete inventory to file
     */
    fun generateAndSaveInventory(
        filename: String? = null,
        includeMetadata: Boolean = true
    ): InventoryFileInfo

    /**
     * Generate and save inventory for a specific group
     */
    fun generateAndSaveGroupInventory(
        groupId: UUID,
        filename: String? = null,
        includeMetadata: Boolean = true
    ): InventoryFileInfo

    /**
     * Get inventory file content
     */
    fun getInventoryFile(filename: String): String

    /**
     * List all inventory files
     */
    fun listInventoryFiles(): List<InventoryFileInfo>

    /**
     * Delete inventory file
     */
    fun deleteInventoryFile(filename: String)

    /**
     * Validate current inventory configuration
     */
    fun validateCurrentInventory(): InventoryValidationResult

    /**
     * Get comprehensive statistics
     */
    fun getComprehensiveStatistics(): AnsibleManagementStatistics

    /**
     * Cleanup old inventory files and perform maintenance
     */
    fun performMaintenance(
        deleteOlderThanDays: Int = 30,
        validateInventory: Boolean = true
    ): MaintenanceResult

    // ========== Delegation Methods for Common Operations ==========

    fun getHost(id: UUID): AnsibleHost?
    fun getHostByHostname(hostname: String): AnsibleHost
    fun getAllHosts(): List<AnsibleHost>
    fun getEnabledHosts(): List<AnsibleHost>
    fun getUngroupedHosts(): List<AnsibleHost>

    fun getGroup(id: UUID): AnsibleInventoryGroup
    fun getGroupByName(name: String): AnsibleInventoryGroup
    fun getAllGroups(): List<AnsibleInventoryGroup>
    fun getEnabledGroups(): List<AnsibleInventoryGroup>

    fun getHostsByGroup(group: AnsibleInventoryGroup): List<AnsibleHost>
    fun getEnabledHostsByGroup(group: AnsibleInventoryGroup): List<AnsibleHost>
}

/**
 * Result of inventory validation
 */
data class InventoryValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val hostCount: Int,
    val groupCount: Int,
    val inventoryContent: String
)

/**
 * Comprehensive statistics for Ansible management
 */
data class AnsibleManagementStatistics(
    val hostStatistics: AnsibleStatistics,
    val inventoryFileCount: Int,
    val totalInventoryFileSize: Long,
    val oldestInventoryFile: LocalDateTime?,
    val newestInventoryFile: LocalDateTime?
)

/**
 * Result of maintenance operations
 */
data class MaintenanceResult(
    val cleanupResult: CleanupResult?,
    val validationResult: InventoryValidationResult?,
    val maintenanceCompletedAt: LocalDateTime,
    val errors: List<String>
)