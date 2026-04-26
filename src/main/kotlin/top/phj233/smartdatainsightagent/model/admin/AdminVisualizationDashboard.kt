package top.phj233.smartdatainsightagent.model.admin

import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.entity.enums.DataSourceType

/**
 * 管理端数据可视化看板响应，用于集中承载首页统计卡片和图表数据。
 *
 * @property generatedAt 统计生成时间戳，单位为毫秒
 * @property trendDays 任务趋势覆盖的天数
 * @property totals 管理端核心资源总览
 * @property taskStatusDistribution 分析任务状态分布
 * @property dataSourceTypeDistribution 数据源类型分布
 * @property taskTrend 近 N 天分析任务趋势
 * @property dataSourceActivity 数据源启用状态统计
 * @property taskExecution 分析任务执行耗时统计
 * @property recentFailures 最近失败任务列表
 */
data class AdminVisualizationDashboard(
    val generatedAt: Long,
    val trendDays: Int,
    val totals: AdminVisualizationTotals,
    val taskStatusDistribution: List<AdminTaskStatusMetric>,
    val dataSourceTypeDistribution: List<AdminDataSourceTypeMetric>,
    val taskTrend: List<AdminDailyTaskTrendPoint>,
    val dataSourceActivity: AdminDataSourceActivityMetric,
    val taskExecution: AdminTaskExecutionMetric,
    val recentFailures: List<AdminRecentFailedTaskMetric>
)

/**
 * 管理端核心资源总览，适合前端展示为统计卡片。
 *
 * @property totalUsers 用户总数
 * @property enabledUsers 已启用用户数
 * @property disabledUsers 已禁用用户数
 * @property totalDataSources 数据源总数
 * @property activeDataSources 已启用数据源数
 * @property inactiveDataSources 已停用数据源数
 * @property totalAnalysisTasks 分析任务总数
 * @property successAnalysisTasks 成功分析任务数
 * @property failedAnalysisTasks 失败分析任务数
 * @property runningAnalysisTasks 运行中分析任务数
 * @property pendingAnalysisTasks 等待执行分析任务数
 */
data class AdminVisualizationTotals(
    val totalUsers: Long,
    val enabledUsers: Long,
    val disabledUsers: Long,
    val totalDataSources: Long,
    val activeDataSources: Long,
    val inactiveDataSources: Long,
    val totalAnalysisTasks: Long,
    val successAnalysisTasks: Long,
    val failedAnalysisTasks: Long,
    val runningAnalysisTasks: Long,
    val pendingAnalysisTasks: Long
)

/**
 * 分析任务状态分布指标。
 *
 * @property status 分析任务状态
 * @property count 对应状态的任务数量
 */
data class AdminTaskStatusMetric(
    val status: AnalysisStatus,
    val count: Long
)

/**
 * 数据源类型分布指标。
 *
 * @property type 数据源类型
 * @property count 对应类型的数据源数量
 */
data class AdminDataSourceTypeMetric(
    val type: DataSourceType,
    val count: Long
)

/**
 * 单日分析任务趋势点，用于折线图或堆叠柱状图。
 *
 * @property date 日期，格式为 yyyy-MM-dd
 * @property total 当日任务总数
 * @property success 当日成功任务数
 * @property failed 当日失败任务数
 * @property running 当日运行中任务数
 * @property pending 当日等待执行任务数
 */
data class AdminDailyTaskTrendPoint(
    val date: String,
    val total: Long,
    val success: Long,
    val failed: Long,
    val running: Long,
    val pending: Long
)

/**
 * 数据源启用状态统计。
 *
 * @property active 已启用数据源数
 * @property inactive 已停用数据源数
 */
data class AdminDataSourceActivityMetric(
    val active: Long,
    val inactive: Long
)

/**
 * 分析任务执行耗时和成功率统计。
 *
 * @property averageExecutionTimeMillis 平均执行耗时，单位为毫秒
 * @property maxExecutionTimeMillis 最大执行耗时，单位为毫秒
 * @property minExecutionTimeMillis 最小执行耗时，单位为毫秒
 * @property successRate 任务成功率，范围为 0.0 到 1.0
 * @property failureRate 任务失败率，范围为 0.0 到 1.0
 */
data class AdminTaskExecutionMetric(
    val averageExecutionTimeMillis: Double?,
    val maxExecutionTimeMillis: Long?,
    val minExecutionTimeMillis: Long?,
    val successRate: Double,
    val failureRate: Double
)

/**
 * 最近失败任务摘要，便于管理端展示告警列表。
 *
 * @property id 任务 ID
 * @property userId 任务所属用户 ID
 * @property name 任务名称
 * @property originalQuery 用户原始查询
 * @property errorMessage 失败原因
 * @property createdTimeStamp 任务创建时间戳，单位为毫秒
 */
data class AdminRecentFailedTaskMetric(
    val id: Long,
    val userId: Long,
    val name: String,
    val originalQuery: String,
    val errorMessage: String?,
    val createdTimeStamp: Long
)
