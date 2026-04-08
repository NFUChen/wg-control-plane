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
        client.server.addresses.forEach {
            tunnelAllowedIPs.add(it.address)
        }

        client.allowedIPs.forEach {
            tunnelAllowedIPs.add(it.address)
        }


        val dataModel = mutableMapOf<String, Any>(
            "privateKey" to client.privateKey,
            "peerIP" to client.peerIPs.joinToString(", ") { it.address },
            "dnsServers" to server.dnsServers.joinToString(", ") { it.address },
            "serverPublicKey" to server.publicKey,
            "serverEndpoint" to serverEndpoint,
            "allowedIPs" to tunnelAllowedIPs.joinToString(", "),
            "persistentKeepalive" to client.persistentKeepalive
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
}

/**
 * Extension function: Convert WireGuardClient to template data
 */
private fun WireGuardClient.toTemplateMap(): Map<String, Any> = mapOf(
    "name" to name,
    "publicKey" to publicKey,
    "peerIP" to peerIPs.joinToString(", ") { it.address },
    "allowedIPs" to allowedIPs.joinToString(", ") { it.address },
    "presharedKey" to (presharedKey ?: ""),
    "persistentKeepalive" to persistentKeepalive,
    "enabled" to enabled
)

