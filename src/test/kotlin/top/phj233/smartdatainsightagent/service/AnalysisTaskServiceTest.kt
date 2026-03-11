package top.phj233.smartdatainsightagent.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
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

    private fun newService(): AnalysisTaskService {
        return AnalysisTaskService(mock(AnalysisTaskRepository::class.java))
    }

    private fun finishedTask(): AnalysisTask {
        return AnalysisTaskDraft.`$`.produce {
            id = 2L
            user {
                this.id = 1L
            }
            originalQuery = "查看销售额"
            generatedSql = null
            parameters = emptyList()
            status = AnalysisStatus.SUCCESS
            result = null
            executionTime = null
            errorMessage = null
        }
    }
}


