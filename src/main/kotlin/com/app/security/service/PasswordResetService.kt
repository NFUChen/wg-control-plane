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
 * 密碼重置服務接口
 * 負責處理用戶忘記密碼的功能
 */
interface PasswordResetService {
    /**
     * 發送密碼重置郵件
     * @param email 用戶郵箱地址
     */
    fun sendPasswordResetEmail(email: String)

    /**
     * 重置密碼
     * @param token 密碼重置令牌
     * @param newPassword 新密碼
     */
    fun resetPassword(token: String, newPassword: String)

    /**
     * 創建密碼重置郵件
     * @param user 用戶對象
     * @param resetToken 重置令牌
     * @return 郵件對象
     */
    fun createPasswordResetEmail(user: User, resetToken: String): Email

    /**
     * 獲取密碼重置前端端點
     * @return 前端端點URL
     */
    fun getPasswordResetFrontendEndpoint(): String
}

/**
 * 密碼重置令牌服務接口
 */
interface PasswordResetTokenService {
    /**
     * 生成密碼重置令牌
     * @param userId 用戶ID
     * @return 重置令牌
     */
    fun generatePasswordResetToken(userId: String): String

    /**
     * 驗證並消費令牌
     * @param token 重置令牌
     * @return 用戶ID（如果有效）
     */
    fun verifyAndConsumeToken(token: String): String?
}

/**
 * 基於Redis的密碼重置令牌服務實現
 */
@Service
class RedisPasswordResetTokenService(
    private val redisRepository: RedisRepository,
    private val tokenGenerator: TokenGenerator
) : PasswordResetTokenService {

    companion object {
        private const val TOKEN_PREFIX = "password_reset"
        private const val TOKEN_TTL_MINUTES = 15L // 密碼重置令牌有效期15分鐘
    }

    override fun generatePasswordResetToken(userId: String): String {
        val token = tokenGenerator.generateToken()
        val key = redisRepository.withPrefix(TOKEN_PREFIX, token)
        redisRepository.setWithTtl(key, userId, TOKEN_TTL_MINUTES, TimeUnit.MINUTES)
        return token
    }

    override fun verifyAndConsumeToken(token: String): String? {
        val key = redisRepository.withPrefix(TOKEN_PREFIX, token)
        return redisRepository.getAndDelete(key) // 一次性消費，重置後自動刪除
    }
}

/**
 * 默認密碼重置服務實現
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

        // 只允許本地帳戶重置密碼
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

        // 檢查是否為本地帳戶
        if (!user.isLocalAccount()) {
            throw OnlyLocalAccountCanResetPassword
        }

        // 更新密碼（先進行加密）
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
            "expirationMinutes" to "15" // 令牌有效期
        )

        val htmlContent = templateService.processTemplate(
            "auth/password-reset-email.html",
            variables
        )

        return Email(
            from = EmailAddress(emailProperties.from, emailProperties.fromName),
            to = listOf(EmailAddress(user.mustGetEmail(), user.username)),
            subject = "[${emailProperties.fromName}] 密碼重置請求",
            content = EmailContent(
                html = htmlContent
            )
        )
    }

    override fun getPasswordResetFrontendEndpoint(): String {
        return appProperties.baseUrl
    }
}