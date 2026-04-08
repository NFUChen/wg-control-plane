package com.app.model

import com.app.converter.IPAddressListConverter
import com.app.model.ClientDeploymentStatus.*
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.NotFound
import org.hibernate.annotations.NotFoundAction
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import java.util.*

const val GOOGLE_DNS = "8.8.8.8"

/**
 * WireGuard Server entity - represents a WireGuard server instance
 */
@Entity
@Table(name = "wg_servers")
class WireGuardServer(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, unique = true)
    var name: String,

    @Column(name = "interface_name", nullable = false, unique = true)
    var interfaceName: String = "wg0", // Default interface name, can be customized

    @Column(name = "private_key", nullable = false, length = 44)
    @JsonIgnore
    val privateKey: String,

    @Column(name = "public_key", nullable = false, length = 44)
    val publicKey: String,

    @Convert(converter = IPAddressListConverter::class)
    @Column(name = "addresses", columnDefinition = "TEXT")
    var addresses: MutableList<IPAddress> = mutableListOf(), // e.g., ["10.0.0.1/24"]

    @Column(name = "listen_port", nullable = false)
    var listenPort: Int = 51820,

    @Convert(converter = IPAddressListConverter::class)
    @Column(name = "dns_servers", columnDefinition = "TEXT")
    val dnsServers: MutableList<IPAddress> = mutableListOf(IPAddress(GOOGLE_DNS)),

    @Column(name = "mtu")
    val mtu: Int? = null,

    @Column(name = "post_up")
    var postUp: String? = null, // iptables rules for NAT, etc.

    @Column(name = "post_down")
    var postDown: String? = null,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    /**
     * Remote deployment target on the control plane's Ansible inventory.
     * Null means this server is managed locally on the control plane host.
     * Many WG servers may reference the same host (e.g. wg0 / wg1 on one machine); EAGER avoids
     * lazy issues when building API responses outside a repository call stack.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, optional = true)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "host_id", nullable = true)
    var ansibleHost: AnsibleHost? = null,

    @OneToMany(mappedBy = "server", cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val clients: MutableList<WireGuardClient> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "agent_token", unique = true, columnDefinition = "TEXT")
    val agentToken: String? = null
) {
    /** Ansible host id for API and routing; mirrors [ansibleHost]?.id. */
    val hostId: UUID?
        get() = ansibleHost?.id

    /**
     * Get the primary server address (first address)
     */
    val primaryAddress: IPAddress
        get() = addresses.first()

    /**
     * Add a client to this server
     */
    fun addClient(client: WireGuardClient) {
        client.server = this
        clients.add(client)
    }

    /**
     * Remove a client from this server
     */
    fun removeClient(clientId: UUID) {
        clients.removeIf { it.id == clientId }
    }
}

/**
 * WireGuard Client entity - represents a client connected to a server
 */
@Entity
@Table(
    name = "wg_clients",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_wg_clients_host_interface",
            columnNames = ["host_id", "interface_name"],
        ),
    ],
)
class WireGuardClient(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false)
    var name: String,

    /** Local interface name when the client config is deployed (e.g. Ansible); must be wg0–wg99, independent of [name]. */
    @Column(name = "interface_name", nullable = false)
    var interfaceName: String = "wg0",

    @Column(name = "private_key", nullable = false, length = 44)
    @JsonIgnore
    val privateKey: String,

    @Column(name = "public_key", nullable = false, length = 44)
    val publicKey: String,

    @Column(name = "preshared_key", length = 44)
    var presharedKey: String? = null,

    @Convert(converter = IPAddressListConverter::class)
    @Column(name = "peer_ips", nullable = false)
    val peerIPs: MutableList<IPAddress> = mutableListOf(),

    @Convert(converter = IPAddressListConverter::class)
    @Column(name = "allowed_ips", columnDefinition = "TEXT")
    var allowedIPs: MutableList<IPAddress> = mutableListOf(),


    @Column(name = "persistent_keepalive")
    var persistentKeepalive: Int = 25,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    /**
     * Remote deployment target on the control plane's Ansible inventory.
     * Null means this server is managed locally on the control plane host.
     * Many WG servers may reference the same host (e.g. wg0 / wg1 on one machine); EAGER avoids
     * lazy issues when building API responses outside a repository call stack.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, optional = true)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "host_id", nullable = true)
    var ansibleHost: AnsibleHost? = null,

    @Column(name = "last_handshake")
    var lastHandshake: LocalDateTime? = null,

    @Column(name = "data_received")
    var dataReceived: Long = 0,

    @Column(name = "data_sent")
    var dataSent: Long = 0,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "server_id", nullable = false)
    @JsonIgnore
    var server: WireGuardServer,

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_status", nullable = false)
    var deploymentStatus: ClientDeploymentStatus = ClientDeploymentStatus.NONE,

    /**
     * Deployment mode - how this client configuration is deployed/managed:
     * - LOCAL: Direct wg commands on control plane host
     * - ANSIBLE: Pushed to remote host via Ansible
     * - AGENT: Client pulls configuration using agentToken
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_mode", nullable = false)
    var deploymentMode: ClientDeploymentMode = ClientDeploymentMode.LOCAL,

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),


    @Column(name = "agent_token", unique = true, columnDefinition = "TEXT")
    val agentToken: String? = null
) {

    /** Ansible host id for API and routing; mirrors [ansibleHost]?.id. */
    val hostId: UUID?
        get() = ansibleHost?.id

    val plainTextAllowedIPs: String
        get() = allowedIPs.joinToString(",") { it.address }

    /**
     * Check if routing all traffic is allowed
     */
    val isAllTrafficAllowed: Boolean
        get() = allowedIPs.any { it.address == "0.0.0.0/0" || it.address == "::/0" }

    val primaryPeerIP = peerIPs.firstOrNull()?.address ?: ""
    /**
     * Check if client is currently online (based on recent handshake)
     */
    val isOnline: Boolean
        get() = lastHandshake?.isAfter(LocalDateTime.now().minusMinutes(3)) == true

    val needsRetryDeploy: Boolean
        get() = deploymentStatus in listOf(ClientDeploymentStatus.DEPLOY_FAILED, ClientDeploymentStatus.PENDING_REMOVAL)
}

/**
 * Tracks the remote deployment state of a client's WireGuard config on its Ansible host.
 *
 * - [NONE]              — no remote host assigned (`hostId` is null); config-file-only client.
 * - [DEPLOYED]          — config successfully deployed (or cleaned up) on the remote host.
 * - [DEPLOY_FAILED]     — last deploy/update attempt failed (e.g. host was offline). Retryable.
 * - [PENDING_REMOVAL]   — client removed from the server peer list, but cleanup on the remote
 *                          host failed. The DB record is kept so the operator can retry cleanup.
 */
enum class ClientDeploymentStatus {
    NONE,
    DEPLOYED,
    DEPLOY_FAILED,
    PENDING_REMOVAL
}

/**
 * Client deployment mode - how the client configuration is deployed/managed
 */
enum class ClientDeploymentMode {
    /** Local deployment on control plane host using wg commands */
    LOCAL,

    /** Remote deployment via Ansible to a managed host */
    ANSIBLE,

    /** Agent mode - client pulls configuration using agent token */
    AGENT
}

/** Same allowed form as [WireGuardServer.interfaceName]: `wg0` … `wg99`. */
fun String.isValidWireGuardInterfaceName(): Boolean {
    if (!matches(Regex("^wg[0-9]{1,2}$"))) return false
    val n = removePrefix("wg").toIntOrNull() ?: return false
    return n in 0..99
}
