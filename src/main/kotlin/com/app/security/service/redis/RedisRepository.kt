package com.app.security.service.redis

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

/**
 * 通用 Redis 資料操作介面
 * Common Redis data operations interface
 */
interface RedisRepository {
    /**
     * 設置鍵值對，帶有過期時間
     * Set key-value pair with expiration time
     */
    fun setWithTtl(key: String, value: String, ttl: Long, timeUnit: TimeUnit)

    /**
     * 獲取並刪除鍵值（原子操作，適用於一次性token）
     * Get and delete key-value (atomic operation, suitable for one-time tokens)
     */
    fun getAndDelete(key: String): String?

    /**
     * 獲取鍵值
     * Get key-value
     */
    fun get(key: String): String?

    /**
     * 刪除鍵
     * Delete key
     */
    fun delete(key: String): Boolean

    /**
     * 檢查鍵是否存在
     * Check if key exists
     */
    fun exists(key: String): Boolean

    /**
     * 為鍵添加前綴（用於命名空間隔離）
     * Add prefix to key (for namespace isolation)
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
        // 使用 GETDEL 確保原子性的一次性 token 消費 (Redis 6.2+)
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