package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.model.*
import freemarker.template.Configuration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.StringWriter

@Service
class TemplateService(@Qualifier("templateConfiguration") private val freemarkerConfig: Configuration) {

    /**
     * Generate server configuration - directly accepts WgInterface and peers
     */
    fun generateServerConfig(serverInterface: WgInterface, peers: List<WgPeer>): String {
        val dataModel: Map<String, Any> = mapOf(
            "privateKey" to serverInterface.privateKey,
            "address" to serverInterface.addresses.joinToString(", ") { it.address },
            "listenPort" to serverInterface.listenPort,
            "peers" to peers.map { it.toTemplateMap() }
        )

        return renderTemplate("server-config.ftl", dataModel)
    }

    /**
     * Generate client configuration - client connects to server
     */
    fun generateClientConfig(
        clientInterface: WgInterface,
        serverPublicKey: String,
        serverEndpoint: String,
        allowedIPs: List<IPAddress> = listOf(IPAddress("0.0.0.0/0"))
    ): String {
        val dataModel = mutableMapOf<String, Any>(
            "privateKey" to clientInterface.privateKey,
            "address" to clientInterface.addresses.joinToString(", ") { it.address },
            "serverPublicKey" to serverPublicKey,
            "serverEndpoint" to serverEndpoint,
            "allowedIPs" to allowedIPs.joinToString(", ") { it.address }
        )

        // Only add listen port when needed
        if (clientInterface.listenPort > 0) {
            dataModel["listenPort"] = clientInterface.listenPort.toString()
        }

        return renderTemplate("client-config.ftl", dataModel)
    }

    /**
     * Generate client configuration - automatically get public key from server interface
     */
    fun generateClientConfig(
        clientInterface: WgInterface,
        serverInterface: WgInterface,
        serverEndpoint: String,
        allowedIPs: List<IPAddress> = listOf(IPAddress("0.0.0.0/0"))
    ): String {
        return generateClientConfig(
            clientInterface = clientInterface,
            serverPublicKey = serverInterface.publicKey,
            serverEndpoint = serverEndpoint,
            allowedIPs = allowedIPs
        )
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
 * Extension function: Convert WgPeer to template data
 */
private fun WgPeer.toTemplateMap(): Map<String, Any> = mapOf(
    "publicKey" to publicKey,
    "allowedIPs" to allowedIPs.joinToString(", ") { it.address },
    "endpoint" to endpoint,
    "presharedKey" to (presharedKey ?: ""),
    "persistentKeepalive" to persistentKeepalive
)