package top.phj233.smartdatainsightagent.model

import top.phj233.smartdatainsightagent.model.visualization.EChartsVisualization

data class AnalysisResult(
    val data: List<Map<String, Any>>,
    val insights: String, // AI生成的洞察文本
    val visualizations: List<EChartsVisualization>,
    val sqlQuery: String?
)
