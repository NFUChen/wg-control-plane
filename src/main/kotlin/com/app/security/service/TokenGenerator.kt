package com.app.security.service

import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import java.security.SecureRandom

interface TokenGenerator {
    fun generateToken(): String
}

@Primary
@Service
class SecureTokenGenerator: TokenGenerator {
    private val secureRandom = SecureRandom()

    override fun generateToken(): String {
        // 32 bytes = 256 bits of entropy (much stronger than UUID's ~122 bits)
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}