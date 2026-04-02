package com.app.service.ansible

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@ConfigurationProperties(prefix = "ansible.inventory")
data class InventoryProperties(
    val basePath: String = "/tmp/ansible-inventories",
    val filePermissions: String = "644"
)

/**
 * Default implementation of InventoryFileService.
 * Provides file system based storage for Ansible inventory files.
 */
@Service
class DefaultInventoryFileService(
    private val properties: InventoryProperties
) : InventoryFileService {

    private val inventoryPath: Path by lazy {
        val path = Path.of(properties.basePath)
        if (!Files.exists(path)) {
            Files.createDirectories(path)
        }
        path
    }

    override fun writeInventoryFile(
        filename: String,
        content: String,
        metadata: InventoryFileMetadata?
    ): InventoryFileInfo {
        validateFilename(filename)

        val filePath = inventoryPath.resolve(sanitizeFilename(filename))

        // Write content to file
        Files.writeString(
            filePath,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )

        // Write metadata if provided
        metadata?.let { writeMetadataFile(filePath, it) }

        // Set file permissions (Unix-like systems only)
        try {
            Runtime.getRuntime().exec("chmod ${properties.filePermissions} ${filePath.toAbsolutePath()}")
        } catch (e: Exception) {
            // Ignore permission errors on non-Unix systems
        }

        return InventoryFileInfo(
            filename = filename,
            path = filePath.toAbsolutePath().toString(),
            size = Files.size(filePath),
            createdAt = LocalDateTime.now(),
            metadata = metadata
        )
    }

    override fun readInventoryFile(filename: String): String {
        validateFilename(filename)

        val filePath = inventoryPath.resolve(sanitizeFilename(filename))

        if (!Files.exists(filePath)) {
            throw NoSuchElementException("Inventory file '$filename' does not exist")
        }

        return Files.readString(filePath)
    }

    override fun deleteInventoryFile(filename: String) {
        validateFilename(filename)

        val filePath = inventoryPath.resolve(sanitizeFilename(filename))
        val metadataPath = getMetadataPath(filePath)

        // Delete main file
        if (Files.exists(filePath)) {
            Files.delete(filePath)
        } else {
            throw NoSuchElementException("Inventory file '$filename' does not exist")
        }

        // Delete metadata file if exists
        if (Files.exists(metadataPath)) {
            Files.delete(metadataPath)
        }
    }

    override fun listInventoryFiles(): List<InventoryFileInfo> {
        val files = mutableListOf<InventoryFileInfo>()

        Files.list(inventoryPath).use { stream ->
            stream
                .filter { path ->
                    Files.isRegularFile(path) &&
                    !path.fileName.toString().endsWith(".metadata.json")
                }
                .forEach { path ->
                    try {
                        val filename = path.fileName.toString()
                        val size = Files.size(path)
                        val createdAt = Files.getLastModifiedTime(path).toInstant()
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()

                        val metadata = try {
                            readMetadataFile(path)
                        } catch (e: Exception) {
                            null
                        }

                        files.add(
                            InventoryFileInfo(
                                filename = filename,
                                path = path.toAbsolutePath().toString(),
                                size = size,
                                createdAt = createdAt,
                                metadata = metadata
                            )
                        )
                    } catch (e: Exception) {
                        // Skip files that can't be read
                    }
                }
        }

        return files.sortedByDescending { it.createdAt }
    }

    override fun inventoryFileExists(filename: String): Boolean {
        return try {
            validateFilename(filename)
            val filePath = inventoryPath.resolve(sanitizeFilename(filename))
            Files.exists(filePath)
        } catch (e: Exception) {
            false
        }
    }

    override fun generateInventoryFilename(
        prefix: String,
        groupName: String?,
        includeTimestamp: Boolean
    ): String {
        val sanitizedPrefix = sanitizeFilename(prefix)
        val sanitizedGroup = groupName?.let { sanitizeFilename(it) }
        val timestamp = if (includeTimestamp) {
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
        } else {
            null
        }

        val parts = listOfNotNull(sanitizedPrefix, sanitizedGroup, timestamp)
        return "${parts.joinToString("-")}.ini"
    }

    override fun writeInventoryWithAutoFilename(
        content: String,
        prefix: String,
        groupName: String?,
        metadata: InventoryFileMetadata?
    ): InventoryFileInfo {
        val filename = generateInventoryFilename(prefix, groupName, true)
        return writeInventoryFile(filename, content, metadata)
    }

    override fun cleanupOldFiles(olderThanDays: Int): CleanupResult {
        val cutoffDate = LocalDateTime.now().minusDays(olderThanDays.toLong())
        val deletedFiles = mutableListOf<String>()
        var deletedCount = 0L
        var errorCount = 0L

        Files.list(inventoryPath).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) }
                .forEach { path ->
                    try {
                        val lastModified = Files.getLastModifiedTime(path).toInstant()
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()

                        if (lastModified.isBefore(cutoffDate)) {
                            val filename = path.fileName.toString()
                            Files.delete(path)

                            // Also delete metadata file if exists
                            val metadataPath = getMetadataPath(path)
                            if (Files.exists(metadataPath)) {
                                Files.delete(metadataPath)
                            }

                            deletedFiles.add(filename)
                            deletedCount++
                        }
                    } catch (e: Exception) {
                        errorCount++
                    }
                }
        }

        return CleanupResult(
            deletedCount = deletedCount,
            errorCount = errorCount,
            deletedFiles = deletedFiles
        )
    }

    // Private helper methods

    private fun validateFilename(filename: String) {
        if (filename.isBlank()) {
            throw IllegalArgumentException("Filename cannot be blank")
        }

        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw IllegalArgumentException("Filename contains invalid characters")
        }

        if (filename.length > 255) {
            throw IllegalArgumentException("Filename is too long")
        }
    }

    private fun sanitizeFilename(filename: String): String {
        return filename
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_{2,}"), "_")
            .take(255)
    }

    private fun writeMetadataFile(filePath: Path, metadata: InventoryFileMetadata) {
        val metadataPath = getMetadataPath(filePath)
        val metadataJson = """
            {
                "description": "${metadata.description ?: ""}",
                "groupName": "${metadata.groupName ?: ""}",
                "hostCount": ${metadata.hostCount},
                "generatedAt": "${metadata.generatedAt}",
                "tags": ${metadata.tags.joinToString(", ") { "\"$it\"" }.let { "[$it]" }}
            }
        """.trimIndent()

        Files.writeString(metadataPath, metadataJson)
    }

    private fun readMetadataFile(filePath: Path): InventoryFileMetadata? {
        val metadataPath = getMetadataPath(filePath)
        if (!Files.exists(metadataPath)) {
            return null
        }

        val content = Files.readString(metadataPath)
        // Simple JSON parsing - in production, use proper JSON library
        return parseMetadataJson(content)
    }

    private fun parseMetadataJson(json: String): InventoryFileMetadata {
        // Simple JSON parsing - in production, use Jackson or similar
        return InventoryFileMetadata(
            description = extractJsonString(json, "description"),
            groupName = extractJsonString(json, "groupName"),
            hostCount = extractJsonNumber(json, "hostCount")?.toInt() ?: 0,
            generatedAt = extractJsonString(json, "generatedAt")?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now(),
            tags = extractJsonArray(json, "tags")
        )
    }

    private fun extractJsonString(json: String, key: String): String? {
        val regex = "\"$key\":\\s*\"([^\"]*)\""
        return Regex(regex).find(json)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    private fun extractJsonNumber(json: String, key: String): String? {
        val regex = "\"$key\":\\s*([0-9]+)"
        return Regex(regex).find(json)?.groupValues?.get(1)
    }

    private fun extractJsonArray(json: String, key: String): List<String> {
        val regex = "\"$key\":\\s*\\[([^\\]]*)]"
        val arrayContent = Regex(regex).find(json)?.groupValues?.get(1) ?: return emptyList()
        return arrayContent.split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    }

    private fun getMetadataPath(filePath: Path): Path {
        return filePath.resolveSibling("${filePath.fileName}.metadata.json")
    }
}