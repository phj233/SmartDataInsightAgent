package top.phj233.smartdatainsightagent.entity.enums

import org.babyfish.jimmer.sql.EnumType

/**
 * 分析状态枚举类，表示分析任务的当前状态。
 * - PENDING: 任务已创建但尚未开始执行。
 * - RUNNING: 任务正在执行中。
 * - SUCCESS: 任务已成功完成。
 * - FAILED: 任务执行失败。
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
