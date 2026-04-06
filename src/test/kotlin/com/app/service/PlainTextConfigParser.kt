package com.app.service

import com.app.model.IPAddress
import com.app.model.WireGuardClient
import com.app.model.WireGuardServer
import java.util.*

/**
 * Sealed class representing different types of parsed configurations
 */
sealed class ParsedConfig {
    /**
     * Server configuration with Interface and multiple Peers
     */
    data class ServerConfig(
        val interfaceSection: Map<String, String>,
        val peers: List<Map<String, String>>
    ) : ParsedConfig() {

        /**
         * Convert parsed server config to WireGuardServer entity
         */
        fun toWireGuardServer(
            name: String = "Parsed Server",
            interfaceName: String = "wg0",
            agentToken: String = UUID.randomUUID().toString()
        ): WireGuardServer {
            return WireGuardServer(
                name = name,
                interfaceName = interfaceName,
                privateKey = interfaceSection["PrivateKey"]
                    ?: throw IllegalArgumentException("PrivateKey is required in Interface section"),
                publicKey = derivePublicKeyPlaceholder(),
                addresses = parseIPAddressList(interfaceSection["Address"] ?: ""),
                listenPort = interfaceSection["ListenPort"]?.toIntOrNull() ?: 51820,
                agentToken = agentToken,
                postUp = interfaceSection["PostUp"],
                postDown = interfaceSection["PostDown"],
                mtu = interfaceSection["MTU"]?.toIntOrNull()
            )
        }

        /**
         * Convert peers to WireGuardClient entities
         * The server parameter should be the result of toWireGuardServer()
         */
        fun toWireGuardClients(
            server: WireGuardServer,
            namePrefix: String = "Parsed Client"
        ): List<WireGuardClient> {
            return peers.mapIndexed { index, peerMap ->
                val allowedIPsList = parseIPAddressList(peerMap["AllowedIPs"] ?: "")
                if (allowedIPsList.isEmpty()) {
                    throw IllegalArgumentException("At least one IP required in AllowedIPs for peer ${index + 1}")
                }

                WireGuardClient(
                    name = "$namePrefix ${index + 1}",
                    privateKey = "placeholder-private-key", // Not available in server config
                    publicKey = peerMap["PublicKey"]
                        ?: throw IllegalArgumentException("PublicKey is required in Peer section"),
                    peerIP = allowedIPsList.toMutableList(),
                    allowedIPs = allowedIPsList.toMutableList(),
                    persistentKeepalive = peerMap["PersistentKeepalive"]?.toIntOrNull() ?: 25,
                    server = server,
                    agentToken = UUID.randomUUID().toString(),
                    presharedKey = peerMap["PresharedKey"]
                )
            }
        }

        /**
         * Convert to complete server with clients in one call
         */
        fun toCompleteWireGuardSetup(
            serverName: String = "Parsed Server",
            clientNamePrefix: String = "Parsed Client",
            serverAgentToken: String = UUID.randomUUID().toString()
        ): WireGuardServer {
            val server = toWireGuardServer(
                name = serverName,
                agentToken = serverAgentToken
            )

            // Add clients to server
            val clients = toWireGuardClients(server, clientNamePrefix)
            server.clients.addAll(clients)

            return server
        }

        private fun derivePublicKeyPlaceholder(): String {
            // In real implementation, you would derive this from private key
            return "placeholder-public-key-derived-from-private"
        }
    }

    /**
     * Client configuration with Interface and single Peer
     */
    data class ClientConfig(
        val interfaceSection: Map<String, String>,
        val peer: Map<String, String>
    ) : ParsedConfig() {

        /**
         * Convert parsed client config to WireGuardClient entity
         * Requires server reference for the entity relationship
         */
        fun toWireGuardClient(
            server: WireGuardServer,
            name: String = "Parsed Client",
            interfaceName: String = "wg0",
            agentToken: String = UUID.randomUUID().toString()
        ): WireGuardClient {
            // Parse peer IP from Interface.Address (should be single IP)
            val addressString = interfaceSection["Address"] ?:
                throw IllegalArgumentException("Address is required in Interface section")
            val peerIP = parseIPAddressList(addressString).firstOrNull()
                ?: throw IllegalArgumentException("Valid peer IP address is required in Interface.Address")

            return WireGuardClient(
                name = name,
                interfaceName = interfaceName,
                privateKey = interfaceSection["PrivateKey"]
                    ?: throw IllegalArgumentException("PrivateKey is required in Interface section"),
                publicKey = peer["PublicKey"]
                    ?: throw IllegalArgumentException("PublicKey is required in Peer section"),
                peerIP = mutableListOf(peerIP),
                allowedIPs = parseIPAddressList(peer["AllowedIPs"] ?: "").toMutableList(),
                persistentKeepalive = interfaceSection["PersistentKeepalive"]?.toIntOrNull() ?: 25,
                server = server,
                agentToken = agentToken,
                presharedKey = peer["PresharedKey"]
            )
        }

        /**
         * Create a minimal server reference for the client
         * This creates a "stub" server that can be used when you only have client config
         */
        fun createStubServerFromPeer(
            serverName: String = "Parsed Server",
            serverAgentToken: String = UUID.randomUUID().toString()
        ): WireGuardServer {
            val serverPublicKey = peer["PublicKey"]
                ?: throw IllegalArgumentException("PublicKey is required in Peer section")

            return WireGuardServer(
                name = serverName,
                privateKey = "placeholder-private-key", // Placeholder - not known from client config
                publicKey = serverPublicKey,
                addresses = mutableListOf(), // Not available from client config
                listenPort = extractPortFromEndpoint(),
                agentToken = serverAgentToken
            )
        }

        private fun extractPortFromEndpoint(): Int {
            val endpoint = peer["Endpoint"] ?: return 51820 // Default port
            return endpoint.substringAfterLast(":").toIntOrNull() ?: 51820
        }
    }
}

/**
 * Generic Plain Text Configuration Parser
 * Can parse different types of plain text configurations and return appropriate typed results
 */
class PlainTextConfigParser {

    /**
     * Parse configuration and automatically determine type based on content
     */
    fun parseConfig(configText: String): ParsedConfig {
        val sections = parseIntoSections(configText)
        val interfaceSection = sections["Interface"]
            ?: throw IllegalArgumentException("Interface section is required")

        val peerSections = sections.filterKeys { it.startsWith("Peer") }

        return when {
            peerSections.isEmpty() -> throw IllegalArgumentException("At least one Peer section is required")
            peerSections.size == 1 -> {
                // Single peer = Client configuration
                ParsedConfig.ClientConfig(
                    interfaceSection = interfaceSection,
                    peer = peerSections.values.first()
                )
            }
            else -> {
                // Multiple peers = Server configuration
                ParsedConfig.ServerConfig(
                    interfaceSection = interfaceSection,
                    peers = peerSections.values.toList()
                )
            }
        }
    }

    /**
     * Parse configuration with explicit type expectation
     */
    inline fun <reified T : ParsedConfig> parseConfigAs(configText: String): T {
        val parsed = parseConfig(configText)
        return parsed as? T
            ?: throw IllegalArgumentException("Expected ${T::class.simpleName} but got ${parsed::class.simpleName}")
    }

    /**
     * Parse only into generic sections without type inference
     */
    fun parseIntoSections(configText: String): Map<String, Map<String, String>> {
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        var currentSection: String? = null
        var peerCount = 0

        configText.lines().forEach { line ->
            val trimmedLine = line.trim()

            when {
                trimmedLine.isEmpty() || trimmedLine.startsWith("#") -> {
                    // Skip empty lines and comments
                }
                trimmedLine.startsWith("[") && trimmedLine.endsWith("]") -> {
                    // Section header
                    val sectionName = trimmedLine.removeSurrounding("[", "]")
                    currentSection = if (sectionName == "Peer") {
                        // Handle multiple Peer sections
                        "Peer$peerCount"
                    } else {
                        sectionName
                    }
                    if (sectionName == "Peer") peerCount++
                    sections[currentSection!!] = mutableMapOf()
                }
                trimmedLine.contains("=") && currentSection != null -> {
                    // Property line
                    val parts = trimmedLine.split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        sections[currentSection]!![key] = value
                    }
                }
            }
        }

        return sections
    }

    /**
     * Helper method to parse IP address strings into IPAddress objects
     */
    private fun parseIPAddressList(addressString: String): MutableList<IPAddress> {
        if (addressString.isBlank()) return mutableListOf()

        return addressString.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { IPAddress(it) }
            .toMutableList()
    }
}

/**
 * Global helper function for parsing IP addresses
 */
private fun parseIPAddressList(addressString: String): MutableList<IPAddress> {
    if (addressString.isBlank()) return mutableListOf()

    return addressString.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { IPAddress(it) }
        .toMutableList()
}

/**
 * Extension functions for easier access to parsed configuration data
 */
fun ParsedConfig.ServerConfig.getClientCount(): Int = peers.size

fun ParsedConfig.ServerConfig.getClientByPublicKey(publicKey: String): Map<String, String>? =
    peers.find { it["PublicKey"] == publicKey }

fun ParsedConfig.ClientConfig.getServerEndpoint(): String? = peer["Endpoint"]

fun ParsedConfig.ClientConfig.getAllowedIPs(): List<String> =
    peer["AllowedIPs"]?.split(",")?.map { it.trim() } ?: emptyList()

fun ParsedConfig.getInterfaceAddress(): String? =
    when (this) {
        is ParsedConfig.ServerConfig -> interfaceSection["Address"]
        is ParsedConfig.ClientConfig -> interfaceSection["Address"]
    }

fun ParsedConfig.getPrivateKey(): String? =
    when (this) {
        is ParsedConfig.ServerConfig -> interfaceSection["PrivateKey"]
        is ParsedConfig.ClientConfig -> interfaceSection["PrivateKey"]
    }