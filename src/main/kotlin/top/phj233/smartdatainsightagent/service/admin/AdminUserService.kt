package top.phj233.smartdatainsightagent.service.admin

import cn.dev33.satoken.secure.BCrypt
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.copy
import top.phj233.smartdatainsightagent.entity.dto.AdminUserCreateInput
import top.phj233.smartdatainsightagent.entity.dto.AdminUserUpdateInput
import top.phj233.smartdatainsightagent.entity.dto.UserMeResponse
import top.phj233.smartdatainsightagent.exception.UserException
import top.phj233.smartdatainsightagent.repository.RoleRepository
import top.phj233.smartdatainsightagent.repository.UserRepository

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository
) {
    private val logger = LoggerFactory.getLogger(AdminUserService::class.java)

    fun list(pageable: Pageable): Page<UserMeResponse> {
        logger.info("[管理员用户服务] 分页查询用户, page={}, size={}", pageable.pageNumber, pageable.pageSize)
        return userRepository.findAll(pageable).map { user ->
            toMeResponse(user.id)
        }
    }

    fun detail(userId: Long): UserMeResponse {
        logger.info("[管理员用户服务] 查询用户详情, userId={}", userId)
        return toMeResponse(userId)
    }

    fun create(input: AdminUserCreateInput): UserMeResponse {
        logger.info("[管理员用户服务] 创建用户, username={}, email={}", input.username, input.email)
        userRepository.findUserByUsername(input.username.trim())?.let {
            throw UserException.userAlreadyExists("用户名已存在")
        }
        userRepository.findUserByEmail(input.email.trim())?.let {
            throw UserException.userAlreadyExists("邮箱已存在")
        }
        validateRoleIds(input.roleIds)

        val created = userRepository.save(
            input.copy(
                password = BCrypt.hashpw(input.password),
            ),
            SaveMode.INSERT_ONLY,
            AssociatedSaveMode.APPEND
        )
        return toMeResponse(created.id)
    }

    fun update(userId: Long, input: AdminUserUpdateInput): UserMeResponse {
        logger.info("[管理员用户服务] 更新用户, userId={}", userId)
        val existing = userRepository.findNullable(userId)
            ?: throw UserException.userNotFound("用户不存在: $userId")

        input.username?.trim()?.let { username ->
            val matched = userRepository.findUserByUsername(username)
            if (matched != null && matched.id != userId) {
                throw UserException.userAlreadyExists("用户名已存在")
            }
        }
        input.email?.trim()?.let { email ->
            val matched = userRepository.findUserByEmail(email)
            if (matched != null && matched.id != userId) {
                throw UserException.userAlreadyExists("邮箱已存在")
            }
        }

        val roleIds = input.roleIds?.let { normalizeRoleIds(it) }
        roleIds?.let(::validateRoleIds)

        val updated = existing.copy {
            input.password?.let { password = BCrypt.hashpw(it) }
        }
        userRepository.save(updated, SaveMode.UPDATE_ONLY, AssociatedSaveMode.REPLACE, null)
        return toMeResponse(userId)
    }

    fun delete(userId: Long) {
        logger.info("[管理员用户服务] 删除用户, userId={}", userId)
        if (!userRepository.existsById(userId)) {
            throw UserException.userNotFound("用户不存在: $userId")
        }
        userRepository.deleteById(userId)
    }

    private fun toMeResponse(userId: Long): UserMeResponse {
        val user = userRepository.findMeById(userId)
            ?: throw UserException.userNotFound("用户不存在: $userId")
        return UserMeResponse(
            id = user.id,
            username = user.username,
            email = user.email,
            avatar = user.avatar,
            enabled = user.enabled,
            roles = user.roles.map { it.name }
        )
    }

    private fun normalizeRoleIds(raw: List<Long>?): List<Long> {
        return raw.orEmpty().distinct().filter { it > 0 }
    }

    private fun validateRoleIds(roleIds: List<Long>) {
        if (roleIds.isEmpty()) {
            return
        }
        val existingRoleIds = roleRepository.findByIds(roleIds).map { it.id }.toSet()
        val invalidRoleIds = roleIds.filterNot { it in existingRoleIds }
        if (invalidRoleIds.isNotEmpty()) {
            logger.warn("[管理员用户服务] 角色不存在, roleIds={}", invalidRoleIds)
            throw UserException.permissionDenied("角色不存在: $invalidRoleIds")
        }
    }
}
