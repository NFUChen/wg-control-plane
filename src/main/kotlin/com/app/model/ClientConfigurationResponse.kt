package com.app.model

import java.time.LocalDateTime
import java.util.*

/**
 * Response model for client configuration information
 */
data class ClientConfigurationResponse(
    val id: UUID,
    val name: String,
    val interfaceName: String,
    val publicKey: String,
    /** VPN/tunnel address(es) for this client; distinct from [allowedIPs]. */
    val peerIPs: List<String>,
    val allowedIPs: List<String>,
    val enabled: Boolean,
    val isOnline: Boolean,
    val lastHandshake: LocalDateTime?,
    val persistentKeepalive: Int,
    /** When set, client config is deployed to this Ansible host (immutable after create). */
    val hostId: String?,
    val server: ServerInfo
)

/**
 * Server information included in client responses
 */
data class ServerInfo(
    val id: UUID,
    val name: String,
    val endpoint: String,
    val publicKey: String,
    val dnsServers: List<String>,
    val mtu: Int?
)

/**
 * Configuration preview with content and metadata
 */
data class ConfigurationPreview(
    val fileName: String,
    val content: String,
    val metadata: ConfigurationMetadata
)

/**
 * Configuration metadata for preview and validation
 */
data class ConfigurationMetadata(
    val clientId: UUID,
    val serverName: String,
    val createdAt: LocalDateTime,
    val allowAllTraffic: Boolean,
    val configHash: String,
    val validationErrors: List<String> = emptyList()
)

/**
 * Request parameters for configuration generation
 */
data class ConfigurationRequest(
    val allowAllTraffic: Boolean = false,
    val includePresharedKey: Boolean = true
)