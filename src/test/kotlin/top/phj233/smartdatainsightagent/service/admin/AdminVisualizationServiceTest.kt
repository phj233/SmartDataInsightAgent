package top.phj233.smartdatainsightagent.service.admin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import top.phj233.smartdatainsightagent.entity.AnalysisTaskDraft
import top.phj233.smartdatainsightagent.entity.DataSourceDraft
import top.phj233.smartdatainsightagent.entity.UserDraft
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.entity.enums.DataSourceType
import top.phj233.smartdatainsightagent.repository.AnalysisTaskRepository
import top.phj233.smartdatainsightagent.repository.DataSourceRepository
import top.phj233.smartdatainsightagent.repository.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 管理端数据可视化服务测试，覆盖总览、分布、趋势和失败任务聚合。
 */
class AdminVisualizationServiceTest {

    private val userRepository = mock(UserRepository::class.java)
    private val dataSourceRepository = mock(DataSourceRepository::class.java)
    private val analysisTaskRepository = mock(AnalysisTaskRepository::class.java)
    private val zoneId = ZoneId.of("Asia/Hong_Kong")
    private val clock = Clock.fixed(Instant.parse("2026-04-26T08:00:00Z"), zoneId)
    private val service = AdminVisualizationService(
        userRepository,
        dataSourceRepository,
        analysisTaskRepository
    ).also {
        it.clock = clock
    }

    @Test
    fun `dashboard should aggregate totals distributions trend and recent failures`() {
        `when`(userRepository.findAll()).thenReturn(
            listOf(
                user(id = 1L, enabled = true),
                user(id = 2L, enabled = false)
            )
        )
        `when`(dataSourceRepository.findAll()).thenReturn(
            listOf(
                dataSource(id = 1L, type = DataSourceType.MYSQL, active = true),
                dataSource(id = 2L, type = DataSourceType.POSTGRESQL, active = false),
                dataSource(id = 3L, type = DataSourceType.MYSQL, active = true)
            )
        )
        `when`(analysisTaskRepository.findAll()).thenReturn(
            listOf(
                analysisTask(
                    id = 1L,
                    status = AnalysisStatus.SUCCESS,
                    createdTimeStamp = dateMillis("2026-04-26"),
                    executionTime = 100L
                ),
                analysisTask(
                    id = 2L,
                    status = AnalysisStatus.FAILED,
                    createdTimeStamp = dateMillis("2026-04-25"),
                    executionTime = 200L,
                    errorMessage = "sql error"
                ),
                analysisTask(
                    id = 3L,
                    status = AnalysisStatus.FAILED,
                    createdTimeStamp = dateMillis("2026-04-24"),
                    executionTime = null,
                    errorMessage = "timeout"
                ),
                analysisTask(
                    id = 4L,
                    status = AnalysisStatus.PENDING,
                    createdTimeStamp = dateMillis("2026-04-18"),
                    executionTime = null
                ),
                analysisTask(
                    id = 5L,
                    status = AnalysisStatus.RUNNING,
                    createdTimeStamp = dateMillis("2026-04-26"),
                    executionTime = 50L
                )
            )
        )

        val result = service.dashboard(days = 3, recentFailureLimit = 1)

        assertEquals(clock.millis(), result.generatedAt)
        assertEquals(3, result.trendDays)
        assertEquals(2, result.totals.totalUsers)
        assertEquals(1, result.totals.enabledUsers)
        assertEquals(1, result.totals.disabledUsers)
        assertEquals(3, result.totals.totalDataSources)
        assertEquals(2, result.totals.activeDataSources)
        assertEquals(1, result.totals.inactiveDataSources)
        assertEquals(5, result.totals.totalAnalysisTasks)
        assertEquals(1, result.totals.successAnalysisTasks)
        assertEquals(2, result.totals.failedAnalysisTasks)
        assertEquals(1, result.totals.runningAnalysisTasks)
        assertEquals(1, result.totals.pendingAnalysisTasks)

        assertEquals(
            mapOf(
                AnalysisStatus.PENDING to 1L,
                AnalysisStatus.RUNNING to 1L,
                AnalysisStatus.SUCCESS to 1L,
                AnalysisStatus.FAILED to 2L
            ),
            result.taskStatusDistribution.associate { it.status to it.count }
        )
        assertEquals(
            mapOf(
                DataSourceType.MYSQL to 2L,
                DataSourceType.POSTGRESQL to 1L,
                DataSourceType.EXCEL to 0L,
                DataSourceType.CSV to 0L
            ),
            result.dataSourceTypeDistribution.associate { it.type to it.count }
        )
        assertEquals(2, result.dataSourceActivity.active)
        assertEquals(1, result.dataSourceActivity.inactive)

        assertEquals(listOf("2026-04-24", "2026-04-25", "2026-04-26"), result.taskTrend.map { it.date })
        assertEquals(listOf(1L, 1L, 2L), result.taskTrend.map { it.total })
        assertEquals(listOf(0L, 0L, 1L), result.taskTrend.map { it.success })
        assertEquals(listOf(1L, 1L, 0L), result.taskTrend.map { it.failed })
        assertEquals(listOf(0L, 0L, 1L), result.taskTrend.map { it.running })
        assertEquals(listOf(0L, 0L, 0L), result.taskTrend.map { it.pending })

        assertEquals(116.666, result.taskExecution.averageExecutionTimeMillis ?: 0.0, 0.001)
        assertEquals(200L, result.taskExecution.maxExecutionTimeMillis)
        assertEquals(50L, result.taskExecution.minExecutionTimeMillis)
        assertEquals(0.2, result.taskExecution.successRate, 0.001)
        assertEquals(0.4, result.taskExecution.failureRate, 0.001)

        assertEquals(1, result.recentFailures.size)
        assertEquals(2L, result.recentFailures.first().id)
        assertEquals("sql error", result.recentFailures.first().errorMessage)
    }

    @Test
    fun `dashboard should clamp trend days and handle empty datasets`() {
        `when`(userRepository.findAll()).thenReturn(emptyList())
        `when`(dataSourceRepository.findAll()).thenReturn(emptyList())
        `when`(analysisTaskRepository.findAll()).thenReturn(emptyList())

        val result = service.dashboard(days = 999, recentFailureLimit = 999)

        assertEquals(90, result.trendDays)
        assertEquals(90, result.taskTrend.size)
        assertEquals(0, result.totals.totalUsers)
        assertEquals(0, result.totals.totalDataSources)
        assertEquals(0, result.totals.totalAnalysisTasks)
        assertNull(result.taskExecution.averageExecutionTimeMillis)
        assertNull(result.taskExecution.maxExecutionTimeMillis)
        assertNull(result.taskExecution.minExecutionTimeMillis)
        assertEquals(0.0, result.taskExecution.successRate)
        assertEquals(0.0, result.taskExecution.failureRate)
        assertEquals(emptyList<Any>(), result.recentFailures)
    }

    /**
     * 构造测试用户。
     *
     * @param id 用户 ID
     * @param enabled 是否启用
     * @return 用户实体
     */
    private fun user(id: Long, enabled: Boolean) = UserDraft.`$`.produce {
        this.id = id
        username = "user_$id"
        password = "hashed-password"
        email = "user_$id@example.com"
        avatar = null
        this.enabled = enabled
    }

    /**
     * 构造测试数据源。
     *
     * @param id 数据源 ID
     * @param type 数据源类型
     * @param active 是否启用
     * @return 数据源实体
     */
    private fun dataSource(id: Long, type: DataSourceType, active: Boolean) = DataSourceDraft.`$`.produce {
        this.id = id
        user {
            this.id = 1L
        }
        name = "source_$id"
        this.type = type
        connectionConfig = emptyList()
        schemaInfo = emptyList()
        this.active = active
        createdTimeStamp = dateMillis("2026-04-20")
        modifiedTimeStamp = dateMillis("2026-04-20")
        createdBy = null
        modifiedBy = null
    }

    /**
     * 构造测试分析任务。
     *
     * @param id 任务 ID
     * @param status 任务状态
     * @param createdTimeStamp 创建时间戳
     * @param executionTime 执行耗时
     * @param errorMessage 错误信息
     * @return 分析任务实体
     */
    private fun analysisTask(
        id: Long,
        status: AnalysisStatus,
        createdTimeStamp: Long,
        executionTime: Long?,
        errorMessage: String? = null
    ) = AnalysisTaskDraft.`$`.produce {
        this.id = id
        user {
            this.id = 1L
        }
        name = "task_$id"
        originalQuery = "show me sales"
        generatedSql = "select * from orders"
        parameters = emptyList()
        this.status = status
        result = emptyList()
        this.executionTime = executionTime
        this.errorMessage = errorMessage
        this.createdTimeStamp = createdTimeStamp
        modifiedTimeStamp = createdTimeStamp
        createdBy = null
        modifiedBy = null
    }

    /**
     * 将日期字符串转换为测试时区当天零点时间戳。
     *
     * @param date 日期字符串，格式为 yyyy-MM-dd
     * @return 时间戳，单位为毫秒
     */
    private fun dateMillis(date: String): Long {
        return LocalDate.parse(date).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
}
