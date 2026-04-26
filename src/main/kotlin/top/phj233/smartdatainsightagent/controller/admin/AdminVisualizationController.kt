package top.phj233.smartdatainsightagent.controller.admin

import cn.dev33.satoken.annotation.SaCheckRole
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.phj233.smartdatainsightagent.model.admin.AdminVisualizationDashboard
import top.phj233.smartdatainsightagent.service.admin.AdminVisualizationService

/**
 * 管理端数据可视化控制器，提供后台首页图表和统计卡片所需数据。
 *
 * @author phj233
 * @since 2026/4/26
 */
@RestController
@SaCheckRole("admin")
@RequestMapping("/api/admin/visualization")
class AdminVisualizationController(
    private val adminVisualizationService: AdminVisualizationService
) {

    /**
     * 获取管理端数据可视化看板。
     *
     * @param days 近 N 天任务趋势，服务端会限制在 1 到 90 天
     * @param recentFailureLimit 最近失败任务数量，服务端会限制在 1 到 20 条
     * @return 管理端数据可视化看板
     */
    @GetMapping("/dashboard")
    fun dashboard(
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(defaultValue = "5") recentFailureLimit: Int
    ): AdminVisualizationDashboard {
        return adminVisualizationService.dashboard(days, recentFailureLimit)
    }
}
