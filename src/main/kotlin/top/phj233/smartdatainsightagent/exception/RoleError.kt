package top.phj233.smartdatainsightagent.exception

import org.babyfish.jimmer.error.ErrorFamily

/**
 * 角色相关错误
 * - ROLE_NOT_FOUND: 角色未找到
 * - ROLE_ALREADY_EXISTS: 角色已存在
 * - INVALID_ROLE_NAME: 无效的角色名称
 * @author phj233
 * @since 2026/4/22 19:43
 * @version
 */
@ErrorFamily
enum class RoleError {
    ROLE_NOT_FOUND, // 角色未找到
    ROLE_ALREADY_EXISTS, // 角色已存在
    INVALID_ROLE_NAME, // 无效的角色名称

}
