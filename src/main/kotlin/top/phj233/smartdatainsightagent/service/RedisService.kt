package top.phj233.smartdatainsightagent.service

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Redis 服务类
 * @author phj233
 * @since 2026/2/26 20:21
 * @version
 */
@Service
class RedisService(val redisTemplate: StringRedisTemplate) {
    private val logger = LoggerFactory.getLogger(RedisService::class.java)

    /**
     * 在 Redis 生成验证码
     */
    fun generateCode(key: String, code: String, expireTime: Long) {
        logger.info("[Redis] 写入验证码，userId={}, key={}, expire={}s", MDC.get("userId"), key, expireTime)
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
            logger.info("[Redis] 验证码校验成功，userId={}, key={}", MDC.get("userId"), key)
            return true
        }
        logger.warn("[Redis] 验证码校验失败，userId={}, key={}", MDC.get("userId"), key)
        return false
    }

    /**
     * 生成邮箱验证码并设置默认有效期。
     */
    fun generateEmailCode(email: String, code: String, expireTime: Long = DEFAULT_EMAIL_CODE_TTL_SECONDS) {
        generateCode(emailCodeKey(email), code, expireTime)
    }

    /**
     * 验证邮箱验证码，成功后自动删除。
     */
    fun verifyEmailCode(email: String, code: String): Boolean {
        return verifyCode(emailCodeKey(email), code)
    }

    private fun emailCodeKey(email: String): String = "$EMAIL_CODE_PREFIX$email"

    companion object {
        private const val EMAIL_CODE_PREFIX = "verify:code:"
        private const val DEFAULT_EMAIL_CODE_TTL_SECONDS = 300L
    }
}
