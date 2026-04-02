package com.app.security.web.controller

import com.app.security.repository.model.ServiceAccount
import com.app.security.service.ServiceAccountAuthRequest
import com.app.security.service.ServiceAccountCreationResponse
import com.app.security.service.ServiceAccountManager
import com.app.security.service.ServiceAccountRegistration
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer"
)


@RestController
@RequestMapping("/api/public/service-accounts")
class PublicServiceController(
    private val serviceAccountManager: ServiceAccountManager
) {
    /**
     * Authenticate a service account and return an access token.
     */
    @PostMapping("/auth")
    fun authenticate(
        @Valid @RequestBody request: ServiceAccountAuthRequest
    ): TokenResponse {
        val serviceAccount = serviceAccountManager.authenticate(request)
        val accessToken = serviceAccountManager.generateAccessToken(serviceAccount)

        return TokenResponse(accessToken = accessToken)
    }
}

@RestController
@RequestMapping("/api/private/service-accounts")
class ServiceAccountController(
    private val serviceAccountManager: ServiceAccountManager
) {



    /**
     * Create a service account. The system generates a client secret shown only in this response.
     * Requires admin role.
     */
    @PostMapping("/create")
    fun createServiceAccount(
        @Valid @RequestBody request: ServiceAccountRegistration
    ): ResponseEntity<ServiceAccountCreationResponse> {
        val creationResponse = serviceAccountManager.createServiceAccount(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(creationResponse)
    }

    /**
     * Get a service account by client ID. Requires admin role.
     */
    @GetMapping("/{clientId}")
    fun getServiceAccount(
        @PathVariable clientId: String
    ): ServiceAccount {
        return serviceAccountManager.getServiceAccountByClientId(clientId)
    }

    /**
     * Return OAuth-style scopes for a service account (e.g. after JWT validation).
     */
    @GetMapping("/{clientId}/scopes")
    fun getServiceAccountScopes(
        @PathVariable clientId: String
    ): Map<String, Set<String>> {
        val scopes = serviceAccountManager.getScopes(clientId)
        return mapOf("scopes" to scopes)
    }

    /**
     * Enable a service account. Requires admin role.
     */
    @PutMapping("/{clientId}/enable")
    fun enableServiceAccount(
        @PathVariable clientId: String
    ): Map<String, String> {
        serviceAccountManager.enableServiceAccount(clientId)
        return mapOf("message" to "Service Account enabled")
    }

    /**
     * Disable a service account. Requires admin role.
     */
    @PutMapping("/{clientId}/disable")
    fun disableServiceAccount(
        @PathVariable clientId: String
    ): Map<String, String> {
        serviceAccountManager.disableServiceAccount(clientId)
        return mapOf("message" to "Service Account disabled")
    }

    /**
     * Delete a service account. Requires admin role.
     */
    @DeleteMapping("/{clientId}")
    fun deleteServiceAccount(
        @PathVariable clientId: String
    ): Map<String, String> {
        serviceAccountManager.deleteServiceAccount(clientId)
        return mapOf("message" to "Service Account deleted")
    }
}
