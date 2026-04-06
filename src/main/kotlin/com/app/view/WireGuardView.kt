package com.app.view

import com.app.model.ClientDeploymentStatus
import com.app.model.GOOGLE_DNS
import com.app.model.IPAddress
import com.app.model.WireGuardClient
import com.app.model.WireGuardServer
import jakarta.validation.constraints.*
import java.time.LocalDateTime
import java.util.UUID


/**
 * Request DTO for creating a WireGuard server
 */
data class UpdateServerRequest(
    @field:NotBlank(message = "Server name cannot be blank")
    @field:Size(min = 1, max = 100, message = "Server name must be between 1 and 100 characters")
    val name: String? = null,

    @field:NotBlank(message = "Interface name cannot be blank")
    @field:Pattern(
        regexp = "^wg[0-9]{1,2}$",
        message = "Interface name must be in the format 'wg0' to 'wg99'"
    )
    val interfaceName: String? = null,

    @field:NotBlank(message = "Network address cannot be blank")
    @field:Pattern(
        regexp = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/(3[0-2]|[12]?[0-9])$",
        message = "Network address must be in CIDR format (e.g., 10.0.0.1/24)"
    )
    val networkAddress: String? = null,

    @field:Min(1024, message = "Listen port must be at least 1024")
    @field:Max(65535, message = "Listen port must be at most 65535")
    val listenPort: Int? = null,

    val dnsServers: List<String>? = null,

    @field:Size(max = 8192, message = "PostUp must be at most 8192 characters")
    val postUp: String? = null,

    @field:Size(max = 8192, message = "PostDown must be at most 8192 characters")
    val postDown: String? = null
)

/**
 * Request DTO for creating a WireGuard server
 */
data class CreateServerRequest(
    @field:NotBlank(message = "Server name cannot be blank")
    @field:Size(min = 1, max = 100, message = "Server name must be between 1 and 100 characters")
    val name: String,

    @field:NotBlank(message = "Interface name cannot be blank")
    @field:Pattern(
        regexp = "^wg[0-9]{1,2}$",
        message = "Interface name must be in the format 'wg0' to 'wg99'"
    )
    val interfaceName: String = "wg0",

    @field:NotBlank(message = "Network address cannot be blank")
    @field:Pattern(
        regexp = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/(3[0-2]|[12]?[0-9])$",
        message = "Network address must be in CIDR format (e.g., 10.0.0.1/24)"
    )
    val networkAddress: String,

    @field:Min(1024, message = "Listen port must be at least 1024")
    @field:Max(65535, message = "Listen port must be at most 65535")
    val listenPort: Int = 51820,

    val dnsServers: List<String> = listOf(GOOGLE_DNS),

    @field:Size(max = 8192, message = "PostUp must be at most 8192 characters")
    val postUp: String? = null,

    @field:Size(max = 8192, message = "PostDown must be at most 8192 characters")
    val postDown: String? = null,

    val hostId: UUID? = null // null = create server on the current host
)


/**
 * Request DTO for adding existing client to server
 */
data class AddClientRequest(
    @field:NotBlank(message = "Client name cannot be blank")
    val clientName: String,

    @field:NotBlank(message = "Interface name cannot be blank")
    @field:Pattern(
        regexp = "^wg[0-9]{1,2}$",
        message = "Interface name must be in the format 'wg0' to 'wg99'",
    )
    val interfaceName: String = "wg0",

    val clientPublicKey: String? = null,

    val presharedKey: String? = null,

    @field:NotEmpty(message = "At least one peer IP is required")
    val peerIPs: List<IPAddress>,

    val allowedIPs: List<IPAddress> = emptyList(),

    val hostId: UUID? = null // null = config file only; non-null = deploy to remote AnsibleHost
)

/**
 * Request DTO for updating an existing client. Omitted fields are left unchanged.
 * [presharedKey]: omit to leave unchanged; send empty string to clear the pre-shared key.
 */
data class UpdateClientRequest(
    val clientName: String? = null,

    val interfaceName: String? = null,

    val addresses: List<IPAddress>? = null,
    val presharedKey: String? = null,
    val persistentKeepalive: Int? = null,
    val enabled: Boolean? = null,
    val hostId: UUID? = null // null = config file only; non-null = deploy to remote AnsibleHost
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
    val interfaceName: String,
    val publicKey: String,
    val networkAddress: IPAddress,
    val listenPort: Int,
    val endpoint: String,
    val dnsServers: List<IPAddress>,
    val postUp: String?,
    val postDown: String?,
    val enabled: Boolean,
    /** WireGuard interface is up (wg process running for this server). */
    val isOnline: Boolean,
    val totalClients: Int,
    val activeClients: Int,
    /** When set, the server is deployed via Ansible to this host (not local wg on the control plane). */
    val hostId: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(server: WireGuardServer, isOnline: Boolean, endpoint: String): ServerResponse {
            val activeClients = server.clients.count { it.enabled }
            return ServerResponse(
                id = server.id.toString(),
                name = server.name,
                interfaceName = server.interfaceName,
                publicKey = server.publicKey,
                networkAddress = server.primaryAddress,
                listenPort = server.listenPort,
                endpoint = endpoint,
                dnsServers = server.dnsServers.toList(),
                postUp = server.postUp,
                postDown = server.postDown,
                enabled = server.enabled,
                isOnline = isOnline,
                totalClients = server.clients.size,
                activeClients = activeClients,
                hostId = server.hostId?.toString(),
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
    val interfaceName: String,
    val publicKey: String,
    val networkAddress: IPAddress,
    val listenPort: Int,
    val endpoint: String,
    val dnsServers: List<IPAddress>,
    val postUp: String?,
    val postDown: String?,
    val enabled: Boolean,
    val clients: List<ClientResponse>,
    /** When set, the server is deployed via Ansible to this host (not local wg on the control plane). */
    val hostId: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(server: WireGuardServer, endpoint: String): ServerDetailResponse {
            return ServerDetailResponse(
                id = server.id.toString(),
                name = server.name,
                interfaceName = server.interfaceName,
                publicKey = server.publicKey,
                networkAddress = server.primaryAddress,
                listenPort = server.listenPort,
                endpoint = endpoint,
                dnsServers = server.dnsServers.toList(),
                postUp = server.postUp,
                postDown = server.postDown,
                enabled = server.enabled,
                clients = server.clients.map { ClientResponse.from(it) },
                hostId = server.hostId?.toString(),
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
    val interfaceName: String,
    val publicKey: String,
    /** VPN/tunnel address(es) for this client (`Address` in client .conf); distinct from [allowedIPs]. */
    val peerIPs: List<String>,
    val allowedIPs: List<String>,
    val persistentKeepalive: Int,
    val enabled: Boolean,
    val isOnline: Boolean,
    val lastHandshake: LocalDateTime?,
    val dataReceived: Long,
    val dataSent: Long,
    /** When set, client config is (or was) deployed to this Ansible host. Immutable after create. */
    val hostId: String?,
    val deploymentStatus: ClientDeploymentStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(client: WireGuardClient): ClientResponse {
            return ClientResponse(
                id = client.id.toString(),
                name = client.name,
                interfaceName = client.interfaceName,
                publicKey = client.publicKey,
                peerIPs = client.peerIP.map { it.address },
                allowedIPs = client.allowedIPs.map { it.address },
                persistentKeepalive = client.persistentKeepalive,
                enabled = client.enabled,
                isOnline = client.isOnline,
                lastHandshake = client.lastHandshake,
                dataReceived = client.dataReceived,
                dataSent = client.dataSent,
                hostId = client.hostId?.toString(),
                deploymentStatus = client.deploymentStatus,
                createdAt = client.createdAt,
                updatedAt = client.updatedAt
            )
        }
    }
}

/**
 * Response DTO for server statistics
 */
data class ServerStatisticsResponse(
    val serverId: UUID,
    val serverName: String,
    val totalClients: Int,
    val activeClients: Int,
    val isOnline: Boolean,
    val totalDataReceived: Long,
    val totalDataSent: Long
)