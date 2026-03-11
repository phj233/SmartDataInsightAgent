package top.phj233.smartdatainsightagent.entity.enums

import org.babyfish.jimmer.sql.EnumType

/**
 * 分析意图枚举类
 * - DATA_QUERY: 数据查询，用户希望从数据中获取特定的信息或结果。
 * - TREND_ANALYSIS: 趋势分析，用户希望分析数据中的趋势和模式。
 * - PREDICTION: 预测，用户希望基于现有数据进行未来趋势或结果的预测。
 * - REPORT_GENERATION: 报告生成，用户希望生成一个包含数据分析结果的报告，可能包括图表和文字描述。
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
