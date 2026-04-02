package com.app.web.dto

import com.app.service.ansible.AnsibleManagementStatistics
import com.app.service.ansible.CleanupResult
import com.app.service.ansible.InventoryValidationResult
import com.app.service.ansible.MaintenanceResult
import java.time.LocalDateTime

/**
 * Type-safe response DTOs for Ansible API endpoints
 */

/**
 * Response for inventory validation endpoint
 */
data class InventoryValidationResponse(
    val valid: Boolean,
    val hostCount: Int,
    val groupCount: Int,
    val message: String?,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    companion object {
        fun fromValidationResult(result: InventoryValidationResult): InventoryValidationResponse {
            return if (result.isValid) {
                InventoryValidationResponse(
                    valid = true,
                    hostCount = result.hostCount,
                    groupCount = result.groupCount,
                    message = "Inventory is valid",
                    errors = emptyList(),
                    warnings = result.warnings
                )
            } else {
                InventoryValidationResponse(
                    valid = false,
                    hostCount = result.hostCount,
                    groupCount = result.groupCount,
                    message = "Inventory validation failed",
                    errors = result.errors,
                    warnings = result.warnings
                )
            }
        }
    }
}

/**
 * Response for statistics endpoint
 */
data class AnsibleStatisticsResponse(
    val hostStatistics: HostStatistics,
    val inventoryFileStatistics: InventoryFileStatistics
) {
    companion object {
        fun fromManagementStatistics(stats: AnsibleManagementStatistics): AnsibleStatisticsResponse {
            return AnsibleStatisticsResponse(
                hostStatistics = HostStatistics(
                    totalHosts = stats.hostStatistics.totalHosts,
                    enabledHosts = stats.hostStatistics.enabledHosts,
                    disabledHosts = stats.hostStatistics.disabledHosts,
                    ungroupedHosts = stats.hostStatistics.ungroupedHosts,
                    totalGroups = stats.hostStatistics.totalGroups,
                    enabledGroups = stats.hostStatistics.enabledGroups,
                    disabledGroups = stats.hostStatistics.disabledGroups
                ),
                inventoryFileStatistics = InventoryFileStatistics(
                    fileCount = stats.inventoryFileCount,
                    totalSize = stats.totalInventoryFileSize,
                    oldestFile = stats.oldestInventoryFile,
                    newestFile = stats.newestInventoryFile
                )
            )
        }
    }
}

/**
 * Host statistics sub-object
 */
data class HostStatistics(
    val totalHosts: Long,
    val enabledHosts: Int,
    val disabledHosts: Long,
    val ungroupedHosts: Int,
    val totalGroups: Long,
    val enabledGroups: Int,
    val disabledGroups: Long
)

/**
 * Inventory file statistics sub-object
 */
data class InventoryFileStatistics(
    val fileCount: Int,
    val totalSize: Long,
    val oldestFile: LocalDateTime?,
    val newestFile: LocalDateTime?
)

/**
 * Response for maintenance endpoint
 */
data class MaintenanceResponse(
    val success: Boolean,
    val maintenanceCompletedAt: LocalDateTime,
    val cleanup: CleanupResponse?,
    val validation: InventoryValidationResponse?,
    val errors: List<String>
) {
    companion object {
        fun fromMaintenanceResult(result: MaintenanceResult): MaintenanceResponse {
            return MaintenanceResponse(
                success = result.errors.isEmpty(),
                maintenanceCompletedAt = result.maintenanceCompletedAt,
                cleanup = result.cleanupResult?.let { CleanupResponse.fromCleanupResult(it) },
                validation = result.validationResult?.let { InventoryValidationResponse.fromValidationResult(it) },
                errors = result.errors
            )
        }
    }
}

/**
 * Cleanup result sub-object
 */
data class CleanupResponse(
    val deletedCount: Long,
    val errorCount: Long,
    val deletedFiles: List<String>
) {
    companion object {
        fun fromCleanupResult(result: CleanupResult): CleanupResponse {
            return CleanupResponse(
                deletedCount = result.deletedCount,
                errorCount = result.errorCount,
                deletedFiles = result.deletedFiles
            )
        }
    }
}