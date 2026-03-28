package com.module.wgcontrolplane.model

import com.module.wgcontrolplane.utils.WireGuardUtils
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.util.UUID

const val GOOGLE_DNS = "8.8.8.8"

/**
 * IP address value object, as an embedded entity
 */
@Embeddable
class IPAddress(
    @Column(nullable = false)
    val address: String = ""  // e.g., "10.0.0.1/24"
) {
    val IP: String
        get() = if (address.contains("/")) address.split("/")[0] else address

    val prefixLength: Int
        get() = if (address.contains("/")) address.split("/")[1].toInt() else 32

    init {
        if (address.isNotEmpty()) {
            validateAddress()
        }
    }

    private fun validateAddress() {
        val parts = address.split("/")
        require(parts.size == 2) { "Invalid CIDR format: $address" }

        val ip = parts[0]
        val prefix = parts[1].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid prefix: $address")

        require(isValidIP(ip)) { "Invalid IP address: $ip" }
    }

    private fun isValidIP(ip: String): Boolean {
        return try {
            InetAddress.getByName(ip)
            true
        } catch (e: UnknownHostException) {
            false
        }
    }

    /**
     * Check if this network segment contains another network segment
     */
    fun contains(other: IPAddress): Boolean {
        if (address.isEmpty() || other.address.isEmpty()) return false

        val thisNet = InetAddress.getByName(IP)
        val otherNet = InetAddress.getByName(other.IP)

        // Cannot compare IPv4 vs IPv6
        if (thisNet.javaClass != otherNet.javaClass) return false

        // Other's prefix must be >= this (smaller or equal range)
        if (other.prefixLength < this.prefixLength) return false

        // Use this mask to check if the network segment is the same
        val thisAddr = thisNet.address
        val otherAddr = otherNet.address

        for (idx in thisAddr.indices) {
            val bitsLeft = (prefixLength - idx * 8).coerceIn(0, 8)
            val maskByte = if (bitsLeft >= 8) 0xFF else (0xFF shl (8 - bitsLeft))
            if ((thisAddr[idx].toInt() and maskByte) != (otherAddr[idx].toInt() and maskByte)) {
                return false
            }
        }
        return true
    }
}

/**
 * WireGuard Server entity - represents a WireGuard server instance
 */
@Entity
@Table(name = "wg_servers")
class WireGuardServer(
    @Id
    @GeneratedValue
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, unique = true)
    val name: String = "",

    @Column(name = "private_key", nullable = false, length = 44)
    val privateKey: String = "",

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "wg_server_addresses", joinColumns = [JoinColumn(name = "server_id")])
    @AttributeOverride(name = "address", column = Column(name = "ip_address"))
    val addresses: MutableList<IPAddress> = mutableListOf(), // e.g., ["10.0.0.1/24"]

    @Column(name = "listen_port", nullable = false)
    val listenPort: Int = 51820,

    @Column(name = "endpoint", nullable = false)
    val endpoint: String = "", // e.g., "vpn.example.com:51820"

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "wg_server_dns", joinColumns = [JoinColumn(name = "server_id")])
    @Column(name = "dns_server")
    val dnsServers: MutableList<String> = mutableListOf(GOOGLE_DNS),

    @Column(name = "mtu")
    val mtu: Int? = null,

    @Column(name = "post_up")
    val postUp: String? = null, // iptables rules for NAT, etc.

    @Column(name = "post_down")
    val postDown: String? = null,

    @Column(name = "enabled", nullable = false)
    val enabled: Boolean = true,

    @OneToMany(mappedBy = "server", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    val clients: MutableList<WireGuardClient> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * Get corresponding public key
     */
    val publicKey: String
        get() = if (privateKey.isNotEmpty()) WireGuardUtils.generatePublicKey(privateKey) else ""

    /**
     * Get the primary server address (first address)
     */
    val primaryAddress: IPAddress?
        get() = addresses.firstOrNull()

    /**
     * Add a client to this server
     */
    fun addClient(client: WireGuardClient) {
        // Validate that client's allowed IPs are within server's network range
        primaryAddress?.let { serverAddr ->
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

    /**
     * Get next available IP address for a new client
     */
    fun getNextAvailableClientIP(): IPAddress? {
        val serverAddr = primaryAddress ?: return null
        val usedIPs = clients.mapNotNull { it.primaryAllowedIP?.IP }.toSet()

        // Simple IP allocation - increment from server IP
        val serverIP = serverAddr.IP
        val parts = serverIP.split(".")
        if (parts.size != 4) return null

        val base = "${parts[0]}.${parts[1]}.${parts[2]}"
        for (i in 2..254) { // Start from .2, avoid .1 (server) and .255 (broadcast)
            val candidateIP = "$base.$i"
            if (!usedIPs.contains(candidateIP)) {
                return IPAddress("$candidateIP/32")
            }
        }
        return null
    }
}

/**
 * WireGuard Client entity - represents a client connected to a server
 */
@Entity
@Table(name = "wg_clients")
class WireGuardClient(
    @Id
    @GeneratedValue
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false)
    val name: String = "",

    @Column(name = "public_key", nullable = false, length = 44)
    val publicKey: String = "",

    @Column(name = "preshared_key", length = 44)
    val presharedKey: String? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "wg_client_allowed_ips", joinColumns = [JoinColumn(name = "client_id")])
    @AttributeOverride(name = "address", column = Column(name = "ip_address"))
    val allowedIPs: MutableList<IPAddress> = mutableListOf(), // e.g., ["10.0.0.2/32"]

    @Column(name = "persistent_keepalive")
    val persistentKeepalive: Int = 25,

    @Column(name = "enabled", nullable = false)
    val enabled: Boolean = true,

    @Column(name = "last_handshake")
    var lastHandshake: LocalDateTime? = null,

    @Column(name = "data_received")
    var dataReceived: Long = 0,

    @Column(name = "data_sent")
    var dataSent: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    var server: WireGuardServer? = null,

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * Get the first allowed IP address (usually the primary IP)
     */
    val primaryAllowedIP: IPAddress?
        get() = allowedIPs.firstOrNull()

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

