package top.phj233.smartdatainsightagent.service.admin

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.AnalysisTask
import top.phj233.smartdatainsightagent.entity.DataSource
import top.phj233.smartdatainsightagent.entity.User
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.entity.enums.DataSourceType
import top.phj233.smartdatainsightagent.model.admin.*
import top.phj233.smartdatainsightagent.repository.AnalysisTaskRepository
import top.phj233.smartdatainsightagent.repository.DataSourceRepository
import top.phj233.smartdatainsightagent.repository.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 管理端数据可视化服务，负责聚合用户、数据源和分析任务统计。
 *
 * @author phj233
 * @since 2026/4/26
 */
@Service
class AdminVisualizationService(
    private val userRepository: UserRepository,
    private val dataSourceRepository: DataSourceRepository,
    private val analysisTaskRepository: AnalysisTaskRepository
) {
    private val logger = LoggerFactory.getLogger(AdminVisualizationService::class.java)

    /**
     * 当前时间源，测试中可替换以稳定验证趋势窗口。
     */
    internal var clock: Clock = Clock.systemDefaultZone()

    /**
     * 生成管理端可视化看板数据。
     *
     * @param days 任务趋势覆盖天数，范围限制为 1 到 90
     * @param recentFailureLimit 最近失败任务数量，范围限制为 1 到 20
     * @return 管理端数据可视化看板响应
     */
    fun dashboard(days: Int, recentFailureLimit: Int): AdminVisualizationDashboard {
        val normalizedDays = days.coerceIn(MIN_TREND_DAYS, MAX_TREND_DAYS)
        val normalizedFailureLimit = recentFailureLimit.coerceIn(MIN_FAILURE_LIMIT, MAX_FAILURE_LIMIT)
        logger.info(
            "[管理端可视化服务] 生成看板统计，days={}, recentFailureLimit={}",
            normalizedDays,
            normalizedFailureLimit
        )

        val users = userRepository.findAll()
        val dataSources = dataSourceRepository.findAll()
        val analysisTasks = analysisTaskRepository.findAll()

        val totals = buildTotals(users, dataSources, analysisTasks)
        return AdminVisualizationDashboard(
            generatedAt = clock.millis(),
            trendDays = normalizedDays,
            totals = totals,
            taskStatusDistribution = buildTaskStatusDistribution(analysisTasks),
            dataSourceTypeDistribution = buildDataSourceTypeDistribution(dataSources),
            taskTrend = buildTaskTrend(analysisTasks, normalizedDays),
            dataSourceActivity = AdminDataSourceActivityMetric(
                active = totals.activeDataSources,
                inactive = totals.inactiveDataSources
            ),
            taskExecution = buildTaskExecution(analysisTasks),
            recentFailures = buildRecentFailures(analysisTasks, normalizedFailureLimit)
        )
    }

    /**
     * 生成总览卡片统计。
     *
     * @param users 用户列表
     * @param dataSources 数据源列表
     * @param analysisTasks 分析任务列表
     * @return 总览统计
     */
    private fun buildTotals(
        users: List<User>,
        dataSources: List<DataSource>,
        analysisTasks: List<AnalysisTask>
    ): AdminVisualizationTotals {
        val enabledUsers = users.count { it.enabled }.toLong()
        val activeDataSources = dataSources.count { it.active }.toLong()
        val taskCounts = analysisTasks.groupingBy { it.status }.eachCount()
        return AdminVisualizationTotals(
            totalUsers = users.size.toLong(),
            enabledUsers = enabledUsers,
            disabledUsers = users.size.toLong() - enabledUsers,
            totalDataSources = dataSources.size.toLong(),
            activeDataSources = activeDataSources,
            inactiveDataSources = dataSources.size.toLong() - activeDataSources,
            totalAnalysisTasks = analysisTasks.size.toLong(),
            successAnalysisTasks = taskCounts.countOf(AnalysisStatus.SUCCESS),
            failedAnalysisTasks = taskCounts.countOf(AnalysisStatus.FAILED),
            runningAnalysisTasks = taskCounts.countOf(AnalysisStatus.RUNNING),
            pendingAnalysisTasks = taskCounts.countOf(AnalysisStatus.PENDING)
        )
    }

    /**
     * 生成任务状态分布，并补齐零值状态。
     *
     * @param analysisTasks 分析任务列表
     * @return 任务状态分布
     */
    private fun buildTaskStatusDistribution(analysisTasks: List<AnalysisTask>): List<AdminTaskStatusMetric> {
        val taskCounts = analysisTasks.groupingBy { it.status }.eachCount()
        return AnalysisStatus.entries.map { status ->
            AdminTaskStatusMetric(
                status = status,
                count = taskCounts.countOf(status)
            )
        }
    }

    /**
     * 生成数据源类型分布，并补齐零值类型。
     *
     * @param dataSources 数据源列表
     * @return 数据源类型分布
     */
    private fun buildDataSourceTypeDistribution(dataSources: List<DataSource>): List<AdminDataSourceTypeMetric> {
        val typeCounts = dataSources.groupingBy { it.type }.eachCount()
        return DataSourceType.entries.map { type ->
            AdminDataSourceTypeMetric(
                type = type,
                count = typeCounts.countOf(type)
            )
        }
    }

    /**
     * 生成近 N 天任务趋势。
     *
     * @param analysisTasks 分析任务列表
     * @param days 趋势天数
     * @return 每日任务趋势点
     */
    private fun buildTaskTrend(analysisTasks: List<AnalysisTask>, days: Int): List<AdminDailyTaskTrendPoint> {
        val zoneId = clock.zone
        val today = LocalDate.now(clock)
        val startDate = today.minusDays(days - 1L)
        val grouped = analysisTasks
            .map { task -> task.createdDate(zoneId) to task.status }
            .filter { (date) -> !date.isBefore(startDate) && !date.isAfter(today) }
            .groupingBy { it }
            .eachCount()

        return (0 until days).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            AdminDailyTaskTrendPoint(
                date = date.toString(),
                total = AnalysisStatus.entries.sumOf { status -> grouped.countOf(date to status) },
                success = grouped.countOf(date to AnalysisStatus.SUCCESS),
                failed = grouped.countOf(date to AnalysisStatus.FAILED),
                running = grouped.countOf(date to AnalysisStatus.RUNNING),
                pending = grouped.countOf(date to AnalysisStatus.PENDING)
            )
        }
    }

    /**
     * 生成任务执行耗时和结果率统计。
     *
     * @param analysisTasks 分析任务列表
     * @return 任务执行统计
     */
    private fun buildTaskExecution(analysisTasks: List<AnalysisTask>): AdminTaskExecutionMetric {
        val executionTimes = analysisTasks.mapNotNull { it.executionTime }
        val totalTasks = analysisTasks.size.toDouble()
        return AdminTaskExecutionMetric(
            averageExecutionTimeMillis = executionTimes.takeIf { it.isNotEmpty() }?.average(),
            maxExecutionTimeMillis = executionTimes.maxOrNull(),
            minExecutionTimeMillis = executionTimes.minOrNull(),
            successRate = ratio(analysisTasks.count { it.status == AnalysisStatus.SUCCESS }, totalTasks),
            failureRate = ratio(analysisTasks.count { it.status == AnalysisStatus.FAILED }, totalTasks)
        )
    }

    /**
     * 生成最近失败任务摘要。
     *
     * @param analysisTasks 分析任务列表
     * @param limit 返回数量上限
     * @return 最近失败任务列表
     */
    private fun buildRecentFailures(analysisTasks: List<AnalysisTask>, limit: Int): List<AdminRecentFailedTaskMetric> {
        return analysisTasks
            .asSequence()
            .filter { it.status == AnalysisStatus.FAILED }
            .sortedByDescending { it.createdTimeStamp }
            .take(limit)
            .map { task ->
                AdminRecentFailedTaskMetric(
                    id = task.id,
                    userId = task.userId,
                    name = task.name,
                    originalQuery = task.originalQuery,
                    errorMessage = task.errorMessage,
                    createdTimeStamp = task.createdTimeStamp
                )
            }
            .toList()
    }

    /**
     * 将指定任务的创建时间转换为本地日期。
     *
     * @param zoneId 时区
     * @return 创建日期
     */
    private fun AnalysisTask.createdDate(zoneId: ZoneId): LocalDate {
        return Instant.ofEpochMilli(createdTimeStamp).atZone(zoneId).toLocalDate()
    }

    /**
     * 从聚合结果中安全读取计数。
     *
     * @param key 聚合键
     * @return 对应键的数量，缺失时返回 0
     */
    private fun <K> Map<K, Int>.countOf(key: K): Long {
        return (this[key] ?: 0).toLong()
    }

    /**
     * 安全计算比例。
     *
     * @param numerator 分子
     * @param denominator 分母
     * @return 比例值，分母为 0 时返回 0.0
     */
    private fun ratio(numerator: Int, denominator: Double): Double {
        if (denominator == 0.0) {
            return 0.0
        }
        return numerator / denominator
    }

    /**
     * 可视化查询参数边界，避免前端误传导致响应过大。
     */
    private companion object {
        private const val MIN_TREND_DAYS = 1
        private const val MAX_TREND_DAYS = 90
        private const val MIN_FAILURE_LIMIT = 1
        private const val MAX_FAILURE_LIMIT = 20
    }
}
