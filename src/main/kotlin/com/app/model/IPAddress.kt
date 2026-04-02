package com.app.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.net.InetAddress
import java.net.UnknownHostException


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
