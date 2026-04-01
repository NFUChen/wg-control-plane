package com.app.model

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
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
        get() = if (address.contains("/")) {
            address.split("/")[1].toInt()
        } else {
            // Default prefix based on IP version
            val ipAddress = InetAddress.getByName(IP)
            if (ipAddress.address.size == 4) 32 else 128
        }

    init {
        validateAddress()
    }

    private fun validateAddress() {
        val parts = address.split("/")

        // Allow both plain IP addresses and CIDR format
        when (parts.size) {
            1 -> {
                // Plain IP address (e.g., "8.8.8.8") - will default to /32 or /128
                val ip = parts[0]
                require(isValidIP(ip)) { "Invalid IP address: $ip" }
            }
            2 -> {
                // CIDR format (e.g., "8.8.8.8/24")
                val ip = parts[0]
                val prefix = parts[1].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid prefix: $address")

                require(isValidIP(ip)) { "Invalid IP address: $ip" }

                // Validate prefix range based on IP version
                val ipAddress = InetAddress.getByName(ip)
                val maxPrefix = if (ipAddress.address.size == 4) 32 else 128 // IPv4 vs IPv6
                require(prefix in 0..maxPrefix) { "Invalid prefix length $prefix for IP address: $ip" }
            }
            else -> {
                throw IllegalArgumentException("Invalid IP address format: $address. Use either 'IP' or 'IP/prefix'")
            }
        }
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

/**
 * JPA Attribute Converter for MutableList<IPAddress>
 * Stores IP addresses as JSON string in database
 */
@Converter
class IPAddressListConverter : AttributeConverter<MutableList<IPAddress>, String> {

    private val objectMapper = ObjectMapper()

    override fun convertToDatabaseColumn(attribute: MutableList<IPAddress>?): String? {
        if (attribute.isNullOrEmpty()) return null

        return try {
            // Convert list of IPAddress to list of address strings
            val addressStrings = attribute.map { it.address }
            objectMapper.writeValueAsString(addressStrings)
        } catch (e: Exception) {
            throw RuntimeException("Failed to convert IP addresses to JSON", e)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): MutableList<IPAddress> {
        if (dbData.isNullOrBlank()) return mutableListOf()

        return try {
            val addressStrings: List<String> = objectMapper.readValue(
                dbData,
                object : TypeReference<List<String>>() {}
            )
            addressStrings.map { IPAddress(it) }.toMutableList()
        } catch (e: Exception) {
            throw RuntimeException("Failed to convert JSON to IP addresses", e)
        }
    }
}

