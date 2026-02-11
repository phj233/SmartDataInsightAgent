package top.phj233.smartdatainsightagent.service.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.model.visualization.ChartRecommendation
import top.phj233.smartdatainsightagent.model.visualization.DataSchema
import top.phj233.smartdatainsightagent.model.visualization.EChartsVisualization
import top.phj233.smartdatainsightagent.service.ai.DeepseekService
import java.time.temporal.Temporal
import java.util.*

/**
 * 可视化建议与配置生成 Agent
 * @author phj233
 * @since 2026/1/28 14:44
 * @version
 */
@Service
class VisualizationAgent(
    private val deepseekService: DeepseekService,
    private val objectMapper: ObjectMapper,
    private val log:Logger = LoggerFactory.getLogger(VisualizationAgent::class.java)
) {
    /**
     * 生成可视化建议及完整的 ECharts 配置
     */
    suspend fun suggestVisualizations(
        data: List<Map<String, Any>>,
        userQuery: String
    ): List<EChartsVisualization> {
        if (data.isEmpty()) return emptyList()

        // 1. 提取元数据 (Schema)
        val schema = extractSchema(data)

        // 2. 调用 AI 获取可视化策略 (不含数据)
        val recommendations = getAiRecommendations(schema, userQuery)

        // 3. 将策略转换为 ECharts Option (注入数据)
        return recommendations.map { rec ->
            buildEChartsOption(rec, data)
        }
    }

    private fun extractSchema(data: List<Map<String, Any>>): List<DataSchema> {
        val firstRow = data.first()
        return firstRow.map { (key, value) ->
            val type = when (value) {
                is Number -> "NUMERIC"
                is Temporal, is Date -> "DATE"
                else -> "STRING"
            }
            DataSchema(key, type, value)
        }
    }

    private suspend fun getAiRecommendations(
        schema: List<DataSchema>,
        query: String
    ): List<ChartRecommendation> {
        val prompt = """
            你是一个数据可视化专家。基于用户查询和数据结构，推荐 1-2 个最合适的图表。
            
            用户查询: "$query"
            数据字段结构: ${objectMapper.writeValueAsString(schema)}
            
            要求：
            1. 'dimensionField' 是分类轴或时间轴的字段名。
            2. 'metricFields' 是数值轴的字段名列表。
            3. 如果是多维分析，metricFields 可以包含多个字段。
            4. 仅仅返回 JSON 数组，不要 Markdown 标记。
            
            返回格式示例:
            [
              {
                "chartType": "bar",
                "title": "各地区销售额对比",
                "reason": "用户关注销售对比",
                "dimensionField": "region",
                "metricFields": ["sales"]
              }
            ]
        """.trimIndent()

        // 使用 structuredOutput 保证稳定性 (如果 DeepseekService 支持)
        // 这里演示解析 JSON 字符串的方式
        return try {
            val jsonStr = deepseekService.chatCompletion(prompt)
                ?.replace("```json", "")
                ?.replace("```", "")
                ?.trim()
                ?: "[]"

            objectMapper.readValue(
                jsonStr,
                objectMapper.typeFactory.constructCollectionType(List::class.java, ChartRecommendation::class.java)
            )
        } catch (e: Exception) {
            log.error("VisualizationAgent 获取 AI 推荐失败，使用降级策略 fallbackRecommendation", e)
            // 降级策略：默认取第一个字符串字段做维度，第一个数字字段做指标
            fallbackRecommendation(schema)
        }
    }

    /**
     * 降级策略：当 AI 推荐失败时，基于简单规则生成一个默认的图表建议
      - 维度：优先选择 STRING 或 DATE 类型的字段
      - 指标：优先选择 NUMERIC 类型的字段
     */
    private fun fallbackRecommendation(schema: List<DataSchema>): List<ChartRecommendation> {
        val dim = schema.find { it.fieldType == "STRING" || it.fieldType == "DATE" }?.fieldName ?: return emptyList()
        val met = schema.find { it.fieldType == "NUMERIC" }?.fieldName ?: return emptyList()
        return listOf(
            ChartRecommendation(
                chartType = "bar",
                title = "数据概览",
                reason = "自动生成的默认图表",
                dimensionField = dim,
                metricFields = listOf(met)
            )
        )
    }

    /**
     * 构建基于 Dataset 的 ECharts Option
     * 这是 ECharts 4.0+ 的最佳实践，数据与配置分离
     */
    private fun buildEChartsOption(
        rec: ChartRecommendation,
        data: List<Map<String, Any>>
    ): EChartsVisualization {

        // 1. 基础配置
        val option = mutableMapOf<String, Any>()

        // 2. 注入数据 (Dataset)
        // source 可以直接接受 List<Map<String, Any>>
        option["dataset"] = mapOf("source" to data)

        // 3. 构建坐标轴
        if (rec.chartType !in listOf("pie", "radar")) {
            option["xAxis"] = mapOf("type" to "category") // 维度轴通常是 category
            option["yAxis"] = mapOf("type" to "value")    // 指标轴通常是 value
            option["grid"] = mapOf("containLabel" to true)
        }

        // 4. 工具栏
        option["tooltip"] = mapOf("trigger" to if (rec.chartType == "pie") "item" else "axis")
        option["toolbox"] = mapOf(
            "feature" to mapOf(
                "saveAsImage" to mapOf("show" to true),
                "dataView" to mapOf("show" to true)
            )
        )

        // 5. 构建 Series (使用 encode 映射)
        val seriesList = rec.metricFields.map { metric ->
            val seriesConfig = mutableMapOf<String, Any>(
                "type" to rec.chartType,
                "name" to metric,
                // 核心：使用 encode 绑定维度和指标
                "encode" to mapOf(
                    "x" to rec.dimensionField, // X轴映射到维度字段
                    "y" to metric,             // Y轴映射到指标字段
                    "tooltip" to listOf(rec.dimensionField, metric)
                )
            )

            // 特殊图表类型的微调
            when (rec.chartType) {
                "line" -> {
                    seriesConfig["smooth"] = true
                    seriesConfig["areaStyle"] = mapOf("opacity" to 0.1)
                }
                "pie" -> {
                    seriesConfig["radius"] = listOf("40%", "70%")
                    seriesConfig["encode"] = mapOf(
                        "itemName" to rec.dimensionField,
                        "value" to metric
                    )
                    // 饼图不需要 Grid/XY轴，需清理
                    option.remove("xAxis")
                    option.remove("yAxis")
                }
            }
            seriesConfig
        }

        option["series"] = seriesList

        // 6. 标题与配色
        option["title"] = mapOf(
            "text" to rec.title,
            "subtext" to rec.reason,
            "left" to "center"
        )

        if (!rec.colorPalette.isNullOrEmpty()) {
            option["color"] = rec.colorPalette
        }

        return EChartsVisualization(
            type = rec.chartType,
            title = rec.title,
            description = rec.reason,
            option = option
        )
    }
}
