package com.app.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * Server configuration preview with content and metadata.
 */
data class ServerConfigurationPreview(
    val fileName: String,
    val content: String,
    val metadata: ServerConfigurationMetadata
)

/**
 * Metadata for server configuration preview and validation.
 */
data class ServerConfigurationMetadata(
    val serverId: UUID,
    val serverName: String,
    val createdAt: LocalDateTime,
    val configHash: String,
    val validationErrors: List<String> = emptyList()
)
