package top.phj233.smartdatainsightagent.model.visualization

/**
 * 最终返回给前端的 ECharts 配置 (结构化输出)
 */
data class EChartsVisualization(
    val type: String,         // 图表类型：line, bar, pie, scatter, map 等
    val title: String,        // 图表标题
    val description: String?, // 图表解读
    val option: Map<String, Any> // 完整的 ECharts Option (含 dataset)
)
