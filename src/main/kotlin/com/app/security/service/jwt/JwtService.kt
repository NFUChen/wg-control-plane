package com.app.security.service.jwt

import com.app.security.config.WebProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*


interface JwtService<T> {
    val secret: ByteArray
    fun issueToken(claims: T, expireAtNumberOfSeconds: Int): String
    fun parseToken(token: String): T?
    fun isValidToken(token: String): Boolean
}

@Service("Default")
class DefaultJwtService(
    @Value("\${spring.application.name}")
    val issuer: String,
    val webProperties: WebProperties
): JwtService<Map<String, Any>> {

    override lateinit var secret: ByteArray


    @PostConstruct
    fun postConstruct() {
        secret = Base64.getDecoder().decode(webProperties.jwtSecret)
    }

    override fun issueToken(claims: Map<String, Any>, expireAtNumberOfSeconds: Int): String {
        val signedKey = Keys.hmacShaKeyFor(secret)
        val jwtBuilder = Jwts.builder()
            .issuer(issuer)
            .subject(claims.getOrDefault("id", UUID.randomUUID()).toString())
            .issuedAt(Date())
            .signWith(signedKey)

        if (expireAtNumberOfSeconds > 0) {
            val expirationDate = Date(System.currentTimeMillis() + expireAtNumberOfSeconds * 1000L)
            jwtBuilder.expiration(expirationDate)
        }

        // Add all claims to the token
        claims.forEach { (key, value) ->
            jwtBuilder.claim(key, value)
        }

        return jwtBuilder.compact()
    }

    override fun parseToken(token: String): Map<String, Any>? {
        val secretKey = Keys.hmacShaKeyFor(secret)
        val claims: Claims
        try {
            claims =  Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload
        } catch (e: JwtException) {
            return null
        }
        return claims.mapValues { it.value }
    }

    override fun isValidToken(token: String): Boolean {
        val secretKey = Keys.hmacShaKeyFor(secret)
        return try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload
            true
        } catch (e: JwtException) {
            false
        }
    }

}