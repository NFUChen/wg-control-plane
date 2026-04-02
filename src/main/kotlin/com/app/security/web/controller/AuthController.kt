package com.app.security.web.controller

import com.app.security.config.WebProperties
import com.app.security.repository.model.User
import com.app.security.service.*
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*


data class UserRegistration(
    @field:NotBlank(message = "Username must not be blank")
    val username: String,
    @field:Email
    val email: String,
    @field:NotBlank(message = "Password must not be blank")
    val password: String,
)

data class PasswordResetRequest(
    @field:Email(message = "A valid email address is required")
    @field:NotBlank(message = "Email must not be blank")
    val email: String
)

data class PasswordResetConfirmation(
    @field:NotBlank(message = "Reset token must not be blank")
    val token: String,
    @field:NotBlank(message = "New password must not be blank")
    val newPassword: String
)


@RestController
@RequestMapping("/api")
class AuthController(
    val webProperties: WebProperties,
    val authService: AuthService,
    val registrationService: RegistrationService,
    val passwordResetService: PasswordResetService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val loginProcessingService: LoginProcessingService,
    ) {

    @PostMapping("/public/auth/register")
    fun register(
        @Valid @RequestBody user: UserRegistration
    ): ResponseEntity<Map<String, String>> {
        val request = UserRegistrationRequest(
            username = user.username,
            email = user.email,
            password = user.password,
            roles = authService.DEFAULT_ROLES
        )
        val registeredUser = registrationService.registerUser(request)
        applicationEventPublisher.publishEvent(UserRegistrationEvent(registeredUser))
        return ResponseEntity.ok(mapOf("message" to "OK"))
    }

    @GetMapping("/private/auth/me")
    fun getCurrentUser(@AuthenticationPrincipal userDetails: User): ResponseEntity<UserDetails> {
        return ResponseEntity(userDetails, HttpStatus.OK)
    }

    @GetMapping("/private/auth/logout")
    fun logout(
        @AuthenticationPrincipal userDetails: User,
        response: HttpServletResponse
    ): ResponseEntity<Map<String, String>> {
        authService.logout(response)
        return ResponseEntity.ok(mapOf("message" to "OK"))
    }

    @PostMapping("/public/auth/login")
    fun login(
        @RequestBody credentials: UserCredentials,
        response: HttpServletResponse
    ): ResponseEntity<Map<String, String>> {
        val user = authService.authenticate(credentials)

        val result = loginProcessingService.processLogin(user)

        authService.writeTokenToCookie(response, authService.LOGIN_KEY, result.userToken)

        // Standard JSON response
        return ResponseEntity.ok(mapOf("message" to "OK"))
    }

    data class LogoutResponse(
        val keys: List<String>,
        val domain: String
    )

    @GetMapping("/public/auth/logout-info")
    fun logoutInfo(): LogoutResponse {
        return LogoutResponse(
            keys = listOf(authService.LOGIN_KEY),
            domain = webProperties.domain
        )
    }

    /**
     * Request a password reset email.
     */
    @PostMapping("/public/auth/reset-password/request")
    fun requestPasswordReset(
        @Valid @RequestBody request: PasswordResetRequest
    ): ResponseEntity<Map<String, String>> {
        passwordResetService.sendPasswordResetEmail(request.email)
        return ResponseEntity.ok(mapOf("message" to "If the email is registered, a password reset link has been sent"))
    }

    /**
     * Confirm password reset and set a new password.
     */
    @PostMapping("/public/auth/reset-password/confirm")
    fun confirmPasswordReset(
        @Valid @RequestBody request: PasswordResetConfirmation
    ): ResponseEntity<Map<String, String>> {
        passwordResetService.resetPassword(request.token, request.newPassword)
        return ResponseEntity.ok(mapOf("message" to "Password has been reset successfully"))
    }
}