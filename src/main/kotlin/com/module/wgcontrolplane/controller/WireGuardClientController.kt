package com.module.wgcontrolplane.controller

import com.module.wgcontrolplane.service.WireGuardManagementService
import com.module.wgcontrolplane.service.WireGuardTemplateService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/clients")
class WireGuardClientController(
    private val managementService: WireGuardManagementService,
    private val templateService: WireGuardTemplateService
) {

    /**
     * Get client configuration file
     */
    @GetMapping("/{clientId}/config")
    fun getClientConfig(
        @PathVariable clientId: UUID,
        @RequestParam(defaultValue = "false") allowAllTraffic: Boolean = false
    ): ResponseEntity<String> {

        // Get client directly by ID
        val client = managementService.getClientById(clientId)

        if (!client.enabled) {
            return ResponseEntity.badRequest().body("Client is disabled")
        }

        // Get server for this client
        val server = managementService.getServerById(client.server.id)

        // Generate client configuration
        val configContent = templateService.generateClientConfigWithPrivateKey(
            clientPrivateKey = client.privateKey,
            client = client,
            server = server,
            allowAllTraffic = allowAllTraffic
        )

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .header("Content-Disposition", "attachment; filename=\"${client.name}.conf\"")
            .body(configContent)
    }

    /**
     * Get client configuration info (without private key for security)
     */
    @GetMapping("/{clientId}/info")
    fun getClientInfo(@PathVariable clientId: UUID): ResponseEntity<Any> {
        val client = managementService.getClientById(clientId)
            ?: return ResponseEntity.notFound().build()

        val server = client.server

        return ResponseEntity.ok(mapOf(
            "id" to client.id,
            "name" to client.name,
            "publicKey" to client.publicKey,
            "allowedIPs" to client.plainTextAllowedIPs,
            "enabled" to client.enabled,
            "isOnline" to client.isOnline,
            "server" to mapOf(
                "id" to server.id,
                "name" to server.name,
                "endpoint" to server.endpoint
            )
        ))
    }
}