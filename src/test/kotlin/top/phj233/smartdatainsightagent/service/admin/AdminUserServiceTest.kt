package top.phj233.smartdatainsightagent.service.admin

import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import top.phj233.smartdatainsightagent.entity.UserDraft
import top.phj233.smartdatainsightagent.entity.addBy
import top.phj233.smartdatainsightagent.entity.dto.AdminUserCreateInput
import top.phj233.smartdatainsightagent.entity.dto.AdminUserUpdateInput
import top.phj233.smartdatainsightagent.exception.UserException
import top.phj233.smartdatainsightagent.repository.UserRepository

class AdminUserServiceTest {

    private val userRepository = mock(UserRepository::class.java) { invocation: InvocationOnMock ->
        when (val argument = invocation.arguments.firstOrNull()) {
            is AdminUserCreateInput -> userEntity(
                id = 3L,
                username = argument.username,
                email = argument.email,
                avatar = argument.avatar,
                enabled = argument.enabled
            )
            is AdminUserUpdateInput -> userEntity(
                id = 1L,
                username = argument.username ?: "updated",
                email = argument.email ?: "updated@example.com",
                avatar = argument.avatar,
                enabled = argument.enabled ?: true
            )
            else -> Answers.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val service = AdminUserService(userRepository)

    @Test
    fun `list should map paged users to me responses`() {
        val pageable = PageRequest.of(0, 2)
        `when`(userRepository.findAll(pageable)).thenReturn(
            PageImpl(
                listOf(
                    userEntity(1L, "alice", "alice@example.com", roles = listOf("ADMIN")),
                    userEntity(2L, "bob", "bob@example.com", roles = listOf("USER"))
                ),
                pageable,
                2
            )
        )
        `when`(userRepository.findMeById(1L)).thenReturn(userEntity(1L, "alice", "alice@example.com", roles = listOf("ADMIN")))
        `when`(userRepository.findMeById(2L)).thenReturn(userEntity(2L, "bob", "bob@example.com", roles = listOf("USER")))

        val result = service.list(pageable)

        assertEquals(2, result.totalElements)
        assertEquals(listOf("alice", "bob"), result.content.map { it.username })
        assertEquals(listOf(listOf("ADMIN"), listOf("USER")), result.content.map { it.roles })
    }

    @Test
    fun `detail should return me response for target user`() {
        `when`(userRepository.findMeById(1L)).thenReturn(userEntity(1L, "alice", "alice@example.com", roles = listOf("ADMIN")))

        val result = service.detail(1L)

        assertEquals(1L, result.id)
        assertEquals("alice", result.username)
        assertEquals(listOf("ADMIN"), result.roles)
    }

    @Test
    fun `create should hash password before save`() {
        val input = AdminUserCreateInput(
            username = "new_admin",
            password = "new_password_123",
            email = "new@example.com",
            avatar = "avatar.png",
            enabled = true,
            roles = listOf(AdminUserCreateInput.TargetOf_roles(2L))
        )
        val created = userEntity(3L, "new_admin", "new@example.com", avatar = "avatar.png", roles = listOf("ADMIN"))
        `when`(userRepository.findUserByUsername("new_admin")).thenReturn(null)
        `when`(userRepository.findUserByEmail("new@example.com")).thenReturn(null)
        `when`(userRepository.findMeById(3L)).thenReturn(created)

        val result = service.create(input)

        assertEquals(3L, result.id)
        assertEquals("new_admin", result.username)
        assertEquals("new@example.com", result.email)
        assertEquals(listOf("ADMIN"), result.roles)

        val saveInvocation = mockingDetails(userRepository).invocations.first { invocation ->
            invocation.method.name == "save" &&
                invocation.arguments.size == 4 &&
                invocation.arguments[1] == SaveMode.INSERT_ONLY &&
                invocation.arguments[2] == AssociatedSaveMode.APPEND
        }
        val saved = saveInvocation.arguments[0] as AdminUserCreateInput
        assertEquals("new_admin", saved.username)
        assertEquals("new@example.com", saved.email)
        assertEquals("avatar.png", saved.avatar)
        assertEquals(true, saved.enabled)
        assertEquals(listOf(AdminUserCreateInput.TargetOf_roles(2L)), saved.roles)
        assertNotEquals("new_password_123", saved.password)
    }

    @Test
    fun `create should reject duplicate username`() {
        val input = AdminUserCreateInput(
            username = "new_admin",
            password = "new_password_123",
            email = "new@example.com",
            avatar = null,
            enabled = true,
            roles = listOf(AdminUserCreateInput.TargetOf_roles(2L))
        )
        `when`(userRepository.findUserByUsername("new_admin")).thenReturn(userEntity(8L, "new_admin", "old@example.com"))

        assertThrows(UserException::class.java) {
            service.create(input)
        }
    }

    @Test
    fun `update should hash password before save`() {
        val existing = userEntity(1L, "old_name", "old@example.com", avatar = "old-avatar", roles = listOf("USER"))
        val updatedMe = userEntity(1L, "new_name", "new@example.com", avatar = "new-avatar", enabled = false, roles = listOf("ADMIN"))
        val input = AdminUserUpdateInput(
            username = "new_name",
            password = "new_password_123",
            email = "new@example.com",
            avatar = "new-avatar",
            enabled = false,
            roles = listOf(AdminUserUpdateInput.TargetOf_roles(2L))
        )

        `when`(userRepository.findNullable(1L)).thenReturn(existing)
        `when`(userRepository.findUserByUsername("new_name")).thenReturn(null)
        `when`(userRepository.findUserByEmail("new@example.com")).thenReturn(null)
        `when`(userRepository.findMeById(1L)).thenReturn(updatedMe)

        val result = service.update(1L, input)

        assertEquals("new_name", result.username)
        assertEquals("new@example.com", result.email)
        assertEquals("new-avatar", result.avatar)
        assertFalse(result.enabled)
        assertEquals(listOf("ADMIN"), result.roles)

        val saveInvocation = mockingDetails(userRepository).invocations.first { invocation ->
            invocation.method.name == "save" &&
                invocation.arguments.size == 4 &&
                invocation.arguments[1] == SaveMode.UPDATE_ONLY &&
                invocation.arguments[2] == AssociatedSaveMode.REPLACE
        }
        val saved = saveInvocation.arguments[0] as AdminUserUpdateInput
        assertEquals("new_name", saved.username)
        assertEquals("new@example.com", saved.email)
        assertEquals("new-avatar", saved.avatar)
        assertEquals(false, saved.enabled)
        assertEquals(listOf(AdminUserUpdateInput.TargetOf_roles(2L)), saved.roles)
        assertNotEquals("new_password_123", saved.password)
    }

    @Test
    fun `update should reject duplicate email`() {
        `when`(userRepository.findNullable(1L)).thenReturn(userEntity(1L, "alice", "alice@example.com"))
        `when`(userRepository.findUserByEmail("dup@example.com")).thenReturn(userEntity(2L, "bob", "dup@example.com"))

        assertThrows(UserException::class.java) {
            service.update(
                1L,
                AdminUserUpdateInput(
                    username = null,
                    password = null,
                    email = "dup@example.com",
                    avatar = null,
                    enabled = null,
                    roles = listOf(AdminUserUpdateInput.TargetOf_roles(2L))
                )
            )
        }
    }

    @Test
    fun `delete should remove existing user`() {
        `when`(userRepository.existsById(1L)).thenReturn(true)

        service.delete(1L)

        verify(userRepository).deleteById(1L)
    }

    private fun userEntity(
        id: Long,
        username: String,
        email: String,
        avatar: String? = null,
        enabled: Boolean = true,
        roles: List<String> = listOf("USER")
    ) = UserDraft.`$`.produce {
        this.id = id
        this.username = username
        password = "hashed-password"
        this.email = email
        this.avatar = avatar
        this.enabled = enabled
        roles.forEachIndexed { index, roleName ->
            roles().addBy {
                this.id = index + 1L
                name = roleName
            }
        }
    }
}
