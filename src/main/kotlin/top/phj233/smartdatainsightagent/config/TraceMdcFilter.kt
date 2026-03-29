package top.phj233.smartdatainsightagent.config

import cn.dev33.satoken.stp.StpUtil
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

/**
 * 为每个请求注入统一 MDC 上下文：traceId/userId。
 * - traceId 优先从请求头 X-Trace-Id 获取，如果没有则生成一个新的 UUID。
 * - userId 从 Sa-Token 的 StpUtil 获取，如果当前未登录则不设置 userId。
 *
 * 这样在日志中就可以使用 %X{traceId} 和 %X{userId} 来输出对应的值，方便日志追踪和分析。
 * @author phj233
 * @since 2026/3/29 17:42
 */
@Component
class TraceMdcFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val traceId = request.getHeader(TRACE_ID_HEADER)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString().replace("-", "")
            MDC.put("traceId", traceId)

            resolveUserIdOrNull()?.let { MDC.put("userId", it.toString()) }
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("traceId")
            MDC.remove("userId")
            MDC.remove("taskId")
        }
    }

    private fun resolveUserIdOrNull(): Long? {
        return try {
            StpUtil.getLoginIdDefaultNull()?.toString()?.toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TRACE_ID_HEADER = "X-Trace-Id"
    }
}

