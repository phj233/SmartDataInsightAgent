package top.phj233.smartdatainsightagent.exception

import cn.dev33.satoken.exception.NotLoginException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.web.server.ResponseStatusException

class GlobalExceptionHandlerTest {

    @Test
    fun `map bind exception to 400 response with field details`() {
        val handler = GlobalExceptionHandler()
        val request = MockHttpServletRequest("POST", "/api/user/register")
        val bindingResult = BeanPropertyBindingResult(Any(), "userRegisterDTO")
        bindingResult.addError(
            FieldError(
                "userRegisterDTO",
                "username",
                "test",
                false,
                null,
                null,
                "用户名长度必须在6-32之间"
            )
        )
        val exception = BindException(bindingResult)

        val response = handler.handleValidationException(exception, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals(400, body!!.status)
        assertEquals("VALIDATION_FAILED", body.code)
        assertEquals("用户名长度必须在6-32之间", body.message)
        assertEquals("/api/user/register", body.path)
        assertEquals(1, body.details.size)
        assertEquals("username", body.details[0].field)
        assertEquals("用户名长度必须在6-32之间", body.details[0].message)
    }

    @Test
    fun `map not login exception to 401 response`() {
        val handler = GlobalExceptionHandler()
        val request = MockHttpServletRequest("GET", "/api/user/me")
        val exception = mock(NotLoginException::class.java)

        val response = handler.handleNotLogin(exception, request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        val body = response.body as AuthErrorResponse
        assertNotNull(body)
        assertEquals(401, body.status)
        assertEquals("AUTH_NOT_LOGIN", body.code)
        assertEquals("/api/user/me", body.path)
        assertTrue(body.timestamp > 0)
    }

    @Test
    fun `map not login exception to sse unauthorized response with numeric timestamp`() {
        val handler = GlobalExceptionHandler()
        val request = MockHttpServletRequest("GET", "/api/analysis/tasks/1/events")
        request.addHeader("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
        val exception = mock(NotLoginException::class.java)

        val response = handler.handleNotLogin(exception, request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(MediaType.TEXT_EVENT_STREAM, response.headers.contentType)
        val body = response.body as String
        assertNotNull(body)
        assertTrue(body.contains("event: error"))
        assertTrue(Regex("\"timestamp\":\\d+").containsMatchIn(body))
    }

    @Test
    fun `map generic exception to response with non-empty message`() {
        val handler = GlobalExceptionHandler()
        val request = MockHttpServletRequest("GET", "/api/test")
        val exception = RuntimeException()

        val response = handler.handleAnyException(exception, request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertTrue(body!!.message.isNotBlank())
    }

    @Test
    fun `map response status exception message as response message`() {
        val handler = GlobalExceptionHandler()
        val request = MockHttpServletRequest("GET", "/api/test")
        val exception = ResponseStatusException(HttpStatus.BAD_REQUEST, "bad request")

        val response = handler.handleAnyException(exception, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals("bad request", body!!.message)
    }

    @Test
    fun `map code based runtime exception to 400 with message`() {
        val handler = GlobalExceptionHandler()
        val request = MockHttpServletRequest("POST", "/api/user/register")
        val exception = UserException.invalidCredentials("验证码错误或已过期")

        val response = handler.handleCodeBasedException(exception, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals("验证码错误或已过期", body!!.message)
        assertEquals("/api/user/register", body.path)
    }
}

