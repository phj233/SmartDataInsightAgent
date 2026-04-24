package top.phj233.smartdatainsightagent.service.admin

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import org.mockito.invocation.InvocationOnMock
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import top.phj233.smartdatainsightagent.entity.Role
import top.phj233.smartdatainsightagent.entity.RoleDraft
import top.phj233.smartdatainsightagent.entity.dto.RoleCreate
import top.phj233.smartdatainsightagent.entity.dto.RoleUpdate
import top.phj233.smartdatainsightagent.exception.RoleException
import top.phj233.smartdatainsightagent.repository.RoleRepository

class AdminRoleServiceTest {

    private val roleRepository = mock(RoleRepository::class.java) { invocation: InvocationOnMock ->
        when (val argument = invocation.arguments.firstOrNull()) {
            is Role -> argument
            is RoleCreate -> role(id = 1L, name = argument.name)
            else -> Answers.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val service = AdminRoleService(roleRepository)

    @Test
    fun `list should delegate to repository`() {
        val pageable = PageRequest.of(0, 10)
        val expected = PageImpl(listOf(role(id = 1L, name = "ADMIN")))
        `when`(roleRepository.findAll(pageable)).thenReturn(expected)

        val result = service.list(pageable)

        assertSame(expected, result)
    }

    @Test
    fun `detail should throw when role does not exist`() {
        `when`(roleRepository.findNullable(1L)).thenReturn(null)

        assertThrows(RoleException::class.java) {
            service.detail(1L)
        }
    }

    @Test
    fun `create should trim name before save`() {
        `when`(roleRepository.findByName("ADMIN")).thenReturn(null)

        val result = service.create(RoleCreate(name = "  ADMIN  "))

        assertEquals(1L, result.id)
        assertEquals("ADMIN", result.name)
    }

    @Test
    fun `create should reject duplicate role name`() {
        `when`(roleRepository.findByName("ADMIN")).thenReturn(role(id = 2L, name = "ADMIN"))

        assertThrows(RoleException::class.java) {
            service.create(RoleCreate(name = " ADMIN "))
        }
    }

    @Test
    fun `update should trim name before save`() {
        `when`(roleRepository.findNullable(1L)).thenReturn(role(id = 1L, name = "USER"))
        `when`(roleRepository.findByName("ADMIN")).thenReturn(null)

        val result = service.update(1L, RoleUpdate(name = "  ADMIN  "))

        assertEquals(1L, result.id)
        assertEquals("ADMIN", result.name)
    }

    @Test
    fun `update should reject duplicate role name owned by another role`() {
        `when`(roleRepository.findNullable(1L)).thenReturn(role(id = 1L, name = "USER"))
        `when`(roleRepository.findByName("ADMIN")).thenReturn(role(id = 2L, name = "ADMIN"))

        assertThrows(RoleException::class.java) {
            service.update(1L, RoleUpdate(name = "ADMIN"))
        }
    }

    @Test
    fun `delete should remove existing role`() {
        `when`(roleRepository.existsById(1L)).thenReturn(true)

        service.delete(1L)

        verify(roleRepository).deleteById(1L)
    }

    private fun role(id: Long, name: String) = RoleDraft.`$`.produce {
        this.id = id
        this.name = name
    }
}
