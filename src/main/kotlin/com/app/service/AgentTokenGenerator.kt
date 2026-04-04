package com.app.service

import org.springframework.stereotype.Service
import java.math.BigInteger
import java.security.SecureRandom


interface AgentTokenGenerator {
    fun generateToken(prefix: String): String
}


@Service
class DefaultAgentTokenGenerator : AgentTokenGenerator {
    val random = SecureRandom()
    override fun generateToken(prefix: String): String {
        // For simplicity, we use a random UUID as the token. In a real implementation, you might want to use a more secure token generation strategy.
        val token: String = BigInteger(130, random).toString(32) // Generates a random string
        return "$prefix-$token"
    }
}