package com.module.wgcontrolplane.dto

import com.module.wgcontrolplane.model.GOOGLE_DNS
import com.module.wgcontrolplane.model.WireGuardClient
import com.module.wgcontrolplane.model.WireGuardServer
import jakarta.validation.constraints.*
import java.time.LocalDateTime

/**
 * Request DTO for creating a WireGuard server
 */
data class CreateServerRequest(
    @field:NotBlank(message = "Server name cannot be blank")
    @field:Size(min = 1, max = 100, message = "Server name must be between 1 and 100 characters")
    val name: String,

    @field:NotBlank(message = "Network address cannot be blank")
    @field:Pattern(
        regexp = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/(3[0-2]|[12]?[0-9])$",
        message = "Network address must be in CIDR format (e.g., 10.0.0.1/24)"
    )
    val networkAddress: String,

    @field:Min(1024, message = "Listen port must be at least 1024")
    @field:Max(65535, message = "Listen port must be at most 65535")
    val listenPort: Int = 51820,

    @field:NotBlank(message = "Endpoint cannot be blank")
    val endpoint: String,

    val dnsServers: List<String> = listOf(GOOGLE_DNS)
)

/**
 * Request DTO for creating a client
 */
data class CreateClientRequest(
    @field:NotBlank(message = "Client name cannot be blank")
    @field:Size(min = 1, max = 100, message = "Client name must be between 1 and 100 characters")
    val name: String,

    val publicKey: String? = null, // If null, keys will be auto-generated
    val presharedKey: String? = null
)

/**
 * Request DTO for adding existing client to server
 */
data class AddClientRequest(
    @field:NotBlank(message = "Client name cannot be blank")
    val clientName: String,

    @field:NotBlank(message = "Public key cannot be blank")
    val clientPublicKey: String,

    val presharedKey: String? = null
)

/**
 * Request DTO for updating client status
 */
data class UpdateClientStatusRequest(
    val enabled: Boolean
)

/**
 * Request DTO for updating client statistics
 */
data class UpdateClientStatsRequest(
    val lastHandshake: LocalDateTime,
    val dataReceived: Long,
    val dataSent: Long
)

/**
 * Response DTO for server information
 */
data class ServerResponse(
    val id: String,
    val name: String,
    val publicKey: String,
    val networkAddress: String,
    val listenPort: Int,
    val endpoint: String,
    val dnsServers: List<String>,
    val enabled: Boolean,
    val totalClients: Int,
    val activeClients: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(server: WireGuardServer): ServerResponse {
            val activeClients = server.clients.count { it.enabled }
            return ServerResponse(
                id = server.id.toString(),
                name = server.name,
                publicKey = server.publicKey,
                networkAddress = server.primaryAddress?.address ?: "",
                listenPort = server.listenPort,
                endpoint = server.endpoint,
                dnsServers = server.dnsServers.toList(),
                enabled = server.enabled,
                totalClients = server.clients.size,
                activeClients = activeClients,
                createdAt = server.createdAt,
                updatedAt = server.updatedAt
            )
        }
    }
}

/**
 * Response DTO for detailed server information with clients
 */
data class ServerDetailResponse(
    val id: String,
    val name: String,
    val publicKey: String,
    val networkAddress: String,
    val listenPort: Int,
    val endpoint: String,
    val dnsServers: List<String>,
    val enabled: Boolean,
    val clients: List<ClientResponse>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(server: WireGuardServer): ServerDetailResponse {
            return ServerDetailResponse(
                id = server.id.toString(),
                name = server.name,
                publicKey = server.publicKey,
                networkAddress = server.primaryAddress?.address ?: "",
                listenPort = server.listenPort,
                endpoint = server.endpoint,
                dnsServers = server.dnsServers.toList(),
                enabled = server.enabled,
                clients = server.clients.map { ClientResponse.from(it) },
                createdAt = server.createdAt,
                updatedAt = server.updatedAt
            )
        }
    }
}

/**
 * Response DTO for client information
 */
data class ClientResponse(
    val id: String,
    val name: String,
    val publicKey: String,
    val allowedIPs: List<String>,
    val persistentKeepalive: Int,
    val enabled: Boolean,
    val isOnline: Boolean,
    val lastHandshake: LocalDateTime?,
    val dataReceived: Long,
    val dataSent: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(client: WireGuardClient): ClientResponse {
            return ClientResponse(
                id = client.id.toString(),
                name = client.name,
                publicKey = client.publicKey,
                allowedIPs = client.allowedIPs.map { it.address },
                persistentKeepalive = client.persistentKeepalive,
                enabled = client.enabled,
                isOnline = client.isOnline,
                lastHandshake = client.lastHandshake,
                dataReceived = client.dataReceived,
                dataSent = client.dataSent,
                createdAt = client.createdAt,
                updatedAt = client.updatedAt
            )
        }
    }
}

/**
 * Response DTO for client creation with private key
 */
data class ClientCreationResponse(
    val client: ClientResponse,
    val privateKey: String
)

/**
 * Response DTO for server statistics
 */
data class ServerStatsResponse(
    val serverId: String,
    val serverName: String,
    val endpoint: String,
    val listenPort: Int,
    val totalClients: Int,
    val onlineClients: Int,
    val offlineClients: Int,
    val totalDataReceived: Long,
    val totalDataSent: Long,
    val networkAddress: String
)