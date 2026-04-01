package com.app.security.service.jwt

import com.app.security.config.WebProperties
import com.app.security.repository.model.ServiceAccount
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.util.*


@Service("serviceAccountJwtService")
class ServiceAccountJwtService(
    private val webProperties: WebProperties,
    private val defaultJwtService: DefaultJwtService,
): JwtService<ServiceAccount> {
    override lateinit var secret: ByteArray
    @PostConstruct
    fun postConstruct() {
        secret = Base64.getDecoder().decode(webProperties.jwtSecret)
    }

    override fun parseToken(token: String): ServiceAccount? {
        val claims = defaultJwtService.parseToken(token) ?: return null
        return ServiceAccount(
            id = UUID.fromString(claims["id"] as String),
            clientId = claims["clientId"] as String,
            name = claims["name"] as String,
            enabled = claims["enabled"] as Boolean,
            scopes = (claims["scopes"] as List<*>).map { it.toString() }.toMutableSet(),
            clientSecretHash = "(sensitive)",
        )
    }

    override fun isValidToken(token: String): Boolean {
        return defaultJwtService.isValidToken(token)
    }

    override fun issueToken(claims: ServiceAccount, expireAtNumberOfSeconds: Int): String {
        val mapClaims = mutableMapOf(
            "id" to claims.id!!,
            "clientId" to claims.clientId,
            "name" to claims.name,
            "enabled" to claims.enabled,
            "scopes" to claims.scopes,
        )



        return defaultJwtService.issueToken(mapClaims, expireAtNumberOfSeconds)
    }
}