package com.app.service.ansible

import com.app.model.AnsibleHost
import com.app.model.AnsibleInventoryGroup
import com.app.view.ansible.CreateAnsibleHostRequest
import com.app.view.ansible.CreateAnsibleInventoryGroupRequest
import com.app.view.ansible.UpdateAnsibleHostRequest
import com.app.view.ansible.UpdateAnsibleInventoryGroupRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

/**
 * Default implementation of AnsibleManagementService.
 * Integrates all Ansible management components and provides a unified facade.
 */
@Service
@Transactional
class DefaultAnsibleManagementService(
    private val ansibleService: AnsibleService,
    private val inventoryFileService: InventoryFileService
) : AnsibleManagementService {

    // ========== Host and Group Management ==========

    override fun createHost(request: CreateAnsibleHostRequest): AnsibleHost {
        return ansibleService.createHost(request)
    }

    override fun updateHost(id: UUID, request: UpdateAnsibleHostRequest): AnsibleHost {
        return ansibleService.updateHost(id, request)
    }

    override fun deleteHost(id: UUID) {
        val host = ansibleService.getHost(id)
        ansibleService.deleteHost(id)

        // Optionally cleanup host-specific inventory files
        cleanupHostInventoryFiles(host.name)

    }

    override fun createGroup(request: CreateAnsibleInventoryGroupRequest): AnsibleInventoryGroup {
        return ansibleService.createGroup(request)
    }

    override fun updateGroup(id: UUID, request: UpdateAnsibleInventoryGroupRequest): AnsibleInventoryGroup {
        return ansibleService.updateGroup(id, request)
    }

    override fun deleteGroup(id: UUID) {
        val group = ansibleService.getGroup(id)
        ansibleService.deleteGroup(id)

        // Cleanup group-specific inventory files
        cleanupGroupInventoryFiles(group.name)

    }

    // ========== Inventory Generation and Management ==========

    override fun generateAndSaveInventory(
        filename: String?,
        includeMetadata: Boolean
    ): InventoryFileInfo {
        val inventory = ansibleService.generateInventory()

        // Validate inventory before saving
        val validationErrors = ansibleService.validateInventory()
        if (validationErrors.isNotEmpty()) {
            throw IllegalStateException("Inventory validation failed: ${validationErrors.joinToString(", ")}")
        }

        val metadata = if (includeMetadata) {
            val stats = ansibleService.getStatistics()
            InventoryFileMetadata(
                description = "Complete Ansible inventory",
                hostCount = stats.enabledHosts,
                generatedAt = LocalDateTime.now(),
                tags = listOf("complete", "all-hosts")
            )
        } else null

        return if (filename != null) {
            inventoryFileService.writeInventoryFile(filename, inventory, metadata)
        } else {
            inventoryFileService.writeInventoryWithAutoFilename(
                content = inventory,
                prefix = "complete-inventory",
                metadata = metadata
            )
        }
    }

    override fun generateAndSaveGroupInventory(
        groupId: UUID,
        filename: String?,
        includeMetadata: Boolean
    ): InventoryFileInfo {
        val group = ansibleService.getGroup(groupId)

        val inventory = ansibleService.generateGroupInventory(groupId)

        val metadata = if (includeMetadata) {
            val hosts = ansibleService.getEnabledHostsByGroup(group)
            InventoryFileMetadata(
                description = "Inventory for group '${group.name}'",
                groupName = group.name,
                hostCount = hosts.size,
                generatedAt = LocalDateTime.now(),
                tags = listOf("group", group.name)
            )
        } else null

        return if (filename != null) {
            inventoryFileService.writeInventoryFile(filename, inventory, metadata)
        } else {
            inventoryFileService.writeInventoryWithAutoFilename(
                content = inventory,
                prefix = "group-inventory",
                groupName = group.name,
                metadata = metadata
            )
        }
    }

    override fun getInventoryFile(filename: String): String {
        return inventoryFileService.readInventoryFile(filename)
    }

    override fun listInventoryFiles(): List<InventoryFileInfo> {
        return inventoryFileService.listInventoryFiles()
    }

    override fun deleteInventoryFile(filename: String) {
        inventoryFileService.deleteInventoryFile(filename)
    }

    override fun validateCurrentInventory(): InventoryValidationResult {
        return try {
            val inventory = ansibleService.generateInventory()
            val validationErrors = ansibleService.validateInventory()
            val stats = ansibleService.getStatistics()

            // Additional validation checks
            val additionalChecks = mutableListOf<String>()

            // Check for hosts without SSH keys
            val hostsWithoutKeys = ansibleService.getEnabledHosts().filter { host ->
                !host.sshPrivateKey.enabled
            }
            if (hostsWithoutKeys.isNotEmpty()) {
                additionalChecks.add("${hostsWithoutKeys.size} host(s) without valid SSH private keys")
            }

            // Check for duplicate IP addresses
            val enabledHosts = ansibleService.getEnabledHosts()
            val duplicateIPs = enabledHosts.groupBy { it.ipAddress }
                .filter { it.value.size > 1 }
                .keys
            if (duplicateIPs.isNotEmpty()) {
                additionalChecks.add("Duplicate IP addresses found: ${duplicateIPs.joinToString(", ")}")
            }

            // Check for ungrouped hosts
            if (stats.ungroupedHosts > 0) {
                additionalChecks.add("${stats.ungroupedHosts} ungrouped host(s) found")
            }

            val allErrors = validationErrors + additionalChecks

            InventoryValidationResult(
                isValid = allErrors.isEmpty(),
                errors = allErrors,
                warnings = emptyList(), // Could add warnings here
                hostCount = stats.enabledHosts,
                groupCount = stats.enabledGroups,
                inventoryContent = inventory
            )
        } catch (e: Exception) {
            InventoryValidationResult(
                isValid = false,
                errors = listOf("Validation failed: ${e.message}"),
                warnings = emptyList(),
                hostCount = 0,
                groupCount = 0,
                inventoryContent = ""
            )
        }
    }

    override fun getComprehensiveStatistics(): AnsibleManagementStatistics {
        val ansibleStats = ansibleService.getStatistics()
        val inventoryFiles = inventoryFileService.listInventoryFiles()

        return AnsibleManagementStatistics(
            hostStatistics = ansibleStats,
            inventoryFileCount = inventoryFiles.size,
            totalInventoryFileSize = inventoryFiles.sumOf { it.size },
            oldestInventoryFile = inventoryFiles.minByOrNull { it.createdAt }?.createdAt,
            newestInventoryFile = inventoryFiles.maxByOrNull { it.createdAt }?.createdAt
        )
    }

    override fun performMaintenance(
        deleteOlderThanDays: Int,
        validateInventory: Boolean
    ): MaintenanceResult {
        return try {
            val cleanupResult = inventoryFileService.cleanupOldFiles(deleteOlderThanDays)
            val validationResult = if (validateInventory) {
                validateCurrentInventory()
            } else null

            MaintenanceResult(
                cleanupResult = cleanupResult,
                validationResult = validationResult,
                maintenanceCompletedAt = LocalDateTime.now(),
                errors = emptyList()
            )
        } catch (e: Exception) {
            MaintenanceResult(
                cleanupResult = null,
                validationResult = null,
                maintenanceCompletedAt = LocalDateTime.now(),
                errors = listOf("Maintenance failed: ${e.message}")
            )
        }
    }

    // ========== Private Helper Methods ==========

    private fun cleanupHostInventoryFiles(hostName: String) {
        try {
            val filesToDelete = inventoryFileService.listInventoryFiles().filter { file ->
                file.filename.contains(hostName, ignoreCase = true) ||
                file.metadata?.tags?.contains(hostName) == true
            }

            filesToDelete.forEach { file ->
                inventoryFileService.deleteInventoryFile(file.filename)
            }
        } catch (e: Exception) {
            // Log error but don't fail the operation
        }
    }

    private fun cleanupGroupInventoryFiles(groupName: String) {
        try {
            val filesToDelete = inventoryFileService.listInventoryFiles().filter { file ->
                file.metadata?.groupName == groupName ||
                file.filename.contains(groupName, ignoreCase = true)
            }

            filesToDelete.forEach { file ->
                inventoryFileService.deleteInventoryFile(file.filename)
            }
        } catch (e: Exception) {
            // Log error but don't fail the operation
        }
    }

    // ========== Delegation Methods for Common Operations ==========

    override fun getHost(id: UUID): AnsibleHost = ansibleService.getHost(id)
    override fun getHostByName(name: String): AnsibleHost = ansibleService.getHostByName(name)
    override fun getAllHosts(): List<AnsibleHost> = ansibleService.getAllHosts()
    override fun getEnabledHosts(): List<AnsibleHost> = ansibleService.getEnabledHosts()
    override fun getUngroupedHosts(): List<AnsibleHost> = ansibleService.getUngroupedHosts()

    override fun getGroup(id: UUID): AnsibleInventoryGroup = ansibleService.getGroup(id)
    override fun getGroupByName(name: String): AnsibleInventoryGroup = ansibleService.getGroupByName(name)
    override fun getAllGroups(): List<AnsibleInventoryGroup> = ansibleService.getAllGroups()
    override fun getEnabledGroups(): List<AnsibleInventoryGroup> = ansibleService.getEnabledGroups()

    override fun getHostsByGroup(group: AnsibleInventoryGroup): List<AnsibleHost> =
        ansibleService.getHostsByGroup(group)
    override fun getEnabledHostsByGroup(group: AnsibleInventoryGroup): List<AnsibleHost> =
        ansibleService.getEnabledHostsByGroup(group)
}