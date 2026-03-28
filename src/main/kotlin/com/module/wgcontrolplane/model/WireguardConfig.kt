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
 * WireGuard configuration main entity
 */
@Entity
@Table(name = "wireguard_configs")
class WireGuardConfig(
    @Id
    @GeneratedValue
    val id: UUID = UUID.randomUUID(),

    @Column(name = "endpoint")
    val endpoint: String? = null, // Server address, null indicates this is a regular peer, not a wg server

    @Column(name = "name", nullable = false)
    val name: String = "Default Config",

    @OneToOne(mappedBy = "config", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var interfaceConfig: WgInterface? = null,

    @OneToMany(mappedBy = "config", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    val peers: MutableList<WgPeer> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {

    fun addPeer(peer: WgPeer) {
        interfaceConfig?.let { interfaceConfig ->
            peer.allowedIPs.forEach { allowedIP ->
                if (!interfaceConfig.addresses.any { interfaceAddress ->
                    interfaceAddress.contains(allowedIP)
                }) {
                    throw IllegalArgumentException("Peer allowed IP ${allowedIP.address} is not within the interface's address range")
                }
            }
        }
        peer.config = this
        peers.add(peer)
    }

    fun removePeer(peerId: UUID) {
        peers.removeIf { it.id == peerId }
    }

    fun setInterface(wgInterface: WgInterface) {
        this.interfaceConfig = wgInterface
        wgInterface.config = this
    }
}

/**
 * WireGuard interface configuration entity
 */
@Entity
@Table(name = "wg_interfaces")
class WgInterface(
    @Id
    @GeneratedValue
    val id: UUID = UUID.randomUUID(),

    @Column(name = "private_key", nullable = false, length = 44)
    val privateKey: String = "",

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "wg_interface_addresses", joinColumns = [JoinColumn(name = "interface_id")])
    @AttributeOverride(name = "address", column = Column(name = "ip_address"))
    val addresses: MutableList<IPAddress> = mutableListOf(), // e.g., ["10.0.0.1/24", "fd86:ea04:1115::1/64"]

    @Column(name = "listen_port", nullable = false)
    val listenPort: Int = 51820,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "wg_interface_dns", joinColumns = [JoinColumn(name = "interface_id")])
    @Column(name = "dns_server")
    val dnsServer: MutableList<String> = mutableListOf(GOOGLE_DNS),

    @Column(name = "mtu")
    val mtu: Int? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    var config: WireGuardConfig? = null,

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
}

/**
 * WireGuard Peer entity
 */
@Entity
@Table(name = "wg_peers")
class WgPeer(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "public_key", nullable = false, length = 44)
    val publicKey: String = "",

    @Column(name = "preshared_key", length = 44)
    val presharedKey: String? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "wg_peer_allowed_ips", joinColumns = [JoinColumn(name = "peer_id")])
    @AttributeOverride(name = "address", column = Column(name = "ip_address"))
    val allowedIPs: MutableList<IPAddress> = mutableListOf(), // e.g., ["10.0.0.2/32"]

    @Column(name = "endpoint", nullable = false)
    val endpoint: String = "", // e.g., "vpn.example.com:51820"

    @Column(name = "persistent_keepalive")
    val persistentKeepalive: Int = 25,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    var config: WireGuardConfig? = null,

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
}