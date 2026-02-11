package top.phj233.smartdatainsightagent.entity.enums

import org.babyfish.jimmer.sql.EnumType

/**
 * @author phj233
 * @since 2026/1/28 14:56
 * @version
 */
@EnumType(EnumType.Strategy.NAME)
enum class DataSourceType {
    MYSQL,
    POSTGRESQL,
    EXCEL, // 演示用，实际上可能需要特殊处理
    CSV
}
