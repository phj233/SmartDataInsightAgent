package top.phj233.smartdatainsightagent.controller

import cn.dev33.satoken.stp.StpUtil
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskDetailView
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskSummaryView
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.model.AnalysisExecuteRequest
import top.phj233.smartdatainsightagent.model.AnalysisRequest
import top.phj233.smartdatainsightagent.model.AnalysisResult
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
    private val analysisTaskService: AnalysisTaskService
) {

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
}
