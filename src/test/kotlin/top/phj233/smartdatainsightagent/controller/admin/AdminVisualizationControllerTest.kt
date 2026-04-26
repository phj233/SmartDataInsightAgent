package top.phj233.smartdatainsightagent.controller.admin

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import top.phj233.smartdatainsightagent.model.admin.AdminDataSourceActivityMetric
import top.phj233.smartdatainsightagent.model.admin.AdminTaskExecutionMetric
import top.phj233.smartdatainsightagent.model.admin.AdminVisualizationDashboard
import top.phj233.smartdatainsightagent.model.admin.AdminVisualizationTotals
import top.phj233.smartdatainsightagent.service.admin.AdminVisualizationService

/**
 * 管理端数据可视化控制器测试。
 */
class AdminVisualizationControllerTest {

    private val adminVisualizationService = mock(AdminVisualizationService::class.java)
    private val controller = AdminVisualizationController(adminVisualizationService)

    @Test
    fun `dashboard endpoint delegates query parameters to service`() {
        val expected = dashboard()
        `when`(adminVisualizationService.dashboard(14, 3)).thenReturn(expected)

        val result = controller.dashboard(14, 3)

        assertSame(expected, result)
        verify(adminVisualizationService).dashboard(14, 3)
    }

    /**
     * 构造最小可用的看板响应，便于验证控制器委托行为。
     *
     * @return 管理端数据可视化看板
     */
    private fun dashboard(): AdminVisualizationDashboard {
        return AdminVisualizationDashboard(
            generatedAt = 1L,
            trendDays = 7,
            totals = AdminVisualizationTotals(
                totalUsers = 0,
                enabledUsers = 0,
                disabledUsers = 0,
                totalDataSources = 0,
                activeDataSources = 0,
                inactiveDataSources = 0,
                totalAnalysisTasks = 0,
                successAnalysisTasks = 0,
                failedAnalysisTasks = 0,
                runningAnalysisTasks = 0,
                pendingAnalysisTasks = 0
            ),
            taskStatusDistribution = emptyList(),
            dataSourceTypeDistribution = emptyList(),
            taskTrend = emptyList(),
            dataSourceActivity = AdminDataSourceActivityMetric(active = 0, inactive = 0),
            taskExecution = AdminTaskExecutionMetric(
                averageExecutionTimeMillis = null,
                maxExecutionTimeMillis = null,
                minExecutionTimeMillis = null,
                successRate = 0.0,
                failureRate = 0.0
            ),
            recentFailures = emptyList()
        )
    }
}
