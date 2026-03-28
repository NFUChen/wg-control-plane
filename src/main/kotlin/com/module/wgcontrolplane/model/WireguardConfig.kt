package com.module.wgcontrolplane.model

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.UUID

const val GOOGLE_DNS = "8.8.8.8"

data class IPAddress(
    val address: String  // e.g., "10.0.0.1/24"
) {
    val IP: String
    val prefixLength: Int

    init {
        val parts = address.split("/")
        require(parts.size == 2) { "Invalid CIDR format: $address" }
        IP = parts[0]
        prefixLength = parts[1].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid prefix: $address")

        if (!isValid()) throw IllegalArgumentException("Invalid IP: $address")
    }

    private fun isValid(): Boolean {
        return try {
            InetAddress.getByName(IP)
            true
        } catch (e: UnknownHostException) {
            false
        }
    }

    // 取得網段的起始 byte array（mask 後的結果）
    private fun networkBytes(): ByteArray {
        val inetAddr = InetAddress.getByName(IP)
        val addr = inetAddr.address
        val mask = prefixLength
        val result = addr.copyOf()

        for (idx in addr.indices) {
            val bitsLeft = (mask - idx * 8).coerceIn(0, 8)
            val maskByte = if (bitsLeft >= 8) 0xFF else (0xFF shl (8 - bitsLeft))
            result[idx] = (result[idx].toInt() and maskByte).toByte()
        }
        return result
    }

    // 這個網段是否涵蓋 other 網段
    fun contains(other: IPAddress): Boolean {
        val thisNet = InetAddress.getByName(IP)
        val otherNet = InetAddress.getByName(other.IP)

        // IPv4 vs IPv6 不能比
        if (thisNet.javaClass != otherNet.javaClass) return false

        // other 的 prefix 必須 >= this（更小或等於的範圍）
        if (other.prefixLength < this.prefixLength) return false

        // 用 this 的 mask 去遮 other 的 IP，看網段是否相同
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

// 整個 WireGuard 配置
data class WireGuardConfig(
    val interfaceConfig: WgInterface,
    val peers: MutableList<WgPeer> = mutableListOf()
) {

    fun addPeer(peer: WgPeer) {
        peer.allowedIPs.forEach { it ->
            if (!interfaceConfig.address.any { it.contains(it) }) {
                throw IllegalArgumentException("Peer allowed IP ${it.address} is not within the interface's address range")
            }
        }
        peers.add(peer)
    }

    fun removePeer(peerId: UUID) {
        peers.removeIf { it.id == peerId }
    }
}

// Interface 部分
data class WgInterface(
    val privateKey: String,
    val address: List<IPAddress>, // e.g., ["10.0.0.1/24", "fd86:ea04:1115::1/64"]
    val listenPort: Int = 51820,
    val dns: List<String> = mutableListOf(GOOGLE_DNS),
    val mtu: Int? = null
)

// Peer 部分
data class WgPeer(
    val id: UUID,
    val publicKey: String,
    val presharedKey: String? = null,
    val allowedIPs: List<IPAddress>, // e.g., ["10.0.0.2/32"]
    val endpoint: String, // e.g., "vpn.example.com:51820"
    val persistentKeepalive: Int = 25
)