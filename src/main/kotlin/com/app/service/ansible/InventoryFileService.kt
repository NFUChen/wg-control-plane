package com.app.service.ansible

import java.time.LocalDateTime

/**
 * Service interface for managing Ansible inventory file storage and operations.
 * Provides abstraction layer for different storage mechanisms.
 */
interface InventoryFileService {

    /**
     * Write inventory content to a file
     */
    fun writeInventoryFile(
        filename: String,
        content: String,
        metadata: InventoryFileMetadata? = null
    ): InventoryFileInfo

    /**
     * Read inventory file content
     */
    fun readInventoryFile(filename: String): String

    /**
     * Delete inventory file
     */
    fun deleteInventoryFile(filename: String)

    /**
     * List all inventory files
     */
    fun listInventoryFiles(): List<InventoryFileInfo>

    /**
     * Check if inventory file exists
     */
    fun inventoryFileExists(filename: String): Boolean

    /**
     * Generate a unique filename for inventory
     */
    fun generateInventoryFilename(
        prefix: String = "inventory",
        groupName: String? = null,
        includeTimestamp: Boolean = true
    ): String

    /**
     * Write inventory with automatic filename generation
     */
    fun writeInventoryWithAutoFilename(
        content: String,
        prefix: String = "inventory",
        groupName: String? = null,
        metadata: InventoryFileMetadata? = null
    ): InventoryFileInfo

    /**
     * Cleanup old inventory files
     */
    fun cleanupOldFiles(olderThanDays: Int = 30): CleanupResult
}

/**
 * Metadata information for inventory files
 */
data class InventoryFileMetadata(
    val description: String? = null,
    val groupName: String? = null,
    val hostCount: Int = 0,
    val generatedAt: LocalDateTime = LocalDateTime.now(),
    val tags: List<String> = emptyList()
)

/**
 * Information about an inventory file
 */
data class InventoryFileInfo(
    val filename: String,
    val path: String,
    val size: Long,
    val createdAt: LocalDateTime,
    val metadata: InventoryFileMetadata? = null
)

/**
 * Result of cleanup operation
 */
data class CleanupResult(
    val deletedCount: Long,
    val errorCount: Long,
    val deletedFiles: List<String>
)