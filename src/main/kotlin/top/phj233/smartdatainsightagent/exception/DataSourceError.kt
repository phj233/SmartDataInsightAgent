package top.phj233.smartdatainsightagent.exception

import org.babyfish.jimmer.error.ErrorFamily

/**
 * 数据源相关错误枚举。
 */
@ErrorFamily
enum class DataSourceError {
    DATA_SOURCE_NOT_FOUND,
    DATA_SOURCE_ACCESS_DENIED,
    DATA_SOURCE_NAME_ALREADY_EXISTS,
    INVALID_CONNECTION_CONFIG
}

