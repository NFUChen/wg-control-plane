package com.app.security.service

import com.app.security.service.redis.RedisRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface TokenExchangeService {
    fun putToken(userToken: String): String
    fun getToken(exchangeToken: String): String?
}


@Service
@Profile("memory")
class InMemoryTokenExchangeService(
    private val tokenGenerator: TokenGenerator
) : TokenExchangeService {
    private val tokenStore = ConcurrentHashMap<String, String>()

    override fun putToken(userToken: String): String {
        val exchangeToken = tokenGenerator.generateToken()
        tokenStore[exchangeToken] = userToken
        return exchangeToken
    }

    override fun getToken(exchangeToken: String): String? {
        return tokenStore.remove(exchangeToken)
    }
}

@Service
@Profile("!memory")
class RedisTokenExchangeService(
    private val tokenGenerator: TokenGenerator,
    private val redisRepository: RedisRepository,
) : TokenExchangeService {


    companion object {
        private const val TOKEN_PREFIX = "exchange"
        private const val TOKEN_TTL_MINUTES = 5L
    }


    override fun putToken(userToken: String): String {
        val exchangeToken = tokenGenerator.generateToken()
        val key = redisRepository.withPrefix(TOKEN_PREFIX, exchangeToken)
        redisRepository.setWithTtl(key, userToken, TOKEN_TTL_MINUTES, TimeUnit.SECONDS)
        return exchangeToken
    }

    override fun getToken(exchangeToken: String): String? {
        val key = redisRepository.withPrefix(TOKEN_PREFIX, exchangeToken)
        return redisRepository.getAndDelete(key)
    }
}