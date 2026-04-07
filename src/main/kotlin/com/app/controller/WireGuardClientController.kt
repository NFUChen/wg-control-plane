package com.app.controller

import com.app.model.*
import com.app.service.GlobalConfigurationService
import com.app.service.WireGuardManagementService
import com.app.service.WireGuardServerEndpointResolver
import com.app.service.WireGuardTemplateService
import com.app.utils.ConfigFileNameSanitizer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/api/private/wireguard/clients")
class WireGuardClientController(
    private val managementService: WireGuardManagementService,
    private val templateService: WireGuardTemplateService,
    private val globalConfigurationService: GlobalConfigurationService,
    private val wireGuardServerEndpointResolver: WireGuardServerEndpointResolver
) {

    /**
     * Get detailed client information with server details
     */
    @GetMapping("/{clientId}")
    fun getClientDetails(@PathVariable clientId: UUID): ResponseEntity<ClientConfigurationResponse> {
        val client = managementService.getClientById(clientId)

        val globalConfig = globalConfigurationService.getCurrentConfig()
        val endpoint = wireGuardServerEndpointResolver.resolve(client.server, globalConfig)

        val response = ClientConfigurationResponse(
            id = client.id,
            name = client.name,
            interfaceName = client.interfaceName,
            publicKey = client.publicKey,
            peerIPs = client.peerIPs.map { it.address },
            allowedIPs = client.allowedIPs.map { it.address },
            enabled = client.enabled,
            isOnline = client.isOnline,
            lastHandshake = client.lastHandshake,
            persistentKeepalive = client.persistentKeepalive,
            hostId = client.hostId?.toString(),
            server = ServerInfo(
                id = client.server.id,
                name = client.server.name,
                endpoint = endpoint,
                publicKey = client.server.publicKey,
                dnsServers = client.server.dnsServers.map { it.IP },
                mtu = client.server.mtu
            )
        )

        return ResponseEntity.ok(response)
    }

    /**
     * Get client configuration preview (JSON format with complete configuration including private key)
     */
    @GetMapping("/{clientId}/preview")
    fun getConfigurationPreview(
        @PathVariable clientId: UUID
    ): ResponseEntity<ConfigurationPreview> {
        val configContent = generateClientConfiguration(clientId)
        val client = managementService.getClientById(clientId)
        val server = managementService.getServerById(client.server.id)

        // Validate configuration format
        val validationErrors = templateService.validateConfigFormat(configContent)
        val configHash = templateService.generateConfigHash(configContent)

        val sanitizedFileName = ConfigFileNameSanitizer.sanitize(
            originalName = client.name,
            reservedNamePrefix = "client",
            fallback = "client_config"
        )

        val preview = ConfigurationPreview(
            fileName = "${sanitizedFileName}.conf",
            content = configContent,
            metadata = ConfigurationMetadata(
                clientId = client.id,
                serverName = server.name,
                createdAt = LocalDateTime.now(),
                configHash = configHash,
                validationErrors = validationErrors
            )
        )

        return ResponseEntity.ok(preview)
    }

    /**
     * Download client configuration file with private key (for actual usage)
     */
    @GetMapping("/{clientId}/download")
    fun downloadConfiguration(
        @PathVariable clientId: UUID,
    ): ResponseEntity<String> {
        val configContent = generateClientConfiguration(clientId)
        val client = managementService.getClientById(clientId)
        val sanitizedFileName = ConfigFileNameSanitizer.sanitize(
            originalName = client.name,
            reservedNamePrefix = "client",
            fallback = "client_config"
        )

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .header("Content-Disposition", "attachment; filename=\"${sanitizedFileName}.conf\"")
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("Expires", "0")
            .body(configContent)
    }

    /**
     * Validate client configuration
     */
    @GetMapping("/{clientId}/validate")
    fun validateConfiguration(@PathVariable clientId: UUID): ResponseEntity<Map<String, Any>> {
        val configContent = generateClientConfiguration(clientId)
        val client = managementService.getClientById(clientId)

        val validationErrors = templateService.validateConfigFormat(configContent)
        val isValid = validationErrors.isEmpty()

        val response = mapOf(
            "valid" to isValid,
            "errors" to validationErrors,
            "clientId" to client.id,
            "validatedAt" to LocalDateTime.now()
        )

        return ResponseEntity.ok(response)
    }

    /**
     * Generate client configuration content (shared logic)
     */
    private fun generateClientConfiguration(clientId: UUID): String {
        val client = managementService.getClientById(clientId)

        if (!client.enabled) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Client is disabled")
        }

        val server = managementService.getServerById(client.server.id)

        return templateService.generateClientConfigWithPrivateKey(
            clientPrivateKey = client.privateKey,
            client = client,
            server = server
        )
    }

}