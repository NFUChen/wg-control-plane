package com.app.security.service

import com.app.security.LocalLoginNotAllowed
import com.app.security.PasswordNotMatch
import com.app.security.UserNotFound
import com.app.security.config.WebProperties
import com.app.security.repository.UserRepository
import com.app.security.repository.model.User
import com.app.security.service.jwt.UserJwtService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.ApplicationEvent
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.jvm.optionals.getOrNull

interface Provider {
    val name: String
}


data class UserCredentials(
    val email: String,
    val password: String
)

class UserLoginEvent(user: User) : ApplicationEvent(user)


interface AuthService {
    val DEFAULT_ROLES: Iterable<String>
    val LOGIN_KEY: String
    fun authenticate(credentials: UserCredentials): User
    fun assignRoles(userId: UUID, roles: Iterable<String>): User
    fun login(user: User): String
    fun writeTokenToCookie(response: HttpServletResponse, key: String, token: String)
    fun logout(response: HttpServletResponse)
    fun parseUserToken(token: String): User?
    fun isValidToken(token: String): Boolean
}

@Service
class DefaultAuthService(
    val webProperties: WebProperties,
    val userJwtService: UserJwtService,
    val userRepository: UserRepository,
    val passwordEncoder: PasswordEncoder
) : AuthService {

    override val DEFAULT_ROLES = setOf("ROLE_USER")

    @Transactional
    override fun assignRoles(userId: UUID, roles: Iterable<String>): User {
        val user = userRepository.findById(userId).getOrNull() ?: throw UserNotFound
        user.systemRoles.clear()
        user.systemRoles.addAll(roles.map { "ROLE_${it}" })
        val savedUser = userRepository.save(user)
        return savedUser
    }

    override val LOGIN_KEY = "jwt"
    override fun authenticate(credentials: UserCredentials): User {
        val user = userRepository.findByEmail(credentials.email) ?: throw UserNotFound
        if (!user.isLocalAccount()) throw LocalLoginNotAllowed

        if (!passwordEncoder.matches(credentials.password, user.password)) throw PasswordNotMatch
        return user
    }

    override fun login(user: User): String {
        return userJwtService.issueToken(user, webProperties.jwtValidSeconds)
    }

    override fun writeTokenToCookie(response: HttpServletResponse, key: String, token: String) {
        val cookie = Cookie(key, token)
        cookie.domain = webProperties.domain
        cookie.path = "/"
        cookie.isHttpOnly = true
        cookie.secure = true
        cookie.setAttribute("SameSite", "None")
        response.addCookie(cookie)
    }

    override fun logout(response: HttpServletResponse) {
        val cookie = Cookie(LOGIN_KEY, null)
        cookie.path = "/"
        cookie.isHttpOnly = true
        cookie.secure = true
        cookie.maxAge = 0
        cookie.domain = webProperties.domain
        cookie.setAttribute("SameSite", "None")
        response.addCookie(cookie)
    }

    override fun parseUserToken(token: String): User? {
        return userJwtService.parseToken(token)
    }

    override fun isValidToken(token: String): Boolean {
        return userJwtService.isValidToken(token)
    }
}