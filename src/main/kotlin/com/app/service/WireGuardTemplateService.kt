package com.app.service

import com.app.model.*
import com.app.common.template.TemplateService
import org.springframework.stereotype.Service

@Service
class WireGuardTemplateService(
    private val templateService: TemplateService,
    private val globalConfigurationService: GlobalConfigurationService,
    private val wireGuardServerEndpointResolver: WireGuardServerEndpointResolver
) {

    /**
     * Generate server configuration with all its clients
     */
    fun generateServerConfig(server: WireGuardServer): String {
        val dataModel: Map<String, Any> = mapOf(
            "privateKey" to server.privateKey,
            "address" to server.addresses.joinToString(", ") { it.address },
            "listenPort" to server.listenPort,
            "dnsServers" to server.dnsServers.joinToString(", ") { it.address },
            "postUp" to (server.postUp ?: ""),
            "postDown" to (server.postDown ?: ""),
            "clients" to server.clients.filter { it.enabled }.map { client ->
                mapOf(
                    "name" to client.name,
                    "publicKey" to client.publicKey,
                    "peerIP" to client.peerIPs.joinToString(", ") { it.address },
                    "allowedIPs" to client.plainTextAllowedIPs,
                    "persistentKeepalive" to client.persistentKeepalive,
                    "presharedKey" to client.presharedKey,
                    "enabled" to client.enabled
                )
            }
        )

        return templateService.processTemplate("wg/server-config.ftl", dataModel)
    }

    /**
     * Generate client configuration for connecting to a server
     */
    fun generateClientConfig(
        client: WireGuardClient,
        server: WireGuardServer,
    ): String {
        val globalConfig = globalConfigurationService.getCurrentConfig()
        val serverEndpoint = wireGuardServerEndpointResolver.resolve(server, globalConfig)

        val tunnelAllowedIPs = mutableSetOf<String>()

        // Server network addresses
        client.server.addresses.forEach {
            tunnelAllowedIPs.add(it.address)
        }

        // This client's allowed IPs
        client.allowedIPs.forEach {
            tunnelAllowedIPs.add(it.address)
        }

        // Other peers' allowed IPs for mesh networking
        client.otherPeerAllowIPs.forEach {
            tunnelAllowedIPs.add(it.address)
        }


        val networkTopology = generateNetworkTopologyComments(client, server)

        val dataModel = mutableMapOf<String, Any>(
            "privateKey" to client.privateKey,
            "peerIP" to client.peerIPs.joinToString(", ") { it.address },
            "dnsServers" to server.dnsServers.joinToString(", ") { it.address },
            "serverPublicKey" to server.publicKey,
            "serverEndpoint" to serverEndpoint,
            "allowedIPs" to tunnelAllowedIPs.joinToString(", "),
            "persistentKeepalive" to client.persistentKeepalive,
            "networkTopology" to networkTopology,
            "otherClientsCount" to server.clients.count { it.enabled && it.id != client.id }
        )


        return templateService.processTemplate("wg/client-config.ftl", dataModel)
    }

    /**
     * Validate generated configuration format
     */
    fun validateConfigFormat(configContent: String): List<String> {
        val errors = mutableListOf<String>()
        val lines = configContent.lines()

        var hasInterface = false

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed == "[Interface]" -> hasInterface = true
                trimmed.startsWith("PrivateKey =") && trimmed.length < 20 -> {
                    errors.add("Private key appears to be invalid or placeholder")
                }
                trimmed.startsWith("PublicKey =") && trimmed.length < 20 -> {
                    errors.add("Public key appears to be invalid or placeholder")
                }
            }
        }

        if (!hasInterface) {
            errors.add("Configuration missing [Interface] section")
        }

        return errors
    }

    /**
     * Generate configuration hash (for configuration comparison and caching)
     */
    fun generateConfigHash(configContent: String): String = configContent.hashCode().toString()

    /**
     * Generate network topology comments for client configuration
     */
    private fun generateNetworkTopologyComments(client: WireGuardClient, server: WireGuardServer): List<String> {
        val networkTopology = mutableListOf<String>()
        val otherClients = server.clients.filter { it.enabled && it.id != client.id }

        // Server networks
        if (server.addresses.isNotEmpty()) {
            networkTopology.add("Server networks: ${server.addresses.joinToString(", ") { it.address }}")
        }

        // This client's networks
        if (client.allowedIPs.isNotEmpty()) {
            networkTopology.add("This client networks: ${client.allowedIPs.joinToString(", ") { it.address }}")
        }

        // Other clients' networks (mesh)
        otherClients.forEach { otherClient ->
            if (otherClient.allowedIPs.isNotEmpty()) {
                networkTopology.add("${otherClient.name} networks: ${otherClient.allowedIPs.joinToString(", ") { it.address }}")
            }
        }

        return networkTopology
    }
}


