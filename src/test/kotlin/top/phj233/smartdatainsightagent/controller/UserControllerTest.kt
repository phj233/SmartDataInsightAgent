package top.phj233.smartdatainsightagent.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import top.phj233.smartdatainsightagent.entity.dto.UserMeResponse
import top.phj233.smartdatainsightagent.entity.dto.UserUpdateProfileDTO
import top.phj233.smartdatainsightagent.service.RedisService
import top.phj233.smartdatainsightagent.service.UserService

class UserControllerTest {

    private val userService = mock(UserService::class.java)
    private val redisService = mock(RedisService::class.java)
    private val controller = UserController(userService, redisService)

    @Test
    fun `update me endpoint delegates to user service`() {
        val input = UserUpdateProfileDTO(
            username = "new_name",
            password = null,
            email = null,
            avatar = null,
            code = null
        )
        val expected = UserMeResponse(
            id = 1L,
            username = "new_name",
            email = "user@example.com",
            avatar = null,
            enabled = true,
            roles = listOf("USER")
        )
        `when`(userService.updateCurrentUser(input)).thenReturn(expected)

        val result = controller.updateMe(input)

        assertEquals(expected, result)
        verify(userService).updateCurrentUser(input)
    }
}

