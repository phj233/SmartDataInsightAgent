package top.phj233.smartdatainsightagent.controller.admin

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import top.phj233.smartdatainsightagent.entity.Role
import top.phj233.smartdatainsightagent.entity.dto.RoleCreate
import top.phj233.smartdatainsightagent.entity.dto.RoleUpdate
import top.phj233.smartdatainsightagent.service.admin.AdminRoleService

class AdminRoleControllerTest {

    private val adminRoleService = mock(AdminRoleService::class.java)
    private val controller = AdminRoleController(adminRoleService)

    @Test
    fun `list endpoint delegates pagination to service`() {
        val expected = PageImpl(listOf(mock(Role::class.java)))
        `when`(adminRoleService.list(PageRequest.of(0, 20))).thenReturn(expected)

        val result = controller.list(0, 20)

        assertSame(expected, result)
        verify(adminRoleService).list(PageRequest.of(0, 20))
    }

    @Test
    fun `detail endpoint delegates role id to service`() {
        val expected = mock(Role::class.java)
        `when`(adminRoleService.detail(2L)).thenReturn(expected)

        val result = controller.detail(2L)

        assertSame(expected, result)
        verify(adminRoleService).detail(2L)
    }

    @Test
    fun `create endpoint delegates request body to service`() {
        val input = RoleCreate(name = "ADMIN")
        val expected = mock(Role::class.java)
        `when`(adminRoleService.create(input)).thenReturn(expected)

        val result = controller.create(input)

        assertSame(expected, result)
        verify(adminRoleService).create(input)
    }

    @Test
    fun `update endpoint forwards path id and body`() {
        val input = RoleUpdate(name = "ADMIN")
        val expected = mock(Role::class.java)
        `when`(adminRoleService.update(3L, input)).thenReturn(expected)

        val result = controller.update(3L, input)

        assertSame(expected, result)
        verify(adminRoleService).update(3L, input)
    }

    @Test
    fun `delete endpoint delegates role id to service`() {
        controller.delete(3L)

        verify(adminRoleService).delete(3L)
    }
}
