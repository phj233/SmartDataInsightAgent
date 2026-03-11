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

    @GetMapping("/tasks")
    fun listTasks(@RequestParam(required = false) status: AnalysisStatus?): List<AnalysisTaskSummaryView> {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return analysisTaskService.listTaskSummaries(currentUserId, status)
    }

    @GetMapping("/tasks/{taskId}")
    fun getTaskDetail(@PathVariable taskId: Long): AnalysisTaskDetailView {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return analysisTaskService.getTaskDetail(taskId, currentUserId)
    }
}
