package top.phj233.smartdatainsightagent.model.visualization

/**
 * AI 返回的推荐配置 (结构化输出)
 */
data class ChartRecommendation(
    val chartType: String, // bar, line, pie...
    val title: String,
    val reason: String, // 推荐理由
    val dimensionField: String, // 维度字段 (通常是 X 轴，如 "日期")
    val metricFields: List<String>, // 指标字段列表 (通常是 Y 轴，如 "销售额", "利润")
    val colorPalette: List<String>? = null // 可选配色建议
)
