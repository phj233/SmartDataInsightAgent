package top.phj233.smartdatainsightagent.exception

import org.babyfish.jimmer.error.ErrorFamily

/**
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
