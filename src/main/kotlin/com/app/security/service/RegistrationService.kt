package com.app.security.service

import com.app.security.RegistrationProviderNotMatchedWithExistingUser
import com.app.security.UserAlreadyRegistered
import com.app.security.UserAlreadyRegisteredWithExternalProvider
import com.app.security.repository.UserRepository
import com.app.security.repository.model.User
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEvent
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class UserRegistrationEvent(user: User) : ApplicationEvent(user)

data class UserRegistrationRequest(
    val username: String,
    val email: String,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val password: String,
    val roles: Iterable<String>,
    val isUpsert: Boolean = false,
)

data class ExternalUserRegistrationRequest(
    val username: String,

    val email: String?,
    val oauthId: String,
    val profileImageEndpoint: String,
    val roles: Iterable<String>,
    val provider: String,
)

interface RegistrationService {
    fun registerUser(request: UserRegistrationRequest): User
    fun registerExternalUser(request: ExternalUserRegistrationRequest): User
}

@Service
class DefaultRegistrationService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : RegistrationService {

    private val logger = LoggerFactory.getLogger(DefaultRegistrationService::class.java)

    @Transactional
    override fun registerUser(request: UserRegistrationRequest): User {
        val existingUser = userRepository.findByEmail(request.email)
        if (existingUser != null) {
            if (request.isUpsert) {
                return existingUser
            }
            if (!existingUser.isLocalAccount()) {
                throw UserAlreadyRegisteredWithExternalProvider
            }
            throw UserAlreadyRegistered
        }

        val user = User(
            name = request.username,
            email = request.email,
            password = passwordEncoder.encode(request.password),
            roles = request.roles.toSet(),
            provider = User.DEFAULT_PLATFORM,
        )

        return userRepository.save(user)
    }

    @Transactional
    override fun registerExternalUser(request: ExternalUserRegistrationRequest): User {
        // Prioritize using oauthId to find existing users
        var existingUser = userRepository.findByOauthId(request.oauthId)

        // If not found via oauthId and email exists, search by email
        if (existingUser == null && !request.email.isNullOrBlank()) {
            existingUser = userRepository.findByEmail(request.email)
        }

        if (existingUser != null) {
            if (existingUser.provider != request.provider) {
                throw RegistrationProviderNotMatchedWithExistingUser
            }
            // If the user exists but has a different oauthId, update it to ensure the user can log in, but only if provider is the same
            if (existingUser.oauthId != request.oauthId) {
                existingUser.oauthId = request.oauthId
            }
            // Ensure existing OAuth users are in verified status
            if (!existingUser.isVerified) {
                logger.info("Setting verification status to true for existing OAuth user: ${existingUser.email ?: existingUser.id}")
                existingUser.verify()
            }
            userRepository.save(existingUser)
            return existingUser
        }

        val user = User(
            name = request.username,
            email = request.email,
            password = "",
            provider = request.provider,
            roles = request.roles.toSet(),
            oauthId = request.oauthId
        )
        user.verify()

        return userRepository.save(user)
    }

}