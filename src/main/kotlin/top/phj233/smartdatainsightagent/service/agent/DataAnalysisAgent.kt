package top.phj233.smartdatainsightagent.service.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.phj233.smartdatainsightagent.entity.enums.IntentType
import top.phj233.smartdatainsightagent.model.AnalysisRequest
import top.phj233.smartdatainsightagent.model.AnalysisResult
import top.phj233.smartdatainsightagent.model.Intent
import top.phj233.smartdatainsightagent.service.AnalysisTaskService
import top.phj233.smartdatainsightagent.service.ai.DeepseekService
import top.phj233.smartdatainsightagent.service.data.NaturalLanguageDataExtractionService
import top.phj233.smartdatainsightagent.service.data.QueryExecutorService
import top.phj233.smartdatainsightagent.service.data.RawTextDataParserService

private typealias StageRecorder = suspend (String, Map<String, Any>, String?) -> Unit

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
    private val rawTextDataParserService: RawTextDataParserService,
    private val naturalLanguageDataExtractionService: NaturalLanguageDataExtractionService,
    private val analysisTaskService: AnalysisTaskService,
    private val objectMapper: ObjectMapper,
    private val log: Logger = LoggerFactory.getLogger(DataAnalysisAgent::class.java)
) {

    /**
     * 分析数据的主入口方法，根据请求内容动态判断分析流程。
     * @param request 包含用户查询、数据源信息和用户 ID 的分析请求对象
     * @return AnalysisResult 包含分析结果、洞察和可视化建议
     * @see AnalysisResult
     * 流程概述：
     * - 如果请求中包含 dataSourceId，则先解析用户意图，再根据意图分发到对应的分析流程（普通查询、趋势分析、预测或报告生成）。
     * - 如果请求中不包含 dataSourceId，则直接将用户输入的文本数据进行解析和分析，适用于用户直接提供表格数据或自然语言数据描述的场景。整个流程中会记录详细的阶段信息，包括 SQL 生成、查询执行、洞察生成和可视化建议等，以便后续分析任务的监控和优化。
     *
     * 异常处理：如果在任何阶段发生异常，都会记录失败状态和错误信息，并抛出异常以便上层处理。
     */
    @Transactional
    suspend fun analyzeData(request: AnalysisRequest): AnalysisResult {
        val task = withContext(Dispatchers.IO) {
            analysisTaskService.createTask(request)
        }
        val startTime = System.currentTimeMillis()
        var lastStage = AnalysisTaskService.STAGE_TASK_CREATED

        val recordStage: StageRecorder = { stage, details, generatedSql ->
            lastStage = stage
            runBlockingTaskPersistence {
                analysisTaskService.markRunning(task.id, stage, details, generatedSql)
            }
        }

        return try {
            val result = if (request.dataSourceId == null) {
                processRawTextData(request, recordStage)
            } else {
                processStructuredRequest(request, recordStage)
            }

            runBlockingTaskPersistence {
                analysisTaskService.markSuccess(
                    taskId = task.id,
                    result = result,
                    executionTime = System.currentTimeMillis() - startTime,
                    stage = AnalysisTaskService.STAGE_COMPLETED,
                    details = buildMap {
                        put("dataSourceId", request.dataSourceId?.toString() ?: "RAW_TEXT")
                        put("resultRowCount", result.data.size)
                        put("visualizationCount", result.visualizations.size)
                    }
                )
            }
            result
        } catch (ex: Exception) {
            runBlockingTaskPersistence {
                analysisTaskService.markFailed(
                    taskId = task.id,
                    stage = lastStage,
                    errorMessage = ex.message ?: "分析任务执行失败",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
            throw ex
        }
    }

    private suspend fun runBlockingTaskPersistence(action: () -> Unit) {
        withContext(Dispatchers.IO) {
            action()
        }
    }

    /**
     * 处理带数据源的结构化分析请求。
     *
     * 会先解析用户意图，再根据意图分发到对应的数据查询、趋势分析、预测或报告生成流程。
     * @param request 分析请求
     * @param recordStage 阶段记录函数
     * @return 对应意图执行后的分析结果
     */
    private suspend fun processStructuredRequest(request: AnalysisRequest, recordStage: StageRecorder): AnalysisResult {
        val intent = resolveIntent(request, recordStage)
        return when (intent.type) {
            IntentType.DATA_QUERY -> processDataQuery(request, recordStage)
            IntentType.TREND_ANALYSIS -> processTrendAnalysis(request, recordStage)
            IntentType.PREDICTION -> processPrediction(request, recordStage)
            IntentType.REPORT_GENERATION -> processReportGeneration(request, recordStage)
        }
    }

    /**
     * 解析用户意图并记录意图分析相关阶段。
     *
     * @param request 分析请求
     * @param recordStage 阶段记录函数
     * @return 解析得到的意图对象
     */
    private suspend fun resolveIntent(request: AnalysisRequest, recordStage: StageRecorder): Intent {
        recordStage(
            AnalysisTaskService.STAGE_INTENT_ANALYZING,
            mapOf("query" to request.query),
            null
        )

        val intent = understandIntent(request.query)
        recordStage(
            AnalysisTaskService.STAGE_INTENT_RESOLVED,
            buildMap {
                put("intentType", intent.type.name)
                put("confidence", intent.confidence)
                intent.parameters?.takeIf { it.isNotEmpty() }?.let { put("intentParameters", it) }
            },
            null
        )
        return intent
    }

    /**
     * 理解用户查询的意图，动态判断是普通查询、趋势分析、预测还是报告生成。
     *
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
     * 处理普通数据查询，生成 SQL 执行并返回结果，同时提供简要洞察和可视化建议。
     *
     * @param request 包含用户查询和数据源信息的请求对象
     * @param recordStage 阶段记录函数
     * @return AnalysisResult 包含查询结果、洞察、可视化建议和执行的 SQL 语句
     */
    private suspend fun processDataQuery(request: AnalysisRequest, recordStage: StageRecorder): AnalysisResult {
        val execution = executeQueryFlow(request, request.query, IntentType.DATA_QUERY, recordStage)
        recordInsightsStage(recordStage, execution.data.size)
        val insights = generateInsights(execution.data, request.query, "简明扼要地回答用户问题。")
        recordVisualizationStage(recordStage, execution.data.size, IntentType.DATA_QUERY.name)
        val visualizations = visualizationAgent.suggestVisualizations(execution.data, request.query)

        return AnalysisResult(execution.data, insights, visualizations, execution.sqlQuery)
    }

    // --- 2. 趋势分析 (侧重时间序列) ---
    /**
     * 处理趋势分析，强调时间维度的分组和排序，生成针对趋势的洞察，并提供适合趋势分析的可视化建议。
     *
     * @param request 包含用户查询和数据源信息的请求对象
     * @param recordStage 阶段记录函数
     * @return AnalysisResult 包含查询结果、趋势洞察、可视化建议和执行的 SQL 语句
     */
    private suspend fun processTrendAnalysis(request: AnalysisRequest, recordStage: StageRecorder): AnalysisResult {
        val enhancedQuery = "${request.query}。请务必按时间维度（如日、月、年）进行分组统计，并按时间排序。"
        val execution = executeQueryFlow(request, enhancedQuery, IntentType.TREND_ANALYSIS, recordStage)
        recordInsightsStage(recordStage, execution.data.size)
        val insights = generateInsights(execution.data, request.query, "重点分析数据的变化趋势、增长率、峰值和异常波动。")
        // 提示词增强：明确告知 AI 用户关注的是"趋势"，引导其选择 Line/Area 图表
        val vizHint = "用户关注时间趋势分析，建议优先使用折线图(line)或面积图。${request.query}"
        recordVisualizationStage(recordStage, execution.data.size, IntentType.TREND_ANALYSIS.name)
        val visualizations = visualizationAgent.suggestVisualizations(execution.data, vizHint)

        return AnalysisResult(execution.data, insights, visualizations, execution.sqlQuery)
    }

    // --- 3. 预测 (简单 AI 预测) ---
    /**
     * 处理预测需求，先获取历史时间序列数据，再使用大模型模拟未来趋势。
     *
     * @param request 包含用户查询和数据源信息的请求对象
     * @param recordStage 阶段记录函数
     * @return AnalysisResult 包含历史数据和预测数据的完整数据集、预测洞察、可视化建议和执行的 SQL 语句
     */
    private suspend fun processPrediction(request: AnalysisRequest, recordStage: StageRecorder): AnalysisResult {
        val historyQuery = "${request.query}。请获取相关的历史时间序列数据用于预测。"
        val execution = executeQueryFlow(request, historyQuery, IntentType.PREDICTION, recordStage)
        val historyData = execution.data

        if (historyData.isEmpty()) {
            return AnalysisResult(emptyList(), "没有足够的历史数据进行预测。", emptyList(), execution.sqlQuery)
        }

        // 简单的 LLM 预测模拟
        recordStage(
            AnalysisTaskService.STAGE_PREDICTION_GENERATING,
            mapOf("historyRowCount" to historyData.size),
            null
        )
        val predictionPrompt = """
            基于以下历史数据，预测未来 3 个时间点的数值：
            历史数据: ${historyData.takeLast(20)}
            
            请返回预测后的完整数据集（包含历史数据和预测数据）。
            对于预测数据，请添加一个字段 "is_prediction": true (布尔值)。
            只返回 JSON 数组。
        """.trimIndent()

        val predictedDataString = deepseekService.chatCompletion(predictionPrompt) ?: "[]"
        val finalData = parsePredictedData(predictedDataString, historyData)

        val insights = "基于历史趋势，AI 预测了未来的走势（预测部分仅供参考）。"

        // 提示词增强：告知 AI 数据中包含预测字段，建议区分显示
        val vizHint = "数据包含历史和预测部分（由 'is_prediction' 字段标记）。建议使用折线图，并尝试用颜色或线型区分预测部分。${request.query}"
        recordVisualizationStage(recordStage, finalData.size, IntentType.PREDICTION.name)
        val visualizations = visualizationAgent.suggestVisualizations(finalData, vizHint)

        return AnalysisResult(finalData, insights, visualizations, execution.sqlQuery)
    }

    // --- 4. 报告生成 (侧重文本) ---
    /**
     * 处理报告生成需求，先执行宽泛查询获取全面数据，再使用大模型生成结构化分析报告。
     *
     * @param request 包含用户查询和数据源信息的请求对象
     * @param recordStage 阶段记录函数
     * @return AnalysisResult 包含查询结果、完整分析报告、可视化建议
     */
    private suspend fun processReportGeneration(request: AnalysisRequest, recordStage: StageRecorder): AnalysisResult {
        // 1. 执行宽泛的查询以获取全面数据
        val execution = executeQueryFlow(request, request.query, IntentType.REPORT_GENERATION, recordStage)
        val data = execution.data

        // 2. 生成详细报告
        recordStage(
            AnalysisTaskService.STAGE_REPORT_GENERATING,
            mapOf("rowCount" to data.size),
            null
        )
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
        recordVisualizationStage(recordStage, data.size, IntentType.REPORT_GENERATION.name)
        val visualizations = visualizationAgent.suggestVisualizations(data, request.query)

        return AnalysisResult(data, fullReport, visualizations, execution.sqlQuery)
    }

    // 通用洞察生成器
    /**
     * 根据查询结果生成针对性的洞察，明确告知 AI 关注点，避免泛泛而谈。
     *
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

    // --- 5. 原始文本数据分析 ---
    /**
     * 处理原始文本数据，直接对用户提供的表格数据或自然语言数据描述进行分析。
     *
     * @param request 包含用户查询和数据源信息的请求对象
     * @param recordStage 阶段记录函数
     * @return AnalysisResult 包含分析结果、洞察和可视化建议
     */
    private suspend fun processRawTextData(request: AnalysisRequest, recordStage: StageRecorder): AnalysisResult {
        recordStage(
            AnalysisTaskService.STAGE_RAW_TEXT_PARSING,
            mapOf("queryLength" to request.query.length),
            null
        )
        val data = extractInputData(request, recordStage)
        recordInsightsStage(recordStage, data.size)
        val insights = generateInsights(data, request.query, "用户直接提供了原始数据或自然语言描述，请提炼关键结论，并指出最值得关注的对比、趋势或异常。")
        recordVisualizationStage(recordStage, data.size, RAW_TEXT_MODE)
        val visualizations = visualizationAgent.suggestVisualizations(data, request.query)
        return AnalysisResult(data, insights, visualizations, null)
    }

    /**
     * 执行通用的数据源查询流程：生成 SQL、执行查询，并记录对应阶段。
     *
     * @param request 分析请求
     * @param queryForSql 用于生成 SQL 的查询文本
     * @param intentType 当前分析意图类型
     * @param recordStage 阶段记录函数
     * @return 包含 SQL 与查询结果的执行结果对象
     */
    private suspend fun executeQueryFlow(
        request: AnalysisRequest,
        queryForSql: String,
        intentType: IntentType,
        recordStage: StageRecorder
    ): QueryExecutionResult {
        val dataSourceId = requireDataSourceId(request)
        recordStage(
            AnalysisTaskService.STAGE_SQL_GENERATING,
            mapOf("analysisType" to intentType.name, "dataSourceId" to dataSourceId),
            null
        )
        val sqlQuery = queryParserAgent.parseNaturalLanguageToSQL(queryForSql, dataSourceId, request.userId)
        recordStage(
            AnalysisTaskService.STAGE_SQL_GENERATED,
            mapOf("analysisType" to intentType.name),
            sqlQuery
        )
        recordStage(AnalysisTaskService.STAGE_QUERY_EXECUTING, mapOf("sqlLength" to sqlQuery.length), null)
        val data = queryExecutorService.executeQuery(sqlQuery, dataSourceId, request.userId)
        recordStage(AnalysisTaskService.STAGE_QUERY_EXECUTED, mapOf("rowCount" to data.size), null)
        return QueryExecutionResult(sqlQuery, data)
    }

    /**
     * 解析预测结果文本为结构化数据；如果解析失败，则回退到历史数据。
     *
     * @param predictedDataString 大模型返回的预测数据文本
     * @param historyData 历史数据，用于解析失败时回退
     * @return 可用于后续可视化和展示的数据集
     */
    private fun parsePredictedData(
        predictedDataString: String,
        historyData: List<Map<String, Any>>
    ): List<Map<String, Any>> {
        return try {
            val parsed: List<Map<String, Any?>> = objectMapper.readValue(
                predictedDataString,
                object : TypeReference<List<Map<String, Any?>>>() {}
            )
            parsed.map { row ->
                row.mapValues { (_, value) -> value ?: "" }
            }
        } catch (ex: Exception) {
            log.warn("DataAnalysisAgent: 预测结果解析失败，回退到历史数据。错误: ${ex.message}")
            historyData
        }
    }

    /**
     * 解析用户输入的原始数据。
     *
     * 会优先尝试按结构化表格/JSON 解析；若失败，则降级为自然语言数据抽取。
     *
     * @param request 分析请求
     * @param recordStage 阶段记录函数
     * @return 解析得到的数据列表
     */
    private suspend fun extractInputData(
        request: AnalysisRequest,
        recordStage: StageRecorder
    ): List<Map<String, Any>> {
        return try {
            rawTextDataParserService.parse(request.query)
        } catch (_: IllegalArgumentException) {
            recordStage(
                AnalysisTaskService.STAGE_NATURAL_LANGUAGE_EXTRACTION,
                mapOf("fallback" to true),
                null
            )
            naturalLanguageDataExtractionService.extract(request.query)
        }
    }

    /**
     * 记录“生成洞察”阶段。
     *
     * @param recordStage 阶段记录函数
     * @param rowCount 当前数据行数
     */
    private suspend fun recordInsightsStage(recordStage: StageRecorder, rowCount: Int) {
        recordStage(AnalysisTaskService.STAGE_INSIGHTS_GENERATING, mapOf("rowCount" to rowCount), null)
    }

    /**
     * 记录“生成可视化”阶段。
     *
     * @param recordStage 阶段记录函数
     * @param rowCount 当前数据行数
     * @param queryMode 当前查询模式，用于区分普通查询、趋势分析、预测或原始文本模式
     */
    private suspend fun recordVisualizationStage(recordStage: StageRecorder, rowCount: Int, queryMode: String) {
        recordStage(
            AnalysisTaskService.STAGE_VISUALIZATION_GENERATING,
            mapOf("rowCount" to rowCount, "queryMode" to queryMode),
            null
        )
    }

    /**
     * 从请求中提取数据源 ID。
     *
     * @param request 分析请求
     * @return 非空的数据源 ID
     * @throws IllegalArgumentException 当请求中未提供数据源 ID 时抛出
     */
    private fun requireDataSourceId(request: AnalysisRequest): Long {
        return requireNotNull(request.dataSourceId) { "dataSourceId 不能为空" }
    }

    private data class QueryExecutionResult(
        val sqlQuery: String,
        val data: List<Map<String, Any>>
    )

    private companion object {
        const val RAW_TEXT_MODE = "RAW_TEXT"
    }
}
