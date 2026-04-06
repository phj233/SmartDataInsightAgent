package top.phj233.smartdatainsightagent.controller

import cn.dev33.satoken.stp.StpUtil
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import top.phj233.smartdatainsightagent.entity.dto.*
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.model.AnalysisRequest
import top.phj233.smartdatainsightagent.model.AnalysisResult
import top.phj233.smartdatainsightagent.service.AnalysisExecutionService
import top.phj233.smartdatainsightagent.service.AnalysisSseService
import top.phj233.smartdatainsightagent.service.AnalysisTaskService
import top.phj233.smartdatainsightagent.service.agent.DataAnalysisAgent

/**
 * 分析控制器
 * @author phj233
 * @since 2026/3/11 18:25
 * @version
 */
@RestController
@Validated
@RequestMapping("/api/analysis")
class AnalysisController(
    private val dataAnalysisAgent: DataAnalysisAgent,
    private val analysisTaskService: AnalysisTaskService,
    private val analysisExecutionService: AnalysisExecutionService,
    private val analysisSseService: AnalysisSseService
) {

    /**
     * 异步创建分析任务，立即返回 taskId，分析链路在后台执行。
     * @param request 包含查询语句和数据源ID的分析执行请求
     * @return 包含新创建的分析任务ID和初始状态的响应对象
     */
    @PostMapping("/tasks")
    fun createTask(@Valid @RequestBody request: AnalysisExecuteRequest): AnalysisTaskCreateResponse {
        val currentUserId = StpUtil.getLoginIdAsLong()
        val normalizedQuery = request.query.trim()
        val analysisRequest = AnalysisRequest(
            query = normalizedQuery,
            dataSourceId = request.dataSourceId,
            userId = currentUserId
        )

        val task = analysisTaskService.createTask(analysisRequest)
        analysisExecutionService.submit(task.id, analysisRequest)

        return AnalysisTaskCreateResponse(
            taskId = task.id,
            status = task.status
        )
    }

    /**
     * 在失败任务上原地重新分析，不创建新任务。
     * @param taskId 失败的分析任务ID
     * @return 包含原任务ID和重试后状态的响应对象
     */
    @PostMapping("/tasks/{taskId}/reanalyze")
    fun reanalyzeFailedTask(@PathVariable taskId: Long): AnalysisTaskCreateResponse {
        val currentUserId = StpUtil.getLoginIdAsLong()
        val request = analysisTaskService.reopenFailedTask(taskId, currentUserId)
        analysisExecutionService.submit(taskId, request)
        return AnalysisTaskCreateResponse(taskId = taskId, status = AnalysisStatus.PENDING)
    }

    /**
     * 订阅任务进度 SSE 事件。
     * @param taskId 分析任务ID
     * @return SseEmitter 对象，前端可以通过它接收分析任务的实时进度更新事件
     */
    @GetMapping("/tasks/{taskId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribeTaskEvents(@PathVariable taskId: Long): SseEmitter {
        val currentUserId = StpUtil.getLoginIdAsLong()
        // 先校验任务归属，防止越权订阅
        analysisTaskService.getTaskDetail(taskId, currentUserId)
        return analysisSseService.subscribe(taskId)
    }

    /**
     * 执行数据分析请求
     * @param request 包含查询语句和数据源ID的分析执行请求
     * @return 分析结果，包含分析生成的SQL语句和查询结果数据
     */
    @PostMapping("/execute")
    suspend fun execute(@Valid @RequestBody request: AnalysisExecuteRequest): AnalysisResult {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return dataAnalysisAgent.analyzeData(
            AnalysisRequest(
                query = request.query.trim(),
                dataSourceId = request.dataSourceId,
                userId = currentUserId
            )
        )
    }

    /**
     * 列出当前用户的分析任务摘要列表，可以根据任务状态进行过滤
     * @param status 可选的分析任务状态过滤参数，如果提供则只返回具有该状态的任务摘要，否则返回所有任务摘要
     * @return 分析任务摘要列表
     */
    @GetMapping("/tasks")
    fun listTasks(@RequestParam(required = false) status: AnalysisStatus?): List<AnalysisTaskSummaryView> {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return analysisTaskService.listTaskSummaries(currentUserId, status)
    }

    /**
     * 获取指定分析任务的详细信息
     * @param taskId 分析任务ID
     * @return 包含分析任务详细信息的视图对象
     */
    @GetMapping("/tasks/{taskId}")
    fun getTaskDetail(@PathVariable taskId: Long): AnalysisTaskDetailView {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return analysisTaskService.getTaskDetail(taskId, currentUserId)
    }

    /**
     * 重命名指定分析任务。
     * @param taskId 分析任务ID
     * @param input 包含新任务名称的请求体对象
     * @return 包含更新后分析任务详细信息的视图对象
     */
    @PatchMapping("/tasks/{taskId}/name")
    fun renameTask(
        @PathVariable taskId: Long,
        @Valid @RequestBody input: AnalysisTaskRenameInput
    ): AnalysisTaskDetailView {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return analysisTaskService.renameTask(taskId, currentUserId, input.name)
    }

    /**
     * 使用 LLM 自动重命名指定分析任务。
     * @param taskId 分析任务ID
     * @return 包含更新后分析任务详细信息的视图对象
     */
    @PostMapping("/tasks/{taskId}/name/llm")
    suspend fun renameTaskByLlm(@PathVariable taskId: Long): AnalysisTaskDetailView {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return analysisTaskService.renameTaskByLlm(taskId, currentUserId)
    }

    /**
     * 删除指定分析任务。
     * @param taskId 分析任务ID
     * @return 无内容响应，表示删除成功
     */
    @DeleteMapping("/tasks/{taskId}")
    fun deleteTask(@PathVariable taskId: Long) {
        val currentUserId = StpUtil.getLoginIdAsLong()
        analysisTaskService.deleteTask(taskId, currentUserId)
    }
}
