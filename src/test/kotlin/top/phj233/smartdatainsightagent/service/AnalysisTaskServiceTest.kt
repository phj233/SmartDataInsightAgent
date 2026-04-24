package top.phj233.smartdatainsightagent.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import top.phj233.smartdatainsightagent.entity.AnalysisTask
import top.phj233.smartdatainsightagent.entity.AnalysisTaskDraft
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskDetailView
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskSummaryView
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.exception.AnalysisTaskException
import top.phj233.smartdatainsightagent.model.AnalysisRequest
import top.phj233.smartdatainsightagent.model.AnalysisTaskStageRecord
import top.phj233.smartdatainsightagent.repository.AnalysisTaskRepository

class AnalysisTaskServiceTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `throw business exception when task query is blank`() {
        val service = newService()

        assertThrows(AnalysisTaskException::class.java) {
            service.createTask(AnalysisRequest(query = "   ", dataSourceId = null, userId = 0L))
        }
    }

    @Test
    fun `throw business exception when task does not exist`() {
        val repository = mock(AnalysisTaskRepository::class.java)
        val service = AnalysisTaskService(repository)
        doReturn(null).`when`(repository).findNullable(1L)

        assertThrows(AnalysisTaskException::class.java) {
            service.markRunning(1L, AnalysisTaskService.STAGE_SQL_GENERATING)
        }
    }

    @Test
    fun `throw business exception when task is already finished`() {
        val repository = mock(AnalysisTaskRepository::class.java)
        val service = AnalysisTaskService(repository)
        doReturn(finishedTask()).`when`(repository).findNullable(2L)

        assertThrows(AnalysisTaskException::class.java) {
            service.markRunning(2L, AnalysisTaskService.STAGE_QUERY_EXECUTING)
        }
    }

    @Test
    fun `list task summaries by user and status`() {
        val repository = mock(AnalysisTaskRepository::class.java)
        val service = AnalysisTaskService(repository)
        val summaries = listOf(
            AnalysisTaskSummaryView(
                id = 1L,
                userId = 7L,
                name = "查看销售额",
                originalQuery = "查看销售额",
                status = AnalysisStatus.SUCCESS,
                executionTime = 120L,
                errorMessage = null
            )
        )
        doReturn(summaries).`when`(repository).findSummaryViewsByUserIdAndStatus(7L, AnalysisStatus.SUCCESS)

        val result = service.listTaskSummaries(7L, AnalysisStatus.SUCCESS)

        assertEquals(1, result.size)
        assertSame(summaries, result)
    }

    @Test
    fun `list task summaries delegates to repository without service level user id validation`() {
        val repository = mock(AnalysisTaskRepository::class.java)
        val service = AnalysisTaskService(repository)
        doReturn(emptyList<AnalysisTaskSummaryView>()).`when`(repository).findSummaryViewsByUserId(0L)

        val result = service.listTaskSummaries(0L)

        assertEquals(emptyList<AnalysisTaskSummaryView>(), result)
    }

    @Test
    fun `get task detail by task id and user id`() {
        val repository = mock(AnalysisTaskRepository::class.java)
        val service = AnalysisTaskService(repository)
        val detail = AnalysisTaskDetailView(
            id = 3L,
            userId = 7L,
            name = "查看趋势",
            originalQuery = "查看趋势",
            generatedSql = "select * from t",
            parameters = listOf(
                AnalysisTaskStageRecord(
                    stage = AnalysisTaskService.STAGE_TASK_CREATED,
                    timestamp = "2026-03-11T20:00:00",
                    details = emptyMap()
                )
            ),
            status = AnalysisStatus.RUNNING,
            result = null,
            executionTime = null,
            errorMessage = null
        )
        doReturn(detail).`when`(repository).findDetailViewByIdAndUserId(3L, 7L)

        val result = service.getTaskDetail(3L, 7L)

        assertSame(detail, result)
    }

    @Test
    fun `throw business exception when task detail is not accessible`() {
        val repository = mock(AnalysisTaskRepository::class.java)
        val service = AnalysisTaskService(repository)
        doReturn(null).`when`(repository).findDetailViewByIdAndUserId(4L, 7L)

        assertThrows(AnalysisTaskException::class.java) {
            service.getTaskDetail(4L, 7L)
        }
    }

    @Test
    fun `reopen failed task should reuse original query by default`() {
        var savedTask: AnalysisTask? = null
        val repository = repositoryWithSaveCapture { savedTask = it }
        val service = AnalysisTaskService(repository)
        doReturn(failedTask(id = 5L)).`when`(repository).findByIdAndUserId(5L, 7L)

        val request = service.reopenFailedTask(5L, 7L, null)

        assertEquals("show me sales", request.query)
        assertEquals(9L, request.dataSourceId)
        assertNotNull(savedTask)
        assertEquals(AnalysisStatus.PENDING, savedTask!!.status)
        assertEquals("show me sales", savedTask.originalQuery)
        assertNull(savedTask.generatedSql)
        assertNull(savedTask.result)
        assertNull(savedTask.executionTime)
        assertNull(savedTask.errorMessage)
        assertEquals(AnalysisTaskService.STAGE_REANALYZE_REQUESTED, savedTask.parameters.last().stage)
        assertFalse(savedTask.parameters.last().details.getValue("queryUpdated").asBoolean())
    }

    @Test
    fun `reopen failed task should update query and task name when override query is provided`() {
        var savedTask: AnalysisTask? = null
        val repository = repositoryWithSaveCapture { savedTask = it }
        val service = AnalysisTaskService(repository)
        doReturn(failedTask(id = 6L)).`when`(repository).findByIdAndUserId(6L, 7L)

        val request = service.reopenFailedTask(6L, 7L, "  show me revenue  ")

        assertEquals("show me revenue", request.query)
        assertNotNull(savedTask)
        assertEquals("show me revenue", savedTask!!.originalQuery)
        assertEquals("show me revenue", savedTask.name)
        assertTrue(savedTask.parameters.last().details.getValue("queryUpdated").asBoolean())
        assertEquals("show me revenue", savedTask.parameters.last().details.getValue("query").asText())
    }

    @Test
    fun `reopen failed task should keep custom name when query changes`() {
        var savedTask: AnalysisTask? = null
        val repository = repositoryWithSaveCapture { savedTask = it }
        val service = AnalysisTaskService(repository)
        doReturn(failedTask(id = 7L, name = "custom task")).`when`(repository).findByIdAndUserId(7L, 7L)

        service.reopenFailedTask(7L, 7L, "show me profit")

        assertNotNull(savedTask)
        assertEquals("custom task", savedTask!!.name)
        assertEquals("show me profit", savedTask.originalQuery)
    }

    @Test
    fun `reopen failed task should reject blank override query`() {
        val repository = repositoryWithSaveCapture()
        val service = AnalysisTaskService(repository)
        doReturn(failedTask(id = 8L)).`when`(repository).findByIdAndUserId(8L, 7L)

        assertThrows(AnalysisTaskException::class.java) {
            service.reopenFailedTask(8L, 7L, "   ")
        }
    }

    private fun newService(): AnalysisTaskService {
        return AnalysisTaskService(mock(AnalysisTaskRepository::class.java))
    }

    private fun repositoryWithSaveCapture(onSave: (AnalysisTask) -> Unit = {}): AnalysisTaskRepository {
        return mock(AnalysisTaskRepository::class.java) { invocation: InvocationOnMock ->
            when (invocation.method.name) {
                "save" -> (invocation.arguments[0] as AnalysisTask).also(onSave)
                else -> Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        }
    }

    private fun failedTask(
        id: Long,
        userId: Long = 7L,
        originalQuery: String = "show me sales",
        name: String = originalQuery.take(20),
        dataSourceId: Long = 9L
    ): AnalysisTask {
        return AnalysisTaskDraft.`$`.produce {
            this.id = id
            user {
                this.id = userId
            }
            this.name = name
            this.originalQuery = originalQuery
            generatedSql = "select * from orders"
            parameters = listOf(
                AnalysisTaskStageRecord(
                    stage = AnalysisTaskService.STAGE_TASK_CREATED,
                    timestamp = "2026-03-11T20:00:00",
                    details = mapOf(
                        "query" to objectMapper.valueToTree(originalQuery),
                        "dataSourceId" to objectMapper.valueToTree(dataSourceId)
                    )
                )
            )
            status = AnalysisStatus.FAILED
            result = emptyList()
            executionTime = 120L
            errorMessage = "boom"
            createdTimeStamp = 1L
            modifiedTimeStamp = 2L
            createdBy = null
            modifiedBy = null
        }
    }

    private fun finishedTask(): AnalysisTask {
        return AnalysisTaskDraft.`$`.produce {
            id = 2L
            user {
                this.id = 1L
            }
            name = "查看销售额"
            originalQuery = "查看销售额"
            generatedSql = null
            parameters = emptyList()
            status = AnalysisStatus.SUCCESS
            result = null
            executionTime = null
            errorMessage = null
            createdTimeStamp = 1L
            modifiedTimeStamp = 2L
            createdBy = null
            modifiedBy = null
        }
    }
}
