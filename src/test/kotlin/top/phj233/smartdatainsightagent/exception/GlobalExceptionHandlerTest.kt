package top.phj233.smartdatainsightagent.exception

import cn.dev33.satoken.exception.NotLoginException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest

class GlobalExceptionHandlerTest {

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
        assertTrue(Regex("\\\"timestamp\\\":\\d+").containsMatchIn(body))
    }
}

