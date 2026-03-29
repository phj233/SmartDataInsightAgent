package top.phj233.smartdatainsightagent.model

import com.fasterxml.jackson.databind.JsonNode
import top.phj233.smartdatainsightagent.model.visualization.EChartsVisualization

/**
 * 分析结果数据类，包含分析结果的详细信息。
 * @param data 分析结果数据列表，每个元素是一个包含分析结果字段的Map。
 * @param insights AI生成的洞察文本，描述分析结果的主要发现和结论。
 * @param visualizations 可视化图表列表，使用EChartsVisualization表示每个图表的类型和数据。
 * @param sqlQuery 生成的SQL查询语句，如果有的话，用于获取分析结果的数据来源。
 * @author phj233
 * @since 2026/2/28 14:57
 * @version
 */
data class AnalysisResult(
    val data: List<Map<String, JsonNode>>,
    val insights: String, // AI生成的洞察文本
    val visualizations: List<EChartsVisualization>,
    val sqlQuery: String?
)
