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

/**
 * 管理员分析任务控制器
 * @author phj233
 * @since 2026/4/22 19:07
 */
@RestController
@SaCheckRole("admin")
@RequestMapping("/api/admin/analysis-tasks")
class AdminAnalysisTaskController(
    private val adminAnalysisTaskService: AdminAnalysisTaskService
) {

    /**
     * 列出分析任务列表，支持分页。
     * @param page 页码，从0开始
     * @param size 每页大小
     * @return 分页的分析任务摘要列表
     */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): Page<AnalysisTaskSummaryView> {
        return adminAnalysisTaskService.list(PageRequest.of(page, size))
    }


    /**
     * 获取分析任务详情。
     * - 管理员可以查看所有用户的分析任务详情，包括成功和失败的任务。
     * @param id 分析任务ID
     * @return 分析任务详情视图
     */
    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): AnalysisTaskDetailView {
        return adminAnalysisTaskService.detail(id)
    }

    /**
     * 更新失败的分析任务。
     * @param id 分析任务ID
     * @param input 更新输入参数
     * @return 更新后的分析任务详情视图
     */
    @PatchMapping("/{id}")
    fun updateFailedTask(
        @PathVariable id: Long,
        @Valid @RequestBody input: AdminFailedAnalysisTaskUpdateInput
    ): AnalysisTaskDetailView {
        return adminAnalysisTaskService.updateFailedTask(id, input)
    }

    /**
     * 删除失败的分析任务。
     * - 仅允许删除状态为失败的分析任务，成功的分析任务不允许删除。
     * @param id 分析任务ID
     */
    @DeleteMapping("/{id}")
    fun deleteFailedTask(@PathVariable id: Long) {
        adminAnalysisTaskService.deleteFailedTask(id)
    }
}
