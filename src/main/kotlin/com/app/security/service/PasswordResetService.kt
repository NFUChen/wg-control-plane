package com.app.security.service

import com.app.security.InvalidPasswordResetToken
import com.app.security.OnlyLocalAccountCanResetPassword
import com.app.security.UserNotFound
import com.app.security.repository.UserRepository
import com.app.security.repository.model.User
import com.app.security.service.email.*
import com.app.security.service.redis.RedisRepository
import com.app.security.config.AppProperties
import com.app.common.template.TemplateService
import jakarta.transaction.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

/**
 * Password reset flow for users who forgot their password.
 */
interface PasswordResetService {
    /**
     * Send a password reset email.
     * @param email User email address
     */
    fun sendPasswordResetEmail(email: String)

    /**
     * Reset password using a valid token.
     * @param token Password reset token
     * @param newPassword New password
     */
    fun resetPassword(token: String, newPassword: String)

    /**
     * Build the password reset email for a user.
     * @param user User entity
     * @param resetToken One-time reset token
     * @return Email model
     */
    fun createPasswordResetEmail(user: User, resetToken: String): Email

    /**
     * Base URL of the frontend used in reset links.
     * @return Frontend base URL
     */
    fun getPasswordResetFrontendEndpoint(): String
}

/**
 * Token storage for password reset.
 */
interface PasswordResetTokenService {
    /**
     * Create and store a reset token for the given user id.
     * @param userId User id
     * @return Opaque token string
     */
    fun generatePasswordResetToken(userId: String): String

    /**
     * Validate and consume a token.
     * @param token Reset token
     * @return User id if valid
     */
    fun verifyAndConsumeToken(token: String): String?
}

/**
 * Redis-backed password reset tokens.
 */
@Service
class RedisPasswordResetTokenService(
    private val redisRepository: RedisRepository,
    private val tokenGenerator: TokenGenerator
) : PasswordResetTokenService {

    companion object {
        private const val TOKEN_PREFIX = "password_reset"
        private const val TOKEN_TTL_MINUTES = 15L // reset token TTL
    }

    override fun generatePasswordResetToken(userId: String): String {
        val token = tokenGenerator.generateToken()
        val key = redisRepository.withPrefix(TOKEN_PREFIX, token)
        redisRepository.setWithTtl(key, userId, TOKEN_TTL_MINUTES, TimeUnit.MINUTES)
        return token
    }

    override fun verifyAndConsumeToken(token: String): String? {
        val key = redisRepository.withPrefix(TOKEN_PREFIX, token)
        return redisRepository.getAndDelete(key) // one-time use
    }
}

/**
 * Default password reset implementation.
 */
@Service
class DefaultPasswordResetService(
    private val appProperties: AppProperties,
    private val emailService: EmailService,
    private val passwordResetTokenService: PasswordResetTokenService,
    private val userRepository: UserRepository,
    private val templateService: TemplateService,
    private val emailProperties: EmailProperties,
    private val passwordEncoder: PasswordEncoder
) : PasswordResetService {

    override fun sendPasswordResetEmail(email: String) {
        val user = userRepository.findByEmail(email) ?: throw UserNotFound

        // Only local (password) accounts can use email reset
        if (!user.isLocalAccount()) {
            throw OnlyLocalAccountCanResetPassword
        }

        val resetToken = passwordResetTokenService.generatePasswordResetToken(user.id.toString())
        val resetEmail = createPasswordResetEmail(user, resetToken)
        emailService.sendEmail(resetEmail)
    }

    @Transactional
    override fun resetPassword(token: String, newPassword: String) {
        val userId = passwordResetTokenService.verifyAndConsumeToken(token)
            ?: throw InvalidPasswordResetToken

        val user = userRepository.findById(UUID.fromString(userId)).getOrNull()
            ?: throw UserNotFound

        if (!user.isLocalAccount()) {
            throw OnlyLocalAccountCanResetPassword
        }

        val hashedPassword = passwordEncoder.encode(newPassword)
        user.updateHashedPassword(hashedPassword)
        userRepository.save(user)
    }

    override fun createPasswordResetEmail(user: User, resetToken: String): Email {
        val resetUrl = "${getPasswordResetFrontendEndpoint()}/auth/reset-password?token=$resetToken"

        val variables = mapOf(
            "username" to user.username,
            "resetUrl" to resetUrl,
            "serviceName" to emailProperties.fromName,
            "expirationMinutes" to "15"
        )

        val htmlContent = templateService.processTemplate(
            "auth/password-reset-email.html",
            variables
        )

        return Email(
            from = EmailAddress(emailProperties.from, emailProperties.fromName),
            to = listOf(EmailAddress(user.mustGetEmail(), user.username)),
            subject = "[${emailProperties.fromName}] Password reset request",
            content = EmailContent(
                html = htmlContent
            )
        )
    }

    override fun getPasswordResetFrontendEndpoint(): String {
        return appProperties.baseUrl
    }
}