package com.app.service.ansible

import com.app.model.AnsibleHost
import com.app.model.AnsibleInventoryGroup
import com.app.view.ansible.CreateAnsibleHostRequest
import com.app.view.ansible.CreateAnsibleInventoryGroupRequest
import com.app.view.ansible.UpdateAnsibleHostRequest
import com.app.view.ansible.UpdateAnsibleInventoryGroupRequest
import java.util.*

/**
 * Service interface for managing Ansible hosts and inventory groups.
 * Provides business logic layer for Ansible-related operations.
 */
interface AnsibleService {

    // ========== Host Management ==========

    /**
     * Create a new Ansible host
     */
    fun createHost(request: CreateAnsibleHostRequest): AnsibleHost

    /**
     * Update an existing Ansible host
     */
    fun updateHost(id: UUID, request: UpdateAnsibleHostRequest): AnsibleHost

    /**
     * Get host by ID
     */
    fun getHost(id: UUID): AnsibleHost

    /**
     * Get host by name
     */
    fun getHostByName(name: String): AnsibleHost

    /**
     * Get all hosts
     */
    fun getAllHosts(): List<AnsibleHost>

    /**
     * Get all enabled hosts
     */
    fun getEnabledHosts(): List<AnsibleHost>

    /**
     * Get hosts by inventory group
     */
    fun getHostsByGroup(group: AnsibleInventoryGroup): List<AnsibleHost>

    /**
     * Get enabled hosts by inventory group
     */
    fun getEnabledHostsByGroup(group: AnsibleInventoryGroup): List<AnsibleHost>

    /**
     * Get ungrouped hosts
     */
    fun getUngroupedHosts(): List<AnsibleHost>

    /**
     * Delete host by ID
     */
    fun deleteHost(id: UUID)

    // ========== Inventory Group Management ==========

    /**
     * Create a new inventory group
     */
    fun createGroup(request: CreateAnsibleInventoryGroupRequest): AnsibleInventoryGroup

    /**
     * Update an existing inventory group
     */
    fun updateGroup(id: UUID, request: UpdateAnsibleInventoryGroupRequest): AnsibleInventoryGroup

    /**
     * Get group by ID
     */
    fun getGroup(id: UUID): AnsibleInventoryGroup

    /**
     * Get group by name
     */
    fun getGroupByName(name: String): AnsibleInventoryGroup

    /**
     * Get all groups
     */
    fun getAllGroups(): List<AnsibleInventoryGroup>

    /**
     * Get all enabled groups
     */
    fun getEnabledGroups(): List<AnsibleInventoryGroup>

    /**
     * Delete group by ID
     */
    fun deleteGroup(id: UUID)

    // ========== Inventory Generation ==========

    /**
     * Generate Ansible inventory for all hosts and groups
     */
    fun generateInventory(): String

    /**
     * Generate Ansible inventory for a specific group
     */
    fun generateGroupInventory(groupId: UUID): String

    /**
     * Validate generated inventory content
     */
    fun validateInventory(): List<String>

    // ========== Statistics and Information ==========

    /**
     * Get statistics about hosts and groups
     */
    fun getStatistics(): AnsibleStatistics
}

/**
 * Statistics about Ansible hosts and groups
 */
data class AnsibleStatistics(
    val totalHosts: Long,
    val enabledHosts: Int,
    val disabledHosts: Long,
    val ungroupedHosts: Int,
    val totalGroups: Long,
    val enabledGroups: Int,
    val disabledGroups: Long
)