package top.phj233.smartdatainsightagent.service.admin

import cn.dev33.satoken.secure.BCrypt
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.dto.AdminUserCreateInput
import top.phj233.smartdatainsightagent.entity.dto.AdminUserUpdateInput
import top.phj233.smartdatainsightagent.entity.dto.UserMeResponse
import top.phj233.smartdatainsightagent.exception.UserException
import top.phj233.smartdatainsightagent.repository.UserRepository

/**
 * 管理员用户服务，负责后台用户的分页查询、详情查看、创建、更新与删除。
 *
 * @author phj233
 * @since 2026/4/24
 */
@Service
class AdminUserService(
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(AdminUserService::class.java)

    /**
     * 分页查询用户列表。
     *
     * @param pageable 分页参数
     * @return 分页用户详情列表
     */
    fun list(pageable: Pageable): Page<UserMeResponse> {
        logger.info("[管理员用户服务] 分页查询用户，page={}, size={}", pageable.pageNumber, pageable.pageSize)
        return userRepository.findAll(pageable).map { user ->
            toMeResponse(user.id)
        }
    }

    /**
     * 查询指定用户详情。
     *
     * @param userId 用户 ID
     * @return 用户详情视图
     */
    fun detail(userId: Long): UserMeResponse {
        logger.info("[管理员用户服务] 查询用户详情，userId={}", userId)
        return toMeResponse(userId)
    }

    /**
     * 创建用户。
     *
     * @param input 管理员创建用户输入
     * @return 创建后的用户详情
     */
    fun create(input: AdminUserCreateInput): UserMeResponse {
        logger.info("[管理员用户服务] 创建用户，username={}, email={}", input.username, input.email)
        val username = input.username.trim()
        val email = input.email.trim()
        userRepository.findUserByUsername(username)?.let {
            throw UserException.userAlreadyExists("用户名已存在")
        }
        userRepository.findUserByEmail(email)?.let {
            throw UserException.userAlreadyExists("邮箱已存在")
        }

        val created = userRepository.save(
            input.copy(
                password = BCrypt.hashpw(input.password)
            ),
            SaveMode.INSERT_ONLY,
            AssociatedSaveMode.APPEND,
            null
        )
        return toMeResponse(created.id)
    }

    /**
     * 更新用户。
     *
     * @param userId 用户 ID
     * @param input 管理员更新用户输入
     * @return 更新后的用户详情
     */
    fun update(userId: Long, input: AdminUserUpdateInput): UserMeResponse {
        logger.info("[管理员用户服务] 更新用户，userId={}", userId)
        userRepository.findNullable(userId)
            ?: throw UserException.userNotFound("用户不存在: $userId")

        val username = input.username?.trim()
        val email = input.email?.trim()
        username?.let {
            val matched = userRepository.findUserByUsername(it)
            if (matched != null && matched.id != userId) {
                throw UserException.userAlreadyExists("用户名已存在")
            }
        }
        email?.let {
            val matched = userRepository.findUserByEmail(it)
            if (matched != null && matched.id != userId) {
                throw UserException.userAlreadyExists("邮箱已存在")
            }
        }
        userRepository.save(
            input.password?.let {
                input.copy(password = BCrypt.hashpw(it))
            } ?: input,
            SaveMode.UPDATE_ONLY,
            AssociatedSaveMode.REPLACE,
            null
        )
        return toMeResponse(userId)
    }

    /**
     * 删除用户。
     *
     * @param userId 用户 ID
     */
    fun delete(userId: Long) {
        logger.info("[管理员用户服务] 删除用户，userId={}", userId)
        if (!userRepository.existsById(userId)) {
            throw UserException.userNotFound("用户不存在: $userId")
        }
        userRepository.deleteById(userId)
    }

    /**
     * 将用户实体转换为当前接口使用的详情视图。
     *
     * @param userId 用户 ID
     * @return 用户详情视图
     */
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
}
