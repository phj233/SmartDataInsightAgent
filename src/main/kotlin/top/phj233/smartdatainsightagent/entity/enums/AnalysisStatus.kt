package top.phj233.smartdatainsightagent.entity.enums

import org.babyfish.jimmer.sql.EnumType

/**
 * @author phj233
 * @since 2026/1/28 14:55
 * @version
 */
@EnumType(EnumType.Strategy.NAME)
enum class AnalysisStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}
