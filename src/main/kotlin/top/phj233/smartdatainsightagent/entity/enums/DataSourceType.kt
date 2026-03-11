package top.phj233.smartdatainsightagent.entity.enums

import org.babyfish.jimmer.sql.EnumType

/**
 * 数据源类型枚举类
 * - MYSQL: MySQL数据库
 * - POSTGRESQL: PostgreSQL数据库
 * - EXCEL: Excel文件（演示用，实际上可能需要特殊处理）
 * - CSV: CSV文件
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
