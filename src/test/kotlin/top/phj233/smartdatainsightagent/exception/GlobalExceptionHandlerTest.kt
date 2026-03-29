package top.phj233.smartdatainsightagent.exception

import cn.dev33.satoken.exception.NotLoginException
import cn.dev33.satoken.exception.NotPermissionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest

class GlobalExceptionHandlerTest {

    @Test
    fun `map not login exception to 401 response`() {
        val handler = GlobalExceptionHandler()
        val request = MockHttpServletRequest("GET", "/api/user/me")
        val exception = mock(NotLoginException::class.java)

        val response = handler.handleNotLogin(exception, request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals(401, body!!.status)
        assertEquals("AUTH_NOT_LOGIN", body.code)
        assertEquals("/api/user/me", body.path)
    }

    @Test
    fun `map not permission exception to 403 response`() {
        val handler = GlobalExceptionHandler()
        val request = MockHttpServletRequest("GET", "/api/data-sources/1")
        val exception = mock(NotPermissionException::class.java)

        val response = handler.handleNotPermission(exception, request)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals(403, body!!.status)
        assertEquals("AUTH_NOT_PERMISSION", body.code)
        assertEquals("/api/data-sources/1", body.path)
    }

    @Test
    fun `map unexpected exception to 500 response`() {
        val handler = GlobalExceptionHandler()
        val request = MockHttpServletRequest("POST", "/api/analysis/tasks")
        val exception = RuntimeException("boom")

        val response = handler.handleException(exception, request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals(500, body!!.status)
        assertEquals("INTERNAL_SERVER_ERROR", body.code)
        assertEquals("/api/analysis/tasks", body.path)
    }
}

