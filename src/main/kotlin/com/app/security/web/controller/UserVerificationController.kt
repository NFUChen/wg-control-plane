package com.app.security.web.controller

import com.app.security.repository.model.User
import com.app.security.service.UserVerificationService
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.view.RedirectView

data class VerificationTokenRequest(
    val token: String
)

data class VerificationRequiredResponse(
    val verificationRequired: Boolean
)

@RestController
@RequestMapping("/api/public/user/verification")
class PublicUserVerificationController(
    private val userVerificationService: UserVerificationService
) {
    private val logger = LoggerFactory.getLogger(PublicUserVerificationController::class.java)
    @PostMapping("/verify-code")
    fun verifyUser(
        @RequestBody request: VerificationTokenRequest
    ): Map<String, String> {
        userVerificationService.verifyUser(request.token)
        return mapOf("message" to "User verified successfully")
    }

    @GetMapping("/verify")
    fun verifyUserByLink(
        @RequestParam token: String
    ): RedirectView {

        return try {
            userVerificationService.verifyUser(token)
            // 驗證成功，重定向到成功頁面
            RedirectView(userVerificationService.getSuccessRedirectEndpoint())
        } catch (error: Exception) {
            // 驗證失敗，重定向到錯誤頁面
            logger.error("User verification failed: ${error.message}")
            RedirectView(userVerificationService.getErrorRedirectEndpoint())
        }
    }
}


@RestController
@RequestMapping("/api/private/user/verification")
class PrivateUserVerificationController(
    private val userVerificationService: UserVerificationService
) {
    @PostMapping("/send-email")
    fun sendVerificationEmail(
        @AuthenticationPrincipal user: User
    ): Map<String, String> {
        userVerificationService.sendVerificationEmail(user.id!!)
        return mapOf("message" to "Verification email sent successfully")
    }

    @GetMapping("/required")
    fun isVerificationRequired(
        @AuthenticationPrincipal user: User
    ): VerificationRequiredResponse {
        val isRequired = !userVerificationService.isUserVerified(user.id!!)
        return VerificationRequiredResponse(isRequired)
    }
}