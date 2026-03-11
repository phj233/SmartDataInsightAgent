package top.phj233.smartdatainsightagent.service

import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.phj233.smartdatainsightagent.entity.AnalysisTask
import top.phj233.smartdatainsightagent.entity.AnalysisTaskDraft
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskCreateInput
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskDetailView
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskSummaryView
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.exception.AnalysisTaskException
import top.phj233.smartdatainsightagent.model.AnalysisRequest
import top.phj233.smartdatainsightagent.model.AnalysisResult
import top.phj233.smartdatainsightagent.model.AnalysisTaskStageRecord
import top.phj233.smartdatainsightagent.repository.AnalysisTaskRepository
import java.time.LocalDateTime

/**
 * 分析任务服务
 * @author phj233
 * @since 2026/3/10 17:30
 * @version
 */
@Service
class AnalysisTaskService(
    private val analysisTaskRepository: AnalysisTaskRepository
) {

    /**
     * 创建新的分析任务，并初始化任务的基础状态与第一条阶段记录。
     *
     * @param request 分析请求，包含用户、查询文本以及可选的数据源信息
     * @return 已持久化的分析任务实体
     * @throws AnalysisTaskException 当请求参数不合法时抛出
     */
    @Transactional
    fun createTask(request: AnalysisRequest): AnalysisTask {
        validateCreateRequest(request)
        val input = AnalysisTaskCreateInput(request.query)
        return analysisTaskRepository.save(
            input.toEntity {
                user {
                    id = request.userId
                }
                parameters = listOf(stageEntry(STAGE_TASK_CREATED, initialDetails(request)))
                status = AnalysisStatus.PENDING
            },
            SaveMode.INSERT_ONLY
        )
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
        details: Map<String, Any> = emptyMap(),
        generatedSql: String? = null
    ) {
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
        details: Map<String, Any> = emptyMap()
    ) {
        applyUpdate(
            taskId,
            TaskUpdateCommand(
                stage = stage,
                status = AnalysisStatus.SUCCESS,
                details = details + successDetails(result),
                generatedSql = result.sqlQuery,
                result = result,
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
        details: Map<String, Any> = emptyMap()
    ) {
        applyUpdate(
            taskId,
            TaskUpdateCommand(
                stage = stage,
                status = AnalysisStatus.FAILED,
                details = details + mapOf("errorMessage" to errorMessage),
                executionTime = executionTime,
                errorMessage = errorMessage
            )
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
        return analysisTaskRepository.findDetailViewByIdAndUserId(taskId, userId)
            ?: throw AnalysisTaskException.taskNotFound("分析任务不存在: $taskId")
    }

    /**
     * 根据更新命令统一更新分析任务状态、阶段轨迹以及相关结果字段。
     *
     * @param taskId 任务ID
     * @param command 任务更新命令
     * @throws AnalysisTaskException 当任务不存在或状态流转非法时抛出
     */
    private fun applyUpdate(taskId: Long, command: TaskUpdateCommand) {
        val existing = findTask(taskId)
        validateTransition(existing, command.status)

        val updatedTask = AnalysisTaskDraft.`$`.produce(existing) {
            status = command.status
            parameters = appendStage(existing.parameters, command.stage, command.details)

            command.generatedSql?.let { generatedSql = it }
            command.result?.let { result = listOf(it) }
            command.executionTime?.let { executionTime = it }
            errorMessage = command.errorMessage
        }

        analysisTaskRepository.save(updatedTask, SaveMode.UPDATE_ONLY)
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
        details: Map<String, Any>
    ): List<AnalysisTaskStageRecord> {
        return existingStages + stageEntry(stage, details)
    }

    /**
     * 构建任务创建阶段的初始详情信息。
     *
     * @param request 分析请求
     * @return 创建阶段的详情信息
     */
    private fun initialDetails(request: AnalysisRequest): Map<String, Any> = buildMap {
        put("userId", request.userId)
        put("query", request.query)
        put("queryLength", request.query.length)
        put("inputMode", if (request.dataSourceId != null) "DATA_SOURCE" else "RAW_TEXT")
        request.dataSourceId?.let { put("dataSourceId", it) }
    }

    /**
     * 构建任务成功时的统计信息。
     *
     * @param result 分析结果
     * @return 成功阶段的详情信息
     */
    private fun successDetails(result: AnalysisResult): Map<String, Any> = buildMap {
        put("rowCount", result.data.size)
        put("visualizationCount", result.visualizations.size)
        put("hasSqlQuery", result.sqlQuery != null)
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
        details: Map<String, Any>
    ): AnalysisTaskStageRecord {
        return AnalysisTaskStageRecord(
            stage = stage,
            timestamp = LocalDateTime.now().toString(),
            details = sanitizeDetails(details)
        )
    }

    /**
     * 清洗阶段详情，确保写入 `@Serialized` 字段的数据结构稳定且可序列化。
     *
     * @param details 原始阶段详情
     * @return 清洗后的阶段详情
     */
    private fun sanitizeDetails(details: Map<String, Any>): Map<String, Any> {
        val sanitized = linkedMapOf<String, Any>()
        details.forEach { (key, value) ->
            sanitizeValue(value)?.let { sanitized[key] = it }
        }
        return sanitized
    }

    /**
     * 递归清洗单个值，尽量保留基础类型、枚举、时间和集合/映射结构，
     * 对无法直接保存的对象退化为字符串表示。
     *
     * @param value 原始值
     * @return 可安全序列化的值；若原值为 `null` 则返回 `null`
     */
    private fun sanitizeValue(value: Any?): Any? = when (value) {
        null -> null
        is String, is Number, is Boolean -> value
        is Enum<*> -> value.name
        is LocalDateTime -> value.toString()
        is Map<*, *> -> value.entries
            .filter { it.key != null && it.value != null }
            .mapNotNull { (key, nestedValue) ->
                sanitizeValue(nestedValue)?.let { key.toString() to it }
            }
            .toMap()
        is Iterable<*> -> value.mapNotNull { sanitizeValue(it) }
        else -> value.toString()
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
    }

    private data class TaskUpdateCommand(
        val stage: String,
        val status: AnalysisStatus,
        val details: Map<String, Any> = emptyMap(),
        val generatedSql: String? = null,
        val result: AnalysisResult? = null,
        val executionTime: Long? = null,
        val errorMessage: String? = null
    )
}
