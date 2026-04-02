package com.app.model

import com.app.converter.IPAddressListConverter
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.net.InetAddress
import java.net.UnknownHostException
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

    @OneToMany(mappedBy = "server", cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val clients: MutableList<WireGuardClient> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),

) {
    /**
     * Get the primary server address (first address)
     */
    val primaryAddress: IPAddress
        get() = addresses.first()

    /**
     * Add a client to this server
     */
    fun addClient(client: WireGuardClient) {
        // Validate that client's allowed IPs are within server's network range
        primaryAddress.let { serverAddr ->
            client.allowedIPs.forEach { clientIP ->
                if (!serverAddr.contains(clientIP)) {
                    throw IllegalArgumentException(
                        "Client allowed IP ${clientIP.address} is not within server's network range ${serverAddr.address}"
                    )
                }
            }
        }

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
@Table(name = "wg_clients")
class WireGuardClient(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "private_key", nullable = false, length = 44)
    @JsonIgnore
    val privateKey: String,

    @Column(name = "public_key", nullable = false, length = 44)
    val publicKey: String,

    @Column(name = "preshared_key", length = 44)
    var presharedKey: String? = null,

    @Convert(converter = IPAddressListConverter::class)
    @Column(name = "allowed_ips", columnDefinition = "TEXT")
    var allowedIPs: MutableList<IPAddress> = mutableListOf(), // e.g., ["10.0.0.2/32"]

    @Column(name = "persistent_keepalive")
    var persistentKeepalive: Int = 25,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

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

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {

    val plainTextAllowedIPs: String
        get() = allowedIPs.joinToString(",") { it.address }

    /**
     * Check if routing all traffic is allowed
     */
    val isAllTrafficAllowed: Boolean
        get() = allowedIPs.any { it.address == "0.0.0.0/0" || it.address == "::/0" }

    /**
     * Check if client is currently online (based on recent handshake)
     */
    val isOnline: Boolean
        get() = lastHandshake?.isAfter(LocalDateTime.now().minusMinutes(3)) == true
}

