package com.app.controller

import com.app.model.*
import com.app.service.GlobalConfigurationService
import com.app.service.WireGuardManagementService
import com.app.service.WireGuardTemplateService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/api/clients")
class WireGuardClientController(
    private val managementService: WireGuardManagementService,
    private val templateService: WireGuardTemplateService,
    private val globalConfigurationService: GlobalConfigurationService
) {

    /**
     * Get detailed client information with server details
     */
    @GetMapping("/{clientId}")
    fun getClientDetails(@PathVariable clientId: UUID): ResponseEntity<ClientConfigurationResponse> {
        val client = managementService.getClientById(clientId)

        val globalConfig = globalConfigurationService.getCurrentConfig()

        val response = ClientConfigurationResponse(
            id = client.id,
            name = client.name,
            publicKey = client.publicKey,
            allowedIPs = client.allowedIPs.map { it.address },
            enabled = client.enabled,
            isOnline = client.isOnline,
            lastHandshake = client.lastHandshake,
            persistentKeepalive = client.persistentKeepalive,
            server = ServerInfo(
                id = client.server.id,
                name = client.server.name,
                endpoint = globalConfig.serverEndpoint,
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
        @PathVariable clientId: UUID,
        @RequestParam(defaultValue = "false") allowAllTraffic: Boolean = false
    ): ResponseEntity<ConfigurationPreview> {
        val configContent = generateClientConfiguration(clientId, allowAllTraffic)
        val client = managementService.getClientById(clientId)
        val server = managementService.getServerById(client.server.id)

        // Validate configuration format
        val validationErrors = templateService.validateConfigFormat(configContent)
        val configHash = templateService.generateConfigHash(configContent)

        val sanitizedFileName = sanitizeFileName(client.name)

        val preview = ConfigurationPreview(
            fileName = "${sanitizedFileName}.conf",
            content = configContent,
            metadata = ConfigurationMetadata(
                clientId = client.id,
                serverName = server.name,
                createdAt = LocalDateTime.now(),
                allowAllTraffic = allowAllTraffic,
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
        @RequestParam(defaultValue = "false") allowAllTraffic: Boolean = false
    ): ResponseEntity<String> {
        val configContent = generateClientConfiguration(clientId, allowAllTraffic)
        val client = managementService.getClientById(clientId)
        val sanitizedFileName = sanitizeFileName(client.name)

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
        val configContent = generateClientConfiguration(clientId, allowAllTraffic = false)
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
    private fun generateClientConfiguration(clientId: UUID, allowAllTraffic: Boolean): String {
        val client = managementService.getClientById(clientId)

        if (!client.enabled) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Client is disabled")
        }

        val server = managementService.getServerById(client.server.id)

        return templateService.generateClientConfigWithPrivateKey(
            clientPrivateKey = client.privateKey,
            client = client,
            server = server,
            allowAllTraffic = allowAllTraffic
        )
    }

    /**
     * Sanitize filename for safe download
     */
    private fun sanitizeFileName(originalName: String): String {
        val illegalChars = setOf('.', ',', '/', '?', '<', '>', '\\', ':', '*', '|', '"', '\n', '\r', '\t')
        val reservedNames = setOf("con", "nul", "prn", "aux", "com1", "com2", "com3", "com4",
                                 "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2",
                                 "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9")

        var sanitized = originalName
            .replace(Regex("\\s+"), "_") // Replace spaces with underscores
            .filter { it !in illegalChars } // Remove illegal characters
            .take(50) // Limit length

        // Replace reserved names
        if (sanitized.lowercase() in reservedNames) {
            sanitized = "client_$sanitized"
        }

        return sanitized.ifBlank { "client_config" }
    }
}