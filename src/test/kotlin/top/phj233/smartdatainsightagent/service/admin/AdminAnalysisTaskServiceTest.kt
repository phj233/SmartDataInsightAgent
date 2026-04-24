package top.phj233.smartdatainsightagent.service.admin

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import org.mockito.invocation.InvocationOnMock
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import top.phj233.smartdatainsightagent.entity.AnalysisTaskDraft
import top.phj233.smartdatainsightagent.entity.dto.AdminFailedAnalysisTaskUpdateInput
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.exception.AnalysisTaskException
import top.phj233.smartdatainsightagent.model.AnalysisResult
import top.phj233.smartdatainsightagent.model.AnalysisTaskStageRecord
import top.phj233.smartdatainsightagent.repository.AnalysisTaskRepository

class AdminAnalysisTaskServiceTest {

    private val analysisTaskRepository = mock(AnalysisTaskRepository::class.java) { invocation: InvocationOnMock ->
        when (invocation.method.name) {
            "save" -> invocation.arguments[0]
            else -> Answers.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val service = AdminAnalysisTaskService(analysisTaskRepository)

    @Test
    fun `list should map repository page to summary views`() {
        val pageable = PageRequest.of(0, 10)
        `when`(analysisTaskRepository.findAll(pageable)).thenReturn(
            PageImpl(listOf(analysisTask(id = 1L, name = "failed task")))
        )

        val result = service.list(pageable)

        assertEquals(1, result.totalElements)
        assertEquals(1L, result.content.first().id)
        assertEquals("failed task", result.content.first().name)
        assertEquals(AnalysisStatus.FAILED, result.content.first().status)
    }

    @Test
    fun `detail should return task view when task exists`() {
        `when`(analysisTaskRepository.findNullable(1L)).thenReturn(analysisTask(id = 1L, name = "detail task"))

        val result = service.detail(1L)

        assertEquals(1L, result.id)
        assertEquals("detail task", result.name)
        assertEquals("select * from orders", result.generatedSql)
    }

    @Test
    fun `detail should throw when task does not exist`() {
        `when`(analysisTaskRepository.findNullable(99L)).thenReturn(null)

        assertThrows(AnalysisTaskException::class.java) {
            service.detail(99L)
        }
    }

    @Test
    fun `update failed task should trim mutable fields before save`() {
        val existing = analysisTask(id = 12L, name = "old task")
        val input = AdminFailedAnalysisTaskUpdateInput(
            name = "  retry task  ",
            originalQuery = "  show me sales  ",
            generatedSql = "   ",
            parameters = listOf(
                AnalysisTaskStageRecord(
                    stage = "manual-fix",
                    timestamp = "2026-04-24T23:00:00"
                )
            ),
            status = AnalysisStatus.SUCCESS,
            result = emptyList(),
            executionTime = 321L,
            errorMessage = "   "
        )
        `when`(analysisTaskRepository.findNullable(12L)).thenReturn(existing)

        val result = service.updateFailedTask(12L, input)

        assertEquals("retry task", result.name)
        assertEquals("show me sales", result.originalQuery)
        assertNull(result.generatedSql)
        assertEquals(AnalysisStatus.SUCCESS, result.status)
        assertEquals(321L, result.executionTime)
        assertEquals("manual-fix", result.parameters.first().stage)
        assertEquals(emptyList<AnalysisResult>(), result.result ?: emptyList<AnalysisResult>())
        assertNull(result.errorMessage)
    }

    @Test
    fun `update failed task should reject blank name`() {
        `when`(analysisTaskRepository.findNullable(12L)).thenReturn(analysisTask(id = 12L))

        assertThrows(AnalysisTaskException::class.java) {
            service.updateFailedTask(
                12L,
                AdminFailedAnalysisTaskUpdateInput(name = "   ")
            )
        }
    }

    @Test
    fun `update failed task should reject non failed task`() {
        `when`(analysisTaskRepository.findNullable(12L)).thenReturn(
            analysisTask(id = 12L, status = AnalysisStatus.SUCCESS)
        )

        assertThrows(AnalysisTaskException::class.java) {
            service.updateFailedTask(12L, AdminFailedAnalysisTaskUpdateInput())
        }
    }

    @Test
    fun `delete failed task should delete task`() {
        `when`(analysisTaskRepository.findNullable(12L)).thenReturn(analysisTask(id = 12L))

        service.deleteFailedTask(12L)

        verify(analysisTaskRepository).deleteById(12L)
    }

    private fun analysisTask(
        id: Long,
        name: String = "failed task",
        status: AnalysisStatus = AnalysisStatus.FAILED
    ) = AnalysisTaskDraft.`$`.produce {
        this.id = id
        user {
            this.id = 7L
        }
        this.name = name
        originalQuery = "show me sales"
        generatedSql = "select * from orders"
        parameters = emptyList()
        this.status = status
        result = emptyList()
        executionTime = 120L
        errorMessage = "boom"
        createdTimeStamp = 1L
        modifiedTimeStamp = 2L
        createdBy = null
        modifiedBy = null
    }
}
