package com.app.security.service.redis

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

/**
 * Common Redis data operations.
 */
interface RedisRepository {
    /**
     * Set a key with a TTL.
     */
    fun setWithTtl(key: String, value: String, ttl: Long, timeUnit: TimeUnit)

    /**
     * Atomically get and delete a key (one-time tokens).
     */
    fun getAndDelete(key: String): String?

    /**
     * Get a value by key.
     */
    fun get(key: String): String?

    /**
     * Delete a key.
     */
    fun delete(key: String): Boolean

    /**
     * Check whether a key exists.
     */
    fun exists(key: String): Boolean

    /**
     * Prefix a key for namespace isolation.
     */
    fun withPrefix(prefix: String, key: String): String = "$prefix:$key"
}

@Repository
class DefaultRedisRepository(
    private val redisTemplate: RedisTemplate<String, String>
) : RedisRepository {

    override fun setWithTtl(key: String, value: String, ttl: Long, timeUnit: TimeUnit) {
        redisTemplate.opsForValue().set(key, value, ttl, timeUnit)
    }

    override fun getAndDelete(key: String): String? {
        // GETDEL for atomic one-time token consumption (Redis 6.2+)
        return redisTemplate.execute { connection ->
            val result = connection.stringCommands().getDel(key.toByteArray())
            result?.let { String(it) }
        }
    }

    override fun get(key: String): String? {
        return redisTemplate.opsForValue().get(key)
    }

    override fun delete(key: String): Boolean {
        return redisTemplate.delete(key)
    }

    override fun exists(key: String): Boolean {
        return redisTemplate.hasKey(key)
    }
}