package top.phj233.smartdatainsightagent.exception

import cn.dev33.satoken.exception.NotLoginException
import jakarta.servlet.http.HttpServletRequest
import org.babyfish.jimmer.error.CodeBasedRuntimeException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * 统一处理全局异常，避免 Sa-Token 未登录异常直接打出服务端错误栈。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class GlobalExceptionHandler {

    @ExceptionHandler(BindException::class)
    fun handleValidationException(ex: BindException, request: HttpServletRequest): ResponseEntity<ValidationErrorResponse> {
        val timestamp = Instant.now().toEpochMilli()
        val details = ex.bindingResult.fieldErrors.map {
            ValidationErrorDetail(
                field = it.field,
                message = it.defaultMessage ?: "参数校验失败"
            )
        }
        val message = details.firstOrNull()?.message ?: "请求参数校验失败"
        val body = ValidationErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            code = "VALIDATION_FAILED",
            message = message,
            path = request.requestURI,
            timestamp = timestamp,
            details = details
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(NotLoginException::class)
    fun handleNotLogin(ex: NotLoginException, request: HttpServletRequest): ResponseEntity<Any> {
        val timestamp = Instant.now().toEpochMilli()
        if (isSseRequest(request)) {
            val sseBody = "event: error\ndata: {\"code\":\"AUTH_NOT_LOGIN\",\"message\":\"${escapeJson(ex.message ?: "未登录或登录已过期")}\",\"timestamp\":$timestamp}\n\n"
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
            timestamp = timestamp
        )
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body)
    }

    @ExceptionHandler(CodeBasedRuntimeException::class)
    fun handleCodeBasedException(
        ex: CodeBasedRuntimeException,
        request: HttpServletRequest
    ): ResponseEntity<CommonErrorResponse> {
        val status = HttpStatus.BAD_REQUEST
        val body = CommonErrorResponse(
            status = status.value(),
            error = status.reasonPhrase,
            code = ex.javaClass.simpleName.ifBlank { "BUSINESS_ERROR" },
            message = resolveMessage(ex, status),
            path = request.requestURI,
            timestamp = Instant.now().toEpochMilli()
        )
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleAnyException(ex: Exception, request: HttpServletRequest): ResponseEntity<CommonErrorResponse> {
        val status = resolveStatus(ex)
        val body = CommonErrorResponse(
            status = status.value(),
            error = status.reasonPhrase,
            code = resolveCode(status),
            message = resolveMessage(ex, status),
            path = request.requestURI,
            timestamp = Instant.now().toEpochMilli()
        )
        return ResponseEntity.status(status).body(body)
    }

    /**
     * 根据异常类型解析出合适的 HTTP 状态码，默认是 500 内部服务器错误。
     */
    private fun resolveStatus(ex: Exception): HttpStatus {
        return when (ex) {
            is ResponseStatusException -> HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
            is IllegalArgumentException -> HttpStatus.BAD_REQUEST
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
    }

    /**
     * 根据 HTTP 状态码解析出错误代码，4xx 是请求错误，5xx 是服务器错误。
     */
    private fun resolveCode(status: HttpStatus): String {
        return if (status.is4xxClientError) "REQUEST_ERROR" else "INTERNAL_ERROR"
    }

    /**
     * 解析异常消息
     * - 优先使用 ResponseStatusException 的 reason 字段
     * - 其次使用异常的 message 字段
     * - 都没有则根据状态码返回默认消息。
     */

    private fun resolveMessage(ex: Exception, status: HttpStatus): String {
        if (ex is ResponseStatusException) {
            val reason = ex.reason?.trim()
            if (!reason.isNullOrEmpty()) {
                return reason
            }
        }
        val raw = ex.message?.trim()
        if (!raw.isNullOrEmpty()) {
            return raw
        }
        return if (status.is4xxClientError) "请求处理失败" else "系统内部错误"
    }

    private fun isSseRequest(request: HttpServletRequest): Boolean {
        val accept = request.getHeader(HttpHeaders.ACCEPT)?.lowercase() ?: ""
        return accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE) || request.requestURI.endsWith("/events")
    }

    // 简单的 JSON 转义，避免在 SSE 中输出不合法的 JSON 字符串。
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
    val timestamp: Long
)

data class ValidationErrorResponse(
    val status: Int,
    val error: String,
    val code: String,
    val message: String,
    val path: String,
    val timestamp: Long,
    val details: List<ValidationErrorDetail>
)

data class ValidationErrorDetail(
    val field: String,
    val message: String
)

data class CommonErrorResponse(
    val status: Int,
    val error: String,
    val code: String,
    val message: String,
    val path: String,
    val timestamp: Long
)

