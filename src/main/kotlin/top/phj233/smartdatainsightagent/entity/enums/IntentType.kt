package top.phj233.smartdatainsightagent.entity.enums

import org.babyfish.jimmer.sql.EnumType

/**
 * @author phj233
 * @since 2026/1/28 14:56
 * @version
 */
@EnumType(EnumType.Strategy.NAME)
enum class IntentType {
    DATA_QUERY,        // 数据查询
    TREND_ANALYSIS,    // 趋势分析
    PREDICTION,        // 预测
    REPORT_GENERATION  // 报告生成
}
