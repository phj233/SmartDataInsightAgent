package top.phj233.smartdatainsightagent.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * @author phj233
 * @since 2026/2/26 20:21
 * @version
 */
@Service
class RedisService(val redisTemplate: StringRedisTemplate) {
    /**
     * 在 Redis 生成验证码
     */
    fun generateCode(key: String, code: String, expireTime: Long) {
        redisTemplate.opsForValue().set(key, code, expireTime, TimeUnit.SECONDS)
    }

    /**
     * 获取验证码
     */
    fun getCode(key: String): String? {
        return redisTemplate.opsForValue().get(key)
    }
    /**
     * 删除验证码
     */
    fun deleteCode(key: String): Boolean? {
        return redisTemplate.delete(key)
    }

    /**
     * 验证验证码
     */
    fun verifyCode(key: String, code: String): Boolean {
        val storedCode = getCode(key)
        if (storedCode == code) {
            deleteCode(key)
            return true
        }
        return false
    }
}
