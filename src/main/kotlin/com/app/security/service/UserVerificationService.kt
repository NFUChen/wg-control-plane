package com.app.security.service

import com.app.security.InvalidVerificationToken
import com.app.security.UserAlreadyVerified
import com.app.security.UserNotFound
import com.app.security.repository.UserRepository
import com.app.security.repository.model.User
import com.app.security.service.email.*
import com.app.security.service.redis.RedisRepository
import com.app.security.config.AppProperties
import com.app.security.service.template.TemplateService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

interface UserVerificationService {
    fun sendVerificationEmail(userId: UUID)
    fun isUserVerified(userId: UUID): Boolean
    fun verifyUser(token: String)
    fun createVerificationEmail(user: User, verificationToken: String): Email
    fun getVerificationFrontendEndpoint(): String
    fun getSuccessRedirectEndpoint(): String {
        return "${getVerificationFrontendEndpoint()}/verification/success"
    }
    fun getErrorRedirectEndpoint(): String {
        return "${getVerificationFrontendEndpoint()}/verification/error"
    }
}

interface VerificationTokenService {
    fun generateVerificationToken(userId: String): String
    fun verifyAndConsumeToken(token: String): String? // Returns userId if valid
    fun isTokenValid(token: String): Boolean
}

@Service
class RedisVerificationTokenService(
    private val redisRepository: RedisRepository,
    private val tokenGenerator: TokenGenerator
) : VerificationTokenService {
    companion object {
        private const val TOKEN_PREFIX = "verification"
        private const val TOKEN_TTL_MINUTES = 5L
    }

    override fun generateVerificationToken(userId: String): String {
        val token = tokenGenerator.generateToken()
        val key = redisRepository.withPrefix(TOKEN_PREFIX, token)
        redisRepository.setWithTtl(key, userId, TOKEN_TTL_MINUTES, TimeUnit.MINUTES)
        return token
    }

    override fun verifyAndConsumeToken(token: String): String? {
        val key = redisRepository.withPrefix(TOKEN_PREFIX, token)
        return redisRepository.getAndDelete(key) // 一次性消費，驗證後自動刪除
    }

    override fun isTokenValid(token: String): Boolean {
        val key = redisRepository.withPrefix(TOKEN_PREFIX, token)
        return redisRepository.exists(key)
    }
}

@Service
class DefaultUserVerificationService(
    private val appProperties: AppProperties,
    private val emailService: EmailService,
    private val verificationTokenService: VerificationTokenService,
    private val userRepository: UserRepository,
    private val templateService: TemplateService,
    private val emailProperties: EmailProperties,
) : UserVerificationService {
    override fun sendVerificationEmail(userId: UUID) {
        val user = userRepository.findById(userId).getOrNull() ?: throw UserNotFound
        if (user.isVerified) {
            throw UserAlreadyVerified
        }
        val verificationToken = verificationTokenService.generateVerificationToken(user.id.toString())
        val email = createVerificationEmail(user, verificationToken)
        emailService.sendEmail(email)
    }

    override fun isUserVerified(userId: UUID): Boolean {
        val user = userRepository.findById(userId).getOrNull() ?: throw UserNotFound
        return user.isVerified
    }


    @Transactional
    override fun verifyUser(token: String) {
        val userId = verificationTokenService.verifyAndConsumeToken(token) ?: throw InvalidVerificationToken
        val user = userRepository.findById(UUID.fromString(userId)).getOrNull() ?: throw UserNotFound
        if (user.isVerified) {
            return
        }
        user.isVerified = true
        userRepository.save(user)
    }



    override fun createVerificationEmail(user: User, verificationToken: String): Email {
        val verificationUrl = "${appProperties.baseUrl}/api/public/user/verification/verify?token=$verificationToken"

        val variables = mapOf(
            "username" to user.username,
            "verificationUrl" to verificationUrl,
            "serviceName" to emailProperties.fromName
        )

        val htmlContent = templateService.processTemplate(
            "auth/verification-email.html",
            variables
        )

        return Email(
            from = EmailAddress(emailProperties.from, emailProperties.fromName),
            to = listOf(EmailAddress(user.mustGetEmail(), user.username)),
            subject = "[${emailProperties.fromName}] 請完成您的帳戶驗證",
            content = EmailContent(
                html = htmlContent
            )
        )
    }

    override fun getVerificationFrontendEndpoint(): String {
        return appProperties.baseUrl
    }
}
