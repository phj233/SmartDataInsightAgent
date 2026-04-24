package top.phj233.smartdatainsightagent.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.phj233.smartdatainsightagent.entity.AnalysisTask
import top.phj233.smartdatainsightagent.entity.copy
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskCreateInput
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskDetailView
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskSummaryView
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.exception.AnalysisTaskException
import top.phj233.smartdatainsightagent.model.AnalysisRequest
import top.phj233.smartdatainsightagent.model.AnalysisResult
import top.phj233.smartdatainsightagent.model.AnalysisTaskProgressEvent
import top.phj233.smartdatainsightagent.model.AnalysisTaskStageRecord
import top.phj233.smartdatainsightagent.repository.AnalysisTaskRepository
import top.phj233.smartdatainsightagent.service.ai.DeepseekService
import top.phj233.smartdatainsightagent.util.withMdc
import top.phj233.smartdatainsightagent.util.withMdcSuspend
import java.time.Instant

/**
 * 分析任务服务
 * @author phj233
 * @since 2026/3/10 17:30
 * @version
 */
@Service
class AnalysisTaskService(
    private val analysisTaskRepository: AnalysisTaskRepository,
    private val progressNotifier: AnalysisTaskProgressNotifier? = null,
    private val deepseekService: DeepseekService? = null,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {
    private val logger = LoggerFactory.getLogger(AnalysisTaskService::class.java)

    /**
     * 创建新的分析任务，并初始化任务的基础状态与第一条阶段记录。
     *
     * @param request 分析请求，包含用户、查询文本以及可选的数据源信息
     * @return 已持久化的分析任务实体
     * @throws AnalysisTaskException 当请求参数不合法时抛出
     */
    @Transactional
    fun createTask(request: AnalysisRequest): AnalysisTask {
        logger.info("[任务服务] 创建分析任务，userId={}, dataSourceId={}, queryLength={}", request.userId, request.dataSourceId, request.query.length)
        validateCreateRequest(request)
        val input = AnalysisTaskCreateInput(
            name = defaultTaskName(request.query),
            originalQuery = request.query
        )
        val created = analysisTaskRepository.save(
            input.toEntity {
                user {
                    id = request.userId
                }
                parameters = listOf(stageEntry(STAGE_TASK_CREATED, initialDetails(request)))
                status = AnalysisStatus.PENDING
            },
            SaveMode.INSERT_ONLY
        )
        return withMdc("taskId" to created.id.toString()) {
            publishProgress(
                taskId = created.id,
                status = AnalysisStatus.PENDING,
                stage = STAGE_TASK_CREATED,
                details = created.parameters.lastOrNull()?.details ?: emptyMap()
            )
            created
        }
    }

    /**
     * 将分析任务标记为运行中，并追加当前阶段轨迹。
     *
     * @param taskId 任务ID
     * @param stage 当前执行阶段
     * @param details 阶段附加信息
     * @param generatedSql 当前阶段若已生成 SQL，可一并写入
     * @throws AnalysisTaskException 当任务不存在或任务已结束时抛出
     */
    @Transactional
    fun markRunning(
        taskId: Long,
        stage: String,
        details: Map<String, JsonNode> = emptyMap(),
        generatedSql: String? = null
    ) {
        logger.info("[任务服务] 任务进入运行态，taskId={}, stage={}", taskId, stage)
        applyUpdate(
            taskId,
            TaskUpdateCommand(
                stage = stage,
                status = AnalysisStatus.RUNNING,
                details = details,
                generatedSql = generatedSql
            )
        )
    }

    /**
     * 将分析任务标记为成功，并写入最终结果、耗时和完成阶段信息。
     *
     * @param taskId 任务ID
     * @param result 最终分析结果
     * @param executionTime 任务执行耗时，单位毫秒
     * @param stage 成功时对应的阶段，默认使用完成阶段
     * @param details 成功阶段的附加信息
     * @throws AnalysisTaskException 当任务不存在或任务已结束时抛出
     */
    @Transactional
    fun markSuccess(
        taskId: Long,
        result: AnalysisResult,
        executionTime: Long,
        stage: String = STAGE_COMPLETED,
        details: Map<String, JsonNode> = emptyMap()
    ) {
        logger.info("[任务服务] 任务执行成功，taskId={}, stage={}, executionTimeMs={}", taskId, stage, executionTime)
        applyUpdate(
            taskId,
            TaskUpdateCommand(
                stage = stage,
                status = AnalysisStatus.SUCCESS,
                details = details + successDetails(result),
                generatedSql = result.sqlQuery,
                result = listOf(result),
                executionTime = executionTime,
                errorMessage = null
            )
        )
    }

    /**
     * 将分析任务标记为失败，并写入失败原因、耗时及出错阶段。
     *
     * @param taskId 任务ID
     * @param stage 发生错误时所在的阶段
     * @param errorMessage 错误信息
     * @param executionTime 任务执行耗时，单位毫秒
     * @param details 失败阶段的附加信息
     * @throws AnalysisTaskException 当任务不存在或任务已结束时抛出
     */
    @Transactional
    fun markFailed(
        taskId: Long,
        stage: String,
        errorMessage: String,
        executionTime: Long,
        details: Map<String, JsonNode> = emptyMap()
    ) {
        logger.warn("[任务服务] 任务执行失败，taskId={}, stage={}, executionTimeMs={}, error={}", taskId, stage, executionTime, errorMessage)
        applyUpdate(
            taskId,
            TaskUpdateCommand(
                stage = stage,
                status = AnalysisStatus.FAILED,
                details = details + mapOf("errorMessage" to toJsonNode(errorMessage)),
                executionTime = executionTime,
                errorMessage = errorMessage
            )
        )
    }

    /**
     * 当任务持久化失败或状态更新失败时，兜底推送失败事件，确保前端及时感知并结束 SSE。
     */
    fun notifyFailureFallback(
        taskId: Long,
        stage: String,
        errorMessage: String,
        details: Map<String, JsonNode> = emptyMap()
    ) {
        val existing = analysisTaskRepository.findNullable(taskId)
        if (existing?.status in TERMINAL_STATUSES) {
            logger.info("[任务服务] 任务已终态，跳过失败兜底通知，taskId={}, status={}", taskId, existing?.status)
            return
        }
        logger.warn("[任务服务] 触发失败事件兜底通知，taskId={}, stage={}, error={}", taskId, stage, errorMessage)
        publishProgress(
            taskId = taskId,
            status = AnalysisStatus.FAILED,
            stage = stage,
            details = details + mapOf("errorMessage" to toJsonNode(errorMessage)),
            errorMessage = errorMessage
        )
    }

    /**
     * 查询当前用户的任务摘要列表。
     *
     * 当 `status` 为空时返回该用户的全部任务摘要；否则按状态过滤。
     *
     * @param userId 当前用户ID
     * @param status 可选的任务状态过滤条件
     * @return 任务摘要视图列表
     * @see AnalysisTaskSummaryView
     */
    @Transactional(readOnly = true)
    fun listTaskSummaries(userId: Long, status: AnalysisStatus? = null): List<AnalysisTaskSummaryView> {
        logger.info("[任务服务] 列出任务摘要，userId={}, status={}", userId, status)
        return status?.let { analysisTaskRepository.findSummaryViewsByUserIdAndStatus(userId, it) }
            ?: analysisTaskRepository.findSummaryViewsByUserId(userId)
    }

    /**
     * 查询当前用户可访问的任务详情。
     *
     * @param taskId 任务ID
     * @param userId 当前用户ID
     * @return 任务详情视图
     * @see AnalysisTaskDetailView
     * @throws AnalysisTaskException 当任务不存在时抛出
     */
    @Transactional(readOnly = true)
    fun getTaskDetail(taskId: Long, userId: Long): AnalysisTaskDetailView {
        logger.info("[任务服务] 查询任务详情，userId={}, taskId={}", userId, taskId)
        return analysisTaskRepository.findDetailViewByIdAndUserId(taskId, userId)
            ?: throw AnalysisTaskException.taskNotFound("分析任务不存在: $taskId")
    }

    /**
     * 在失败任务上直接重开分析，不新建任务。
     */
    @Transactional
    fun reopenFailedTask(taskId: Long, userId: Long): AnalysisRequest {
        return reopenFailedTask(taskId, userId, null)
    }

    /**
     * 在失败任务上重新分析，允许用户修改 query 后沿用原任务重试。
     *
     * @param taskId 任务ID
     * @param userId 当前用户ID
     * @param overrideQuery 可选的新 query，传空时沿用原 query
     * @return 重开的分析请求
     */
    @Transactional
    fun reopenFailedTask(taskId: Long, userId: Long, overrideQuery: String?): AnalysisRequest {
        return withMdc("taskId" to taskId.toString(), "userId" to userId.toString()) {
            val existing = findOwnedTask(taskId, userId)
            if (existing.status != AnalysisStatus.FAILED) {
                throw AnalysisTaskException.invalidTaskRequest("仅失败任务支持重新分析")
            }

            val query = resolveRetryQuery(existing.originalQuery, overrideQuery)
            val request = AnalysisRequest(
                query = query,
                dataSourceId = extractDataSourceId(existing),
                userId = userId
            )
            val queryUpdated = query != existing.originalQuery

            val updated = existing.copy {
                status = AnalysisStatus.PENDING
                if (queryUpdated) {
                    originalQuery = query
                    if (shouldRefreshTaskName(existing)) {
                        name = defaultTaskName(query)
                    }
                }
                generatedSql = null
                result = null
                executionTime = null
                errorMessage = null
                parameters = appendStage(
                    existing.parameters,
                    STAGE_REANALYZE_REQUESTED,
                    reanalyzeDetails(request, queryUpdated)
                )
            }
            analysisTaskRepository.save(updated, SaveMode.UPDATE_ONLY)

            publishProgress(
                taskId = taskId,
                status = AnalysisStatus.PENDING,
                stage = STAGE_REANALYZE_REQUESTED,
                details = updated.parameters.lastOrNull()?.details ?: emptyMap()
            )

            logger.info(
                "[任务服务] 失败任务已重开，taskId={}, dataSourceId={}, queryUpdated={}",
                taskId,
                request.dataSourceId,
                queryUpdated
            )
            request
        }
    }

    /**
     * 重命名当前用户可访问的分析任务。
     */
    @Transactional
    fun renameTask(taskId: Long, userId: Long, name: String): AnalysisTaskDetailView {
        return withMdc("taskId" to taskId.toString()) {
            logger.info("[任务服务] 重命名任务，userId={}, taskId={}, name={}", userId, taskId, name)
            val normalizedName = name.trim()
            if (normalizedName.isBlank()) {
                throw AnalysisTaskException.invalidTaskRequest("任务名称不能为空")
            }

            val existing = findOwnedTask(taskId, userId)
            val updated = existing.copy {
                this.name = normalizedName
            }
            analysisTaskRepository.save(updated, SaveMode.UPDATE_ONLY)

            analysisTaskRepository.findDetailViewByIdAndUserId(taskId, userId)
                ?: throw AnalysisTaskException.taskNotFound("分析任务不存在: $taskId")
        }
    }

    /**
     * 使用 LLM 为任务生成更合适的名称并重命名。
     */
    @Transactional
    suspend fun renameTaskByLlm(taskId: Long, userId: Long): AnalysisTaskDetailView {
        return withMdcSuspend("taskId" to taskId.toString()) {
            val existing = findOwnedTask(taskId, userId)
            val llmName = generateNameByLlm(existing.originalQuery, existing.name)
            logger.info("[任务服务] LLM重命名任务，userId={}, taskId={}, generatedName={}", userId, taskId, llmName)
            withContext(Dispatchers.IO) {
                renameTask(taskId, userId, llmName)
            }
        }
    }

    /**
     * 删除当前用户可访问的分析任务。
     */
    @Transactional
    fun deleteTask(taskId: Long, userId: Long) {
        withMdc("taskId" to taskId.toString()) {
            logger.info("[任务服务] 删除任务，userId={}, taskId={}", userId, taskId)
            val affectedRows = analysisTaskRepository.deleteByIdAndUserId(taskId, userId)
            if (affectedRows == 0) {
                throw AnalysisTaskException.taskNotFound("分析任务不存在: $taskId")
            }
        }
    }

    /**
     * 根据更新命令统一更新分析任务状态、阶段轨迹以及相关结果字段。
     *
     * @param taskId 任务ID
     * @param command 任务更新命令
     * @throws AnalysisTaskException 当任务不存在或状态流转非法时抛出
     */
    private fun applyUpdate(taskId: Long, command: TaskUpdateCommand) {
        withMdc("taskId" to taskId.toString()) {
            logger.debug("[任务服务] 应用任务状态更新，taskId={}, targetStatus={}, stage={}", taskId, command.status, command.stage)
            val existing = findTask(taskId)
            withMdc("userId" to existing.userId.toString()) {
                validateTransition(existing, command.status)

                val updatedTask = existing.copy {
                    status = command.status
                    parameters = appendStage(existing.parameters, command.stage, command.details)
                    generatedSql = command.generatedSql ?: generatedSql
                    result = command.result ?: result
                    executionTime = command.executionTime ?: executionTime
                    errorMessage = command.errorMessage ?: errorMessage
                }

                analysisTaskRepository.save(updatedTask, SaveMode.UPDATE_ONLY)

                publishProgress(
                    taskId = taskId,
                    status = command.status,
                    stage = command.stage,
                    details = command.details,
                    generatedSql = command.generatedSql,
                    errorMessage = command.errorMessage
                )
            }
        }
    }

    /**
     * 推送任务进度事件到通知系统，包含当前状态、阶段、附加详情以及可选的生成 SQL 和错误信息。
     * @param taskId 任务ID
     * @param status 当前任务状态
     * @param stage 当前执行阶段
     * @param details 当前阶段的附加信息
     * @param generatedSql 当前阶段若已生成 SQL，可一并传入
     * @param errorMessage 当前阶段若发生错误，可一并传入错误信息
     * @throws Exception 当通知系统发生错误时抛出（调用方会捕获并记录日志，但不会中断任务执行）
     */
    private fun publishProgress(
        taskId: Long,
        status: AnalysisStatus,
        stage: String,
        details: Map<String, JsonNode>,
        generatedSql: String? = null,
        errorMessage: String? = null
    ) {
        try {
            progressNotifier?.notify(
                AnalysisTaskProgressEvent(
                    taskId = taskId,
                    status = status,
                    stage = stage,
                    timestamp = nowTimestamp(),
                    details = details,
                    generatedSql = generatedSql,
                    errorMessage = errorMessage
                )
            )
        } catch (ex: Exception) {
            logger.warn("[任务服务] 推送任务进度失败(已忽略)，taskId={}, status={}, stage={}", taskId, status, stage, ex)
        }
    }

    /**
     * 根据任务ID查找任务。
     *
     * @param taskId 任务ID
     * @return 对应的分析任务实体
     * @throws AnalysisTaskException 当任务不存在时抛出
     */
    private fun findTask(taskId: Long): AnalysisTask {
        return analysisTaskRepository.findNullable(taskId)
            ?: throw AnalysisTaskException.taskNotFound("分析任务不存在: $taskId")
    }

    private fun findOwnedTask(taskId: Long, userId: Long): AnalysisTask {
        return analysisTaskRepository.findByIdAndUserId(taskId, userId)
            ?: throw AnalysisTaskException.taskNotFound("分析任务不存在: $taskId")
    }

    /**
     * 校验创建任务请求是否合法。
     *
     * 当前要求：
     * - `query` 不能为空或纯空白字符
     *
     * @param request 分析请求
     * @throws AnalysisTaskException 当参数不合法时抛出
     */
    private fun validateCreateRequest(request: AnalysisRequest) {
        if (request.query.isBlank()) {
            throw AnalysisTaskException.invalidTaskRequest("分析任务创建参数无效")
        }
    }

    /**
     * 校验任务状态流转是否合法。
     *
     * 当前约束：
     * - 已进入终态（成功/失败）的任务不能再次更新
     * - 任务状态不能回退为 `PENDING`
     *
     * @param existing 当前任务
     * @param targetStatus 目标状态
     * @throws AnalysisTaskException 当状态流转不合法时抛出
     */
    private fun validateTransition(existing: AnalysisTask, targetStatus: AnalysisStatus) {
        if (existing.status in TERMINAL_STATUSES) {
            throw AnalysisTaskException.taskAlreadyFinished(
                "分析任务 ${existing.id} 已结束，当前状态为 ${existing.status.name}"
            )
        }
        if (targetStatus == AnalysisStatus.PENDING) {
            throw AnalysisTaskException.invalidTaskRequest("分析任务状态不能回退为 PENDING")
        }
    }

    /**
     * 追加新的阶段记录，保留历史阶段轨迹。
     *
     * @param existingStages 已有阶段记录列表
     * @param stage 当前阶段
     * @param details 当前阶段附加信息
     * @return 追加后的阶段记录列表
     */
    private fun appendStage(
        existingStages: List<AnalysisTaskStageRecord>,
        stage: String,
        details: Map<String, JsonNode>
    ): List<AnalysisTaskStageRecord> {
        return existingStages + stageEntry(stage, details)
    }

    /**
     * 构建任务创建阶段的初始详情信息。
     *
     * @param request 分析请求
     * @return 创建阶段的详情信息
     */
    private fun initialDetails(request: AnalysisRequest): Map<String, JsonNode> = buildMap {
        put("userId", toJsonNode(request.userId))
        put("query", toJsonNode(request.query))
        put("queryLength", toJsonNode(request.query.length))
        put("inputMode", toJsonNode(if (request.dataSourceId != null) "DATA_SOURCE" else "RAW_TEXT"))
        request.dataSourceId?.let { put("dataSourceId", toJsonNode(it)) }
    }

    /**
     * 构建重新分析阶段的详情信息。
     *
     * @param request 重开的分析请求
     * @param queryUpdated 本次是否修改了 query
     * @return 重试阶段的详情信息
     */
    private fun reanalyzeDetails(
        request: AnalysisRequest,
        queryUpdated: Boolean
    ): Map<String, JsonNode> = buildMap {
        put("query", toJsonNode(request.query))
        put("queryLength", toJsonNode(request.query.length))
        put("inputMode", toJsonNode(if (request.dataSourceId != null) "DATA_SOURCE" else "RAW_TEXT"))
        put("queryUpdated", toJsonNode(queryUpdated))
        request.dataSourceId?.let { put("dataSourceId", toJsonNode(it)) }
    }

    /**
     * 构建任务成功时的统计信息。
     *
     * @param result 分析结果
     * @return 成功阶段的详情信息
     */
    private fun successDetails(result: AnalysisResult): Map<String, JsonNode> = buildMap {
        put("rowCount", toJsonNode(result.data.size))
        put("visualizationCount", toJsonNode(result.visualizations.size))
        put("hasSqlQuery", toJsonNode(result.sqlQuery != null))
    }

    private fun extractDataSourceId(task: AnalysisTask): Long? {
        val createdStage = task.parameters.firstOrNull { it.stage == STAGE_TASK_CREATED }
        val node = createdStage?.details?.get("dataSourceId") ?: return null
        val value = when {
            node.isIntegralNumber -> node.asLong()
            node.isTextual -> node.asText().toLongOrNull()
            else -> null
        }
        return value?.takeIf { it > 0 }
    }

    /**
     * 构建单条阶段记录对象。
     *
     * @param stage 阶段名称
     * @param details 阶段详情
     * @return 强类型的阶段记录对象
     */
    private fun stageEntry(
        stage: String,
        details: Map<String, JsonNode>
    ): AnalysisTaskStageRecord {
        return AnalysisTaskStageRecord(
            stage = stage,
            timestamp = nowTimestamp(),
            details = details
        )
    }

    private fun nowTimestamp(): String = Instant.now().toString()

    private fun resolveRetryQuery(originalQuery: String, overrideQuery: String?): String {
        if (overrideQuery == null) {
            return originalQuery.trim().takeIf { it.isNotBlank() }
                ?: throw AnalysisTaskException.invalidTaskRequest("原任务查询为空，无法重新分析")
        }

        return overrideQuery.trim().takeIf { it.isNotBlank() }
            ?: throw AnalysisTaskException.invalidTaskRequest("重试 query 不能为空")
    }

    private fun shouldRefreshTaskName(task: AnalysisTask): Boolean {
        val currentName = task.name.trim()
        return currentName.isBlank() || currentName == defaultTaskName(task.originalQuery.trim())
    }

    private fun defaultTaskName(query: String): String = query.take(20)

    private fun toJsonNode(value: Any?): JsonNode {
        return objectMapper.valueToTree(value)
    }

    private suspend fun generateNameByLlm(originalQuery: String, fallbackName: String): String {
        val fallback = fallbackName.ifBlank { originalQuery.take(20) }
        val service = deepseekService ?: return fallback.take(20)

        val prompt = """
            你是一个任务命名助手。请根据用户分析请求生成一个简洁的任务名称。
            约束：
            1. 仅返回任务名称文本，不要解释，不要 JSON。
            2. 名称长度不超过 20 个字符。
            3. 名称应准确概括查询主题。

            用户查询：$originalQuery
        """.trimIndent()

        val generated = runCatching { service.chatCompletion(prompt) }
            .onFailure { logger.warn("[任务服务] LLM生成任务名称失败，使用兜底名称", it) }
            .getOrNull()
            ?.trim()
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.replace(Regex("\\s+"), " ")
            ?.takeIf { it.isNotBlank() }

        return (generated ?: fallback).take(20)
    }

    companion object {
        private val TERMINAL_STATUSES = setOf(AnalysisStatus.SUCCESS, AnalysisStatus.FAILED)

        const val STAGE_TASK_CREATED = "TASK_CREATED"
        const val STAGE_INTENT_ANALYZING = "INTENT_ANALYZING"
        const val STAGE_INTENT_RESOLVED = "INTENT_RESOLVED"
        const val STAGE_SQL_GENERATING = "SQL_GENERATING"
        const val STAGE_SQL_GENERATED = "SQL_GENERATED"
        const val STAGE_QUERY_EXECUTING = "QUERY_EXECUTING"
        const val STAGE_QUERY_EXECUTED = "QUERY_EXECUTED"
        const val STAGE_RAW_TEXT_PARSING = "RAW_TEXT_PARSING"
        const val STAGE_NATURAL_LANGUAGE_EXTRACTION = "NATURAL_LANGUAGE_EXTRACTION"
        const val STAGE_INSIGHTS_GENERATING = "INSIGHTS_GENERATING"
        const val STAGE_VISUALIZATION_GENERATING = "VISUALIZATION_GENERATING"
        const val STAGE_PREDICTION_GENERATING = "PREDICTION_GENERATING"
        const val STAGE_REPORT_GENERATING = "REPORT_GENERATING"
        const val STAGE_COMPLETED = "COMPLETED"
        const val STAGE_ASYNC_EXECUTION_FAILED = "ASYNC_EXECUTION_FAILED"
        const val STAGE_REANALYZE_REQUESTED = "REANALYZE_REQUESTED"
    }

    private data class TaskUpdateCommand(
        val stage: String,
        val status: AnalysisStatus,
        val details: Map<String, JsonNode> = emptyMap(),
        val generatedSql: String? = null,
        val result: List<AnalysisResult>? = null,
        val executionTime: Long? = null,
        val errorMessage: String? = null
    )
}
