package top.phj233.smartdatainsightagent.service.agent

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.slf4j.Logger
import org.springframework.ai.deepseek.DeepSeekChatModel
import org.springframework.jdbc.core.JdbcTemplate
import top.phj233.smartdatainsightagent.entity.AnalysisTask
import top.phj233.smartdatainsightagent.entity.AnalysisTaskDraft
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.entity.enums.IntentType
import top.phj233.smartdatainsightagent.model.AnalysisRequest
import top.phj233.smartdatainsightagent.model.AnalysisResult
import top.phj233.smartdatainsightagent.model.Intent
import top.phj233.smartdatainsightagent.model.visualization.EChartsVisualization
import top.phj233.smartdatainsightagent.repository.AnalysisTaskRepository
import top.phj233.smartdatainsightagent.service.AnalysisTaskService
import top.phj233.smartdatainsightagent.service.ai.DeepseekService
import top.phj233.smartdatainsightagent.service.data.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class DataAnalysisAgentTest {

    @Test
    fun `persist task across datasource analysis stages`() {
        val sql = "SELECT region, sales FROM orders"
        val data = listOf(mapOf("region" to "华北", "sales" to 1200))
        val visualizations = listOf(
            EChartsVisualization(
                type = "bar",
                title = "地区销售额",
                description = "自动生成",
                option = mapOf("dataset" to mapOf("source" to data))
            )
        )
        val analysisTaskService = RecordingAnalysisTaskService(100L)
        val deepseekService = FakeDeepseekService(
            structuredOutputHandler = { _, _ -> Intent(IntentType.DATA_QUERY, mapOf("metric" to "sales"), 0.98) },
            chatCompletionHandler = { "销售额整体稳定" }
        )
        val agent = createAgent(
            queryParserAgent = FakeQueryParserAgent { _, _, _ -> sql },
            visualizationAgent = FakeVisualizationAgent { _, _ -> visualizations },
            deepseekService = deepseekService,
            queryExecutorService = FakeQueryExecutorService { executedSql, dataSourceId, userId ->
                assertEquals(sql, executedSql)
                assertEquals(9L, dataSourceId)
                assertEquals(7L, userId)
                data
            },
            rawTextDataParserService = FakeRawTextDataParserService { emptyList() },
            naturalLanguageDataExtractionService = FakeNaturalLanguageDataExtractionService { emptyList() },
            analysisTaskService = analysisTaskService
        )

        val result = runSuspend {
            agent.analyzeData(AnalysisRequest(query = "查看销售额", dataSourceId = 9L, userId = 7L))
        }

        assertEquals(sql, result.sqlQuery)
        assertEquals(1, result.data.size)
        assertEquals(
            listOf(
                AnalysisTaskService.STAGE_INTENT_ANALYZING,
                AnalysisTaskService.STAGE_INTENT_RESOLVED,
                AnalysisTaskService.STAGE_SQL_GENERATING,
                AnalysisTaskService.STAGE_SQL_GENERATED,
                AnalysisTaskService.STAGE_QUERY_EXECUTING,
                AnalysisTaskService.STAGE_QUERY_EXECUTED,
                AnalysisTaskService.STAGE_INSIGHTS_GENERATING,
                AnalysisTaskService.STAGE_VISUALIZATION_GENERATING
            ),
            analysisTaskService.runningStages.map { it.stage }
        )
        assertEquals(sql, analysisTaskService.runningStages.first { it.stage == AnalysisTaskService.STAGE_SQL_GENERATED }.generatedSql)
        assertNotNull(analysisTaskService.successCall)
        assertEquals(AnalysisTaskService.STAGE_COMPLETED, analysisTaskService.successCall!!.stage)
        assertNull(analysisTaskService.failedCall)
    }

    @Test
    fun `persist task for raw text fallback analysis`() {
        val data = listOf(mapOf("month" to "一月", "sales" to 3200))
        val analysisTaskService = RecordingAnalysisTaskService(200L)
        val agent = createAgent(
            queryParserAgent = FakeQueryParserAgent { _, _, _ -> "SELECT 1" },
            visualizationAgent = FakeVisualizationAgent { _, _ -> emptyList() },
            deepseekService = FakeDeepseekService(chatCompletionHandler = { "一月销售额最高" }),
            queryExecutorService = FakeQueryExecutorService { _, _, _ -> emptyList() },
            rawTextDataParserService = FakeRawTextDataParserService { throw IllegalArgumentException("not tabular") },
            naturalLanguageDataExtractionService = FakeNaturalLanguageDataExtractionService { data },
            analysisTaskService = analysisTaskService
        )

        val result = runSuspend {
            agent.analyzeData(AnalysisRequest(query = "一月销售额 3200，二月销售额 2800", dataSourceId = null, userId = 7L))
        }

        assertEquals(1, result.data.size)
        assertEquals(
            listOf(
                AnalysisTaskService.STAGE_RAW_TEXT_PARSING,
                AnalysisTaskService.STAGE_NATURAL_LANGUAGE_EXTRACTION,
                AnalysisTaskService.STAGE_INSIGHTS_GENERATING,
                AnalysisTaskService.STAGE_VISUALIZATION_GENERATING
            ),
            analysisTaskService.runningStages.map { it.stage }
        )
        assertNotNull(analysisTaskService.successCall)
        assertNull(analysisTaskService.failedCall)
    }

    @Test
    fun `mark task failed when datasource stage throws exception`() {
        val analysisTaskService = RecordingAnalysisTaskService(300L)
        val agent = createAgent(
            queryParserAgent = FakeQueryParserAgent { _, _, _ -> throw IllegalArgumentException("SQL 生成失败") },
            visualizationAgent = FakeVisualizationAgent { _, _ -> emptyList() },
            deepseekService = FakeDeepseekService(
                structuredOutputHandler = { _, _ -> Intent(IntentType.DATA_QUERY, confidence = 1.0) }
            ),
            queryExecutorService = FakeQueryExecutorService { _, _, _ -> emptyList() },
            rawTextDataParserService = FakeRawTextDataParserService { emptyList() },
            naturalLanguageDataExtractionService = FakeNaturalLanguageDataExtractionService { emptyList() },
            analysisTaskService = analysisTaskService
        )

        assertThrows(IllegalArgumentException::class.java) {
            runSuspend {
                agent.analyzeData(AnalysisRequest(query = "查看销售额", dataSourceId = 9L, userId = 7L))
            }
        }

        assertNotNull(analysisTaskService.failedCall)
        assertEquals(AnalysisTaskService.STAGE_SQL_GENERATING, analysisTaskService.failedCall!!.stage)
        assertEquals("SQL 生成失败", analysisTaskService.failedCall!!.errorMessage)
        assertNull(analysisTaskService.successCall)
    }

    private fun createAgent(
        queryParserAgent: QueryParserAgent,
        visualizationAgent: VisualizationAgent,
        deepseekService: DeepseekService,
        queryExecutorService: QueryExecutorService,
        rawTextDataParserService: RawTextDataParserService,
        naturalLanguageDataExtractionService: NaturalLanguageDataExtractionService,
        analysisTaskService: AnalysisTaskService
    ): DataAnalysisAgent {
        return DataAnalysisAgent(
            queryParserAgent = queryParserAgent,
            visualizationAgent = visualizationAgent,
            deepseekService = deepseekService,
            queryExecutorService = queryExecutorService,
            rawTextDataParserService = rawTextDataParserService,
            naturalLanguageDataExtractionService = naturalLanguageDataExtractionService,
            analysisTaskService = analysisTaskService,
            objectMapper = ObjectMapper(),
            log = mock(Logger::class.java)
        )
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return outcome!!.getOrThrow()
    }

    private class FakeDeepseekService(
        private val structuredOutputHandler: (String, Class<*>) -> Any = { _, _ -> Intent(IntentType.DATA_QUERY, confidence = 1.0) },
        private val chatCompletionHandler: (String) -> String? = { null }
    ) : DeepseekService(mock(DeepSeekChatModel::class.java), ObjectMapper()) {
        override suspend fun chatCompletion(prompt: String): String? = chatCompletionHandler(prompt)

        override suspend fun structuredOutput(prompt: String, responseType: Class<*>): Any {
            return structuredOutputHandler(prompt, responseType)
        }
    }

    private class FakeQueryParserAgent(
        private val handler: suspend (String, Long, Long) -> String
    ) : QueryParserAgent(
        mock(DeepseekService::class.java),
        mock(DataSourceService::class.java)
    ) {
        override suspend fun parseNaturalLanguageToSQL(nlQuery: String, dataSourceId: Long, userId: Long): String {
            return handler(nlQuery, dataSourceId, userId)
        }
    }

    private class FakeVisualizationAgent(
        private val handler: suspend (List<Map<String, Any>>, String) -> List<EChartsVisualization>
    ) : VisualizationAgent(
        mock(DeepseekService::class.java),
        ObjectMapper()
    ) {
        override suspend fun suggestVisualizations(data: List<Map<String, Any>>, userQuery: String): List<EChartsVisualization> {
            return handler(data, userQuery)
        }
    }

    private class FakeQueryExecutorService(
        private val handler: (String, Long, Long?) -> List<Map<String, Any>>
    ) : QueryExecutorService(
        mock(JdbcTemplate::class.java),
        mock(ExternalJdbcTemplateProvider::class.java)
    ) {
        override fun executeQuery(sql: String, dataSourceId: Long, userId: Long?): List<Map<String, Any>> {
            return handler(sql, dataSourceId, userId)
        }
    }

    private class FakeRawTextDataParserService(
        private val handler: (String) -> List<Map<String, Any>>
    ) : RawTextDataParserService(ObjectMapper()) {
        override fun parse(rawText: String): List<Map<String, Any>> = handler(rawText)
    }

    private class FakeNaturalLanguageDataExtractionService(
        private val handler: suspend (String) -> List<Map<String, Any>>
    ) : NaturalLanguageDataExtractionService(
        mock(DeepseekService::class.java),
        ObjectMapper()
    ) {
        override suspend fun extract(rawText: String): List<Map<String, Any>> = handler(rawText)
    }

    private class RecordingAnalysisTaskService(
        private val taskId: Long
    ) : AnalysisTaskService(
        mock(AnalysisTaskRepository::class.java)
    ) {
        val runningStages = mutableListOf<StageCall>()
        var successCall: SuccessCall? = null
        var failedCall: FailedCall? = null

        override fun createTask(request: AnalysisRequest): AnalysisTask {
            return AnalysisTaskDraft.`$`.produce {
                id = taskId
                originalQuery = request.query
                generatedSql = null
                parameters = emptyList()
                status = AnalysisStatus.PENDING
                result = null
                executionTime = null
                errorMessage = null
                user {
                    id = request.userId
                }
            }
        }

        override fun markRunning(taskId: Long, stage: String, details: Map<String, Any>, generatedSql: String?) {
            runningStages += StageCall(taskId, stage, details, generatedSql)
        }

        override fun markSuccess(taskId: Long, result: AnalysisResult, executionTime: Long, stage: String, details: Map<String, Any>) {
            successCall = SuccessCall(taskId, result, executionTime, stage, details)
        }

        override fun markFailed(taskId: Long, stage: String, errorMessage: String, executionTime: Long, details: Map<String, Any>) {
            failedCall = FailedCall(taskId, stage, errorMessage, executionTime, details)
        }
    }

    private data class StageCall(
        val taskId: Long,
        val stage: String,
        val details: Map<String, Any>,
        val generatedSql: String?
    )

    private data class SuccessCall(
        val taskId: Long,
        val result: AnalysisResult,
        val executionTime: Long,
        val stage: String,
        val details: Map<String, Any>
    )

    private data class FailedCall(
        val taskId: Long,
        val stage: String,
        val errorMessage: String,
        val executionTime: Long,
        val details: Map<String, Any>
    )
}
