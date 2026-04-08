package com.app.controller

import com.app.service.WireGuardManagementService
import com.app.view.ClientResponse
import com.app.view.UpdateClientStatsRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*



data class AgentTokenRequest(
    val agentToken: String
)
// * Machine-to-machine endpoints secured by [com.app.security.web.filter.ApiKeyAuthenticationInterceptor]
// * (X-API-Key / Bearer). `web.unprotected-routes` includes `/api/internal/**` so Spring Security does not require JWT.
@RestController
@RequestMapping("/api/internal/wireguard")
class InternalWireGuardController(
    private val wireGuardService: WireGuardManagementService
) {

    @PutMapping("/clients/{clientId}/stats")
    fun updateClientStats(
        @PathVariable clientId: UUID,
        @Valid @RequestBody request: UpdateClientStatsRequest
    ): ResponseEntity<ClientResponse> {
        val client = wireGuardService.updateClientStats(
            clientId,
            request.lastHandshake,
            request.dataReceived,
            request.dataSent
        )
        return ResponseEntity.ok(ClientResponse.from(client))
    }
}
