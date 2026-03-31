package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.model.*
import freemarker.template.Configuration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.StringWriter

@Service
class WireGuardTemplateService(@Qualifier("templateConfiguration") private val freemarkerConfig: Configuration) {

    /**
     * Generate server configuration with all its clients
     */
    fun generateServerConfig(server: WireGuardServer): String {
        val dataModel: Map<String, Any> = mapOf(
            "privateKey" to server.privateKey,
            "address" to server.addresses.joinToString(", ") { it.address },
            "listenPort" to server.listenPort,
            "dnsServers" to server.dnsServers.joinToString(", ") { it.IP },
            "mtu" to (server.mtu ?: ""),
            "postUp" to (server.postUp ?: ""),
            "postDown" to (server.postDown ?: ""),
            "clients" to server.clients.filter { it.enabled }.map { it.toTemplateMap() }
        )

        return renderTemplate("server-config.ftl", dataModel)
    }

    /**
     * Generate client configuration for connecting to a server
     */
    fun generateClientConfig(
        client: WireGuardClient,
        server: WireGuardServer,
        allowAllTraffic: Boolean = false
    ): String {
        val allowedIPs = if (allowAllTraffic) {
            listOf("0.0.0.0/0", "::/0")
        } else {
            server.addresses.map { it.address }
        }

        val dataModel = mutableMapOf<String, Any>(
            "privateKey" to "", // Client should provide their own private key
            "address" to (client.allowedIPs.joinToString(", ") { it.address }),
            "dnsServers" to server.dnsServers.joinToString(", ") { it.IP },
            "serverPublicKey" to server.publicKey,
            "serverEndpoint" to server.endpoint,
            "allowedIPs" to allowedIPs.joinToString(", "),
            "persistentKeepalive" to client.persistentKeepalive
        )

        if (server.mtu != null) {
            dataModel["mtu"] = server.mtu!!
        }

        return renderTemplate("client-config.ftl", dataModel)
    }

    /**
     * Generate client configuration with custom private key
     */
    fun generateClientConfigWithPrivateKey(
        clientPrivateKey: String,
        client: WireGuardClient,
        server: WireGuardServer,
        allowAllTraffic: Boolean = false
    ): String {
        val allowedIPs = if (allowAllTraffic) {
            listOf("0.0.0.0/0", "::/0")
        } else {
            server.addresses.map { it.address }
        }

        val dataModel = mutableMapOf<String, Any>(
            "privateKey" to clientPrivateKey,
            "address" to (client.allowedIPs.joinToString(", ") { it.address }),
            "dnsServers" to server.dnsServers.joinToString(", ") { it.IP },
            "serverPublicKey" to server.publicKey,
            "serverEndpoint" to server.endpoint,
            "allowedIPs" to allowedIPs.joinToString(", "),
            "persistentKeepalive" to client.persistentKeepalive
        )

        if (server.mtu != null) {
            dataModel["mtu"] = server.mtu!!
        }

        return renderTemplate("client-config.ftl", dataModel)
    }

    /**
     * Render specified template
     */
    private fun renderTemplate(templateName: String, dataModel: Map<String, Any>): String {
        val template = freemarkerConfig.getTemplate(templateName)
        val out = StringWriter()
        template.process(dataModel, out)
        return out.toString()
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
    "allowedIPs" to allowedIPs.joinToString(", ") { it.address },
    "presharedKey" to (presharedKey ?: ""),
    "persistentKeepalive" to persistentKeepalive,
    "enabled" to enabled
)

