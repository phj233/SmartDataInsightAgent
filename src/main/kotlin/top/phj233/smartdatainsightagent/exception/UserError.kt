package top.phj233.smartdatainsightagent.exception

import org.babyfish.jimmer.error.ErrorFamily

/**
 * 用户相关的错误枚举类
 * - USER_NOT_FOUND: 用户未找到，表示请求的用户不存在。
 * - USERNAME_NOT_AVAILABLE: 用户名不可用，表示请求的用户名已经被占用。
 * - INVALID_CREDENTIALS: 无效的凭据，表示提供的用户名或密码不正确。
 * - USER_ALREADY_EXISTS: 用户已存在，表示请求创建的用户已经存在。
 * - PERMISSION_DENIED: 权限被拒绝，表示当前用户没有执行请求的权限。
 * @author phj233
 * @since 2026/2/26 20:13
 * @version
 */
@ErrorFamily
enum class UserError {
    USER_NOT_FOUND,
    USERNAME_NOT_AVAILABLE,
    INVALID_CREDENTIALS,
    USER_ALREADY_EXISTS,
    PERMISSION_DENIED
}
