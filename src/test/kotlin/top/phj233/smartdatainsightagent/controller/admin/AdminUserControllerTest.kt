package top.phj233.smartdatainsightagent.controller.admin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import top.phj233.smartdatainsightagent.entity.dto.AdminUserCreateInput
import top.phj233.smartdatainsightagent.entity.dto.AdminUserUpdateInput
import top.phj233.smartdatainsightagent.entity.dto.UserMeResponse
import top.phj233.smartdatainsightagent.service.admin.AdminUserService

class AdminUserControllerTest {

    private val adminUserService = mock(AdminUserService::class.java)
    private val controller = AdminUserController(adminUserService)

    @Test
    fun `list endpoint delegates pagination to service`() {
        val expectedUser = userResponse()
        val expected = PageImpl(listOf(expectedUser))
        `when`(adminUserService.list(PageRequest.of(1, 5))).thenReturn(expected)

        val result = controller.list(1, 5)

        assertSame(expected, result)
        verify(adminUserService).list(PageRequest.of(1, 5))
    }

    @Test
    fun `detail endpoint delegates user id to service`() {
        val expected = userResponse()
        `when`(adminUserService.detail(1L)).thenReturn(expected)

        val result = controller.detail(1L)

        assertEquals(expected, result)
        verify(adminUserService).detail(1L)
    }

    @Test
    fun `create endpoint delegates request body to service`() {
        val input = createInput()
        val expected = userResponse()
        `when`(adminUserService.create(input)).thenReturn(expected)

        val result = controller.create(input)

        assertEquals(expected, result)
        verify(adminUserService).create(input)
    }

    @Test
    fun `update endpoint delegates to admin user service`() {
        val input = updateInput()
        val expected = userResponse()
        `when`(adminUserService.update(1L, input)).thenReturn(expected)

        val result = controller.update(1L, input)

        assertEquals(expected, result)
        verify(adminUserService).update(1L, input)
    }

    @Test
    fun `delete endpoint delegates user id to service`() {
        controller.delete(1L)

        verify(adminUserService).delete(1L)
    }

    private fun createInput(): AdminUserCreateInput {
        return AdminUserCreateInput(
            username = "new_admin_user",
            password = "new_password_123",
            email = "new@example.com",
            avatar = null,
            enabled = true,
            roles = listOf(AdminUserCreateInput.TargetOf_roles(2L))
        )
    }

    private fun updateInput(): AdminUserUpdateInput {
        return AdminUserUpdateInput(
            username = "new_admin_user",
            password = null,
            email = "new@example.com",
            avatar = null,
            enabled = true,
            roles = listOf(AdminUserUpdateInput.TargetOf_roles(2L))
        )
    }

    private fun userResponse(): UserMeResponse {
        return UserMeResponse(
            id = 1L,
            username = "new_admin_user",
            email = "new@example.com",
            avatar = null,
            createdTimeStamp = 1L,
            modifiedTimeStamp = 2L,
            enabled = true,
            roles = listOf("ADMIN")
        )
    }
}
