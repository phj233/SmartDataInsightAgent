package top.phj233.smartdatainsightagent.controller.admin

import cn.dev33.satoken.annotation.SaCheckRole
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.*
import top.phj233.smartdatainsightagent.entity.dto.AdminFailedAnalysisTaskUpdateInput
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskDetailView
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskSummaryView
import top.phj233.smartdatainsightagent.service.admin.AdminAnalysisTaskService

@RestController
@SaCheckRole("admin")
@RequestMapping("/api/admin/analysis-tasks")
class AdminAnalysisTaskController(
    private val adminAnalysisTaskService: AdminAnalysisTaskService
) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): Page<AnalysisTaskSummaryView> {
        return adminAnalysisTaskService.list(PageRequest.of(page, size))
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): AnalysisTaskDetailView {
        return adminAnalysisTaskService.detail(id)
    }

    @PatchMapping("/{id}")
    fun updateFailedTask(
        @PathVariable id: Long,
        @Valid @RequestBody input: AdminFailedAnalysisTaskUpdateInput
    ): AnalysisTaskDetailView {
        return adminAnalysisTaskService.updateFailedTask(id, input)
    }

    @DeleteMapping("/{id}")
    fun deleteFailedTask(@PathVariable id: Long) {
        adminAnalysisTaskService.deleteFailedTask(id)
    }
}
