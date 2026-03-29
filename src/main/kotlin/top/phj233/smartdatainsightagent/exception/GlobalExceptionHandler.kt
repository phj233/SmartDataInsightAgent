package top.phj233.smartdatainsightagent.exception

import cn.dev33.satoken.exception.NotLoginException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/**
 * 统一处理全局异常，避免 Sa-Token 未登录异常直接打出服务端错误栈。
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException::class)
    fun handleNotLogin(ex: NotLoginException, request: HttpServletRequest): ResponseEntity<Any> {
        if (isSseRequest(request)) {
            val sseBody = "event: error\ndata: {\"code\":\"AUTH_NOT_LOGIN\",\"message\":\"${escapeJson(ex.message ?: "未登录或登录已过期")}\"}\n\n"
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(sseBody)
        }

        val body = AuthErrorResponse(
            status = HttpStatus.UNAUTHORIZED.value(),
            error = HttpStatus.UNAUTHORIZED.reasonPhrase,
            code = "AUTH_NOT_LOGIN",
            message = ex.message ?: "未登录或登录已过期",
            path = request.requestURI,
            timestamp = Instant.now()
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body)
    }

    private fun isSseRequest(request: HttpServletRequest): Boolean {
        val accept = request.getHeader(HttpHeaders.ACCEPT)?.lowercase() ?: ""
        return accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE) || request.requestURI.endsWith("/events")
    }

    private fun escapeJson(raw: String): String {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}

data class AuthErrorResponse(
    val status: Int,
    val error: String,
    val code: String,
    val message: String,
    val path: String,
    val timestamp: Instant
)

