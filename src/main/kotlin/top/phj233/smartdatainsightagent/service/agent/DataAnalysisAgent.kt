package top.phj233.smartdatainsightagent.service.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.phj233.smartdatainsightagent.entity.enums.IntentType
import top.phj233.smartdatainsightagent.model.AnalysisRequest
import top.phj233.smartdatainsightagent.model.AnalysisResult
import top.phj233.smartdatainsightagent.model.Intent
import top.phj233.smartdatainsightagent.service.ai.DeepseekService
import top.phj233.smartdatainsightagent.service.data.QueryExecutorService

@Service
/**
 * 数据分析 Agent，负责理解用户意图并执行相应的数据分析流程
 * @author phj233
 * @since 2026/1/28 15:30
 * @version
 */
class DataAnalysisAgent(
    private val queryParserAgent: QueryParserAgent,
    private val visualizationAgent: VisualizationAgent,
    private val deepseekService: DeepseekService,
    private val queryExecutorService: QueryExecutorService,
    private val objectMapper: ObjectMapper,
    private val log: Logger = LoggerFactory.getLogger(DataAnalysisAgent::class.java)
) {

    @Transactional
    suspend fun analyzeData(request: AnalysisRequest): AnalysisResult {
        // 1. 理解用户意图
        val intent = understandIntent(request.query)

        // 2. 根据意图选择处理流程
        return when (intent.type) {
            IntentType.DATA_QUERY -> processDataQuery(request)
            IntentType.TREND_ANALYSIS -> processTrendAnalysis(request)
            IntentType.PREDICTION -> processPrediction(request)
            IntentType.REPORT_GENERATION -> processReportGeneration(request)
        }
    }

    /**
     * 理解用户查询的意图，动态判断是普通查询、趋势分析、预测还是报告生成
     * @param query 用户的自然语言查询
     * @return Intent 包含意图类型、相关参数和置信度
     */
    private suspend fun understandIntent(query: String): Intent {
        // 使用 DeepSeek 动态判断意图
        val prompt = """
            分析以下用户查询的意图：
            查询："$query"
            
            请从以下类型中选择一个：
            DATA_QUERY: 普通的数据检索
            TREND_ANALYSIS: 包含"趋势"、"变化"、"走势"等时间相关分析
            PREDICTION: 包含"预测"、"未来"、"下个月"等预测性需求
            REPORT_GENERATION: 包含"报告"、"总结"、"综述"等生成文档需求
            
            返回JSON格式：
            {
                "type": "DATA_QUERY|TREND_ANALYSIS|PREDICTION|REPORT_GENERATION",
                "parameters": {相关参数},
                "confidence": 0.95
            }
        """.trimIndent()

        return try {
            deepseekService.structuredOutput(prompt, Intent::class.java) as Intent
        } catch (e: Exception) {
            log.warn("DataAnalysisAgent: 无法准确理解意图，默认使用 DATA_QUERY。错误: ${e.message}")
            // 兜底策略
            Intent(IntentType.DATA_QUERY, confidence = 1.0)
        }
    }

    // --- 1. 普通数据查询 ---
    /**
     * 处理普通数据查询，生成 SQL 执行并返回结果，同时提供简要洞察和可视化建议
     * @param request 包含用户查询和数据源信息的请求对象
     * @return AnalysisResult 包含查询结果、洞察、可视化建议和执行的 SQL 语句
      - 洞察生成提示词：明确告知 AI 关注点，避免泛泛而谈
     */
    private suspend fun processDataQuery(request: AnalysisRequest): AnalysisResult {
        val sqlQuery = queryParserAgent.parseNaturalLanguageToSQL(request.query, request.dataSourceId)
        val data = queryExecutorService.executeQuery(sqlQuery)
        val insights = generateInsights(data, request.query, "简明扼要地回答用户问题。")
        val visualizations = visualizationAgent.suggestVisualizations(data, request.query)

        return AnalysisResult(data, insights, visualizations, sqlQuery)
    }

    // --- 2. 趋势分析 (侧重时间序列) ---
    /**
     * 处理趋势分析，强调时间维度的分组和排序，生成针对趋势的洞察，并提供适合趋势分析的可视化建议
     * @param request 包含用户查询和数据源信息的请求对象
     * @return AnalysisResult 包含查询结果、趋势洞察、可视化建议和执行的 SQL 语句
      - 洞察生成提示词：明确告知 AI 关注点，指导其分析趋势、增长率、峰值和异常波动，避免泛泛而谈
      - 可视化建议提示词：明确告知 AI 用户关注时间趋势分析，建议优先使用折线图(line)或面积图，引导其选择更合适的图表类型
     */
    private suspend fun processTrendAnalysis(request: AnalysisRequest): AnalysisResult {
        val enhancedQuery = "${request.query}。请务必按时间维度（如日、月、年）进行分组统计，并按时间排序。"
        val sqlQuery = queryParserAgent.parseNaturalLanguageToSQL(enhancedQuery, request.dataSourceId)
        val data = queryExecutorService.executeQuery(sqlQuery)

        val insights = generateInsights(data, request.query, "重点分析数据的变化趋势、增长率、峰值和异常波动。")
        // 提示词增强：明确告知 AI 用户关注的是"趋势"，引导其选择 Line/Area 图表
        val vizHint = "用户关注时间趋势分析，建议优先使用折线图(line)或面积图。${request.query}"
        val visualizations = visualizationAgent.suggestVisualizations(data, vizHint)

        return AnalysisResult(data, insights, visualizations, sqlQuery)
    }

    // --- 3. 预测 (简单 AI 预测) ---
    /**
     * 处理预测需求，首先获取相关的历史时间序列数据，然后使用简单的 LLM 模拟预测未来趋势
     * @param request 包含用户查询和数据源信息的请求对象
     * @return AnalysisResult 包含历史数据和预测数据的完整数据集、预测洞察、可视化建议和执行的 SQL 语句
     */
    private suspend fun processPrediction(request: AnalysisRequest): AnalysisResult {
        val historyQuery = "${request.query}。请获取相关的历史时间序列数据用于预测。"
        val sqlQuery = queryParserAgent.parseNaturalLanguageToSQL(historyQuery, request.dataSourceId)
        val historyData = queryExecutorService.executeQuery(sqlQuery)

        if (historyData.isEmpty()) {
            return AnalysisResult(emptyList(), "没有足够的历史数据进行预测。", emptyList(), sqlQuery)
        }

        // 简单的 LLM 预测模拟
        val predictionPrompt = """
            基于以下历史数据，预测未来 3 个时间点的数值：
            历史数据: ${historyData.takeLast(20)}
            
            请返回预测后的完整数据集（包含历史数据和预测数据）。
            对于预测数据，请添加一个字段 "is_prediction": true (布尔值)。
            只返回 JSON 数组。
        """.trimIndent()

        val predictedDataString = deepseekService.chatCompletion(predictionPrompt) ?: "[]"

        val finalData = try {
            objectMapper.readValue(predictedDataString, List::class.java) as List<Map<String, Any>>
        } catch (e: Exception) {
            historyData
        }

        val insights = "基于历史趋势，AI 预测了未来的走势（预测部分仅供参考）。"

        // 提示词增强：告知 AI 数据中包含预测字段，建议区分显示
        val vizHint = "数据包含历史和预测部分（由 'is_prediction' 字段标记）。建议使用折线图，并尝试用颜色或线型区分预测部分。${request.query}"
        val visualizations = visualizationAgent.suggestVisualizations(finalData, vizHint)

        return AnalysisResult(finalData, insights, visualizations, sqlQuery)
    }

    // --- 4. 报告生成 (侧重文本) ---
    /**
     * 处理报告生成需求，首先执行宽泛的查询获取全面数据，然后使用 LLM 生成结构化的分析报告
     * @param request 包含用户查询和数据源信息的请求对象
     * @return AnalysisResult 包含查询结果、完整分析报告、可视化建议
     */
    private suspend fun processReportGeneration(request: AnalysisRequest): AnalysisResult {
        // 1. 执行宽泛的查询以获取全面数据
        val sqlQuery = queryParserAgent.parseNaturalLanguageToSQL(request.query, request.dataSourceId)
        val data = queryExecutorService.executeQuery(sqlQuery)

        // 2. 生成详细报告
        val reportPrompt = """
            你是一个商业数据分析师。请根据以下数据生成一份详细的分析报告。
            
            数据摘要: ${data.take(100)}
            用户主题: "${request.query}"
            
            报告结构：
            1. 核心结论 (Executive Summary)
            2. 数据详情与现状
            3. 潜在风险与机会
            4. 业务建议
            
            使用 Markdown 格式。
        """.trimIndent()

        val fullReport = deepseekService.chatCompletion(reportPrompt) ?: "报告生成失败"

        // 报告模式下，图表是辅助
        val visualizations = visualizationAgent.suggestVisualizations(data, request.query)

        return AnalysisResult(data, fullReport, visualizations, sqlQuery)
    }

    // 通用洞察生成器
    /**
     * 根据查询结果生成针对性的洞察，明确告知 AI 关注点，避免泛泛而谈
     * @param data 查询结果数据
     * @param query 用户的原始查询
     * @param focus 洞察关注点提示词，指导 AI 生成更有针对性的洞察
     * @return 洞察文本
     */
    private suspend fun generateInsights(data: List<Map<String, Any>>, query: String, focus: String): String {
        if (data.isEmpty()) return "未查询到相关数据。"
        val prompt = """
            根据以下数据回答问题。
            问题: "$query"
            关注点: $focus
            数据样本: ${data.take(20)}
        """.trimIndent()
        return deepseekService.chatCompletion(prompt) ?: "暂无洞察。"
    }
}
