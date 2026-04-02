package com.app.security.service

import com.app.security.ServiceAccountAlreadyExists
import com.app.security.ServiceAccountNotEnabled
import com.app.security.ServiceAccountNotFound
import com.app.security.ServiceAccountSecretNotMatch
import com.app.security.repository.ServiceAccountRepository
import com.app.security.repository.model.ServiceAccount
import com.app.security.service.jwt.ServiceAccountJwtService
import jakarta.transaction.Transactional
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.*
import kotlin.jvm.optionals.getOrNull


// DTO classes for API requests and responses
data class ServiceAccountAuthRequest(
    @field:NotBlank(message = "Client ID cannot be empty")
    val clientId: String,
    @field:NotBlank(message = "Client Secret cannot be empty")
    val clientSecret: String
)

data class ServiceAccountRegistration(
    @field:NotBlank(message = "Service account name cannot be empty")
    @field:Size(min = 3, max = 100, message = "Service account name must be between 3 and 100 characters")
    val name: String,
    val description: String? = null,
    val scopes: Set<String> = emptySet(),
)

// Response when creating a service account; includes generated client secret (shown once).
data class ServiceAccountCreationResponse(
    val serviceAccount: ServiceAccount,
    val clientSecret: String // system-generated; returned only at creation
)

interface ServiceAccountManager {
    fun authenticate(authRequest: ServiceAccountAuthRequest): ServiceAccount // verify secret; return account
    fun generateAccessToken(serviceAccount: ServiceAccount): String // issue JWT
    fun getScopes(clientId: String): Set<String> // scopes after JWT validation

    @PreAuthorize("hasRole('ADMIN')")
    fun getServiceAccountByClientId(clientId: String): ServiceAccount // non-login lookups

    @PreAuthorize("hasRole('ADMIN')")
    fun createServiceAccount(account: ServiceAccountRegistration): ServiceAccountCreationResponse // create; auto clientSecret

    @PreAuthorize("hasRole('ADMIN')")
    fun disableServiceAccount(clientId: String)

    @PreAuthorize("hasRole('ADMIN')")
    fun enableServiceAccount(clientId: String)

    @PreAuthorize("hasRole('ADMIN')")
    fun deleteServiceAccount(clientId: String)
}


interface ClientSecretGenerator {
    fun generateSecret(): String
}

@Service
class DefaultClientSecretGenerator(): ClientSecretGenerator {
    private val secureRandom = SecureRandom()
    override fun generateSecret(): String {
        val bytes = ByteArray(32) // 256 bits of entropy
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

}

@Service
class DefaultServiceAccountManager(
    val clientSecretGenerator: ClientSecretGenerator,
    val serviceAccountRepository: ServiceAccountRepository,
    val serviceAccountJwtService: ServiceAccountJwtService,
    val passwordEncoder: PasswordEncoder
): ServiceAccountManager {

    @Transactional
    override fun authenticate(authRequest: ServiceAccountAuthRequest): ServiceAccount {
        val account = serviceAccountRepository.findByClientId(authRequest.clientId).getOrNull() ?: throw ServiceAccountNotFound
        if (!account.enabled) {
            throw ServiceAccountNotEnabled
        }
        if (!passwordEncoder.matches(authRequest.clientSecret, account.clientSecretHash)) {
            throw ServiceAccountSecretNotMatch
        }

        account.lastUsedAt = LocalDateTime.now()
        return serviceAccountRepository.save(account)
    }

    private fun hashName(name: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(name.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes)
    }

    override fun generateAccessToken(serviceAccount: ServiceAccount): String {
        if (!serviceAccount.enabled) {
            throw ServiceAccountNotEnabled
        }
        return serviceAccountJwtService.issueToken(serviceAccount, -1) // unlimited expiration
    }

    override fun getScopes(clientId: String): Set<String> {
        val account = serviceAccountRepository.findByClientId(clientId).getOrNull() ?: throw ServiceAccountNotFound
        return account.scopes
    }

    override fun getServiceAccountByClientId(clientId: String): ServiceAccount {
        return serviceAccountRepository.findByClientId(clientId).getOrNull() ?: throw ServiceAccountNotFound
    }

    @Transactional
    override fun createServiceAccount(account: ServiceAccountRegistration): ServiceAccountCreationResponse {
        if (serviceAccountRepository.existsByClientId(account.name)) throw ServiceAccountAlreadyExists

        val clientSecret = clientSecretGenerator.generateSecret()
        val newAccount = ServiceAccount(
            clientId = hashName(account.name),
            clientSecretHash = passwordEncoder.encode(clientSecret),
            name = account.name,
            description = account.description,
            scopes = account.scopes,
            enabled = true
        )
        
        val savedAccount = serviceAccountRepository.save(newAccount)

        return ServiceAccountCreationResponse(
            serviceAccount = savedAccount,
            clientSecret = clientSecret
        )
    }

    @Transactional
    override fun disableServiceAccount(clientId: String) {
        val account = serviceAccountRepository.findByClientId(clientId).getOrNull() ?: throw ServiceAccountNotFound
        account.enabled = false
        serviceAccountRepository.save(account)
    }

    @Transactional
    override fun enableServiceAccount(clientId: String) {
        val account = serviceAccountRepository.findByClientId(clientId).getOrNull() ?: throw ServiceAccountNotFound
        account.enabled = true
        serviceAccountRepository.save(account)
    }

    @Transactional
    override fun deleteServiceAccount(clientId: String) {
        val account = serviceAccountRepository.findByClientId(clientId).getOrNull() ?: return
        serviceAccountRepository.delete(account)
    }

}