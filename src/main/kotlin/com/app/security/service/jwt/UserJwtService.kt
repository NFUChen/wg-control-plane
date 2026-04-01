package com.app.security.service.jwt

import com.app.security.config.WebProperties
import com.app.security.repository.model.User
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.util.*


@Service("userJwtService")
class UserJwtService(
    val webProperties: WebProperties,
    val defaultJwtService: DefaultJwtService,
) : JwtService<User> {
    override lateinit var secret: ByteArray

    @PostConstruct
    fun postConstruct() {
        secret = Base64.getDecoder().decode(webProperties.jwtSecret)
    }

    override fun issueToken(
        claims: User,
        expireAtNumberOfSeconds: Int
    ): String {
        val mapClaims = buildMap {
            put("id", claims.id!!)
            claims.email?.let { put("email", it) }
            claims.oauthId?.let { put("oauthId", it) }
            put("username", claims.username)
            put("systemRoles", claims.systemRoles)
            put("provider", claims.provider)
            put("isVerified", claims.isVerified)
        }

        return defaultJwtService.issueToken(mapClaims, expireAtNumberOfSeconds)
    }

    override fun parseToken(token: String): User? {
        val claims = defaultJwtService.parseToken(token) ?: return null
        return User(
            id = UUID.fromString(claims["sub"] as String),
            username = claims["username"] as String,
            password = "(sensitive)",
            email = claims["email"] as String?,
            oauthId = claims["oauthId"] as String?,
            provider = claims["provider"] as String,
            systemRoles = (claims["systemRoles"] as List<*>).map { it.toString() }.toMutableSet(),
            isVerified = claims["isVerified"] as Boolean
        )
    }

    override fun isValidToken(token: String): Boolean {
        return defaultJwtService.isValidToken(token)
    }
}