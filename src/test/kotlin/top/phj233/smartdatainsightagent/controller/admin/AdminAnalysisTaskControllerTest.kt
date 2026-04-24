package top.phj233.smartdatainsightagent.controller.admin

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import top.phj233.smartdatainsightagent.entity.dto.AdminFailedAnalysisTaskUpdateInput
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskDetailView
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskSummaryView
import top.phj233.smartdatainsightagent.service.admin.AdminAnalysisTaskService

class AdminAnalysisTaskControllerTest {

    private val adminAnalysisTaskService = mock(AdminAnalysisTaskService::class.java)
    private val controller = AdminAnalysisTaskController(adminAnalysisTaskService)

    @Test
    fun `list endpoint delegates pagination to service`() {
        val expected = PageImpl(listOf(mock(AnalysisTaskSummaryView::class.java)))
        `when`(adminAnalysisTaskService.list(PageRequest.of(1, 15))).thenReturn(expected)

        val result = controller.list(1, 15)

        assertSame(expected, result)
        verify(adminAnalysisTaskService).list(PageRequest.of(1, 15))
    }

    @Test
    fun `detail endpoint delegates task id to service`() {
        val expected = mock(AnalysisTaskDetailView::class.java)
        `when`(adminAnalysisTaskService.detail(8L)).thenReturn(expected)

        val result = controller.detail(8L)

        assertSame(expected, result)
        verify(adminAnalysisTaskService).detail(8L)
    }

    @Test
    fun `update failed task endpoint delegates to service`() {
        val input = AdminFailedAnalysisTaskUpdateInput(
            name = "retry task",
            originalQuery = null,
            generatedSql = null,
            parameters = null,
            status = null,
            result = null,
            executionTime = null,
            errorMessage = null
        )
        val expected = mock(AnalysisTaskDetailView::class.java)
        `when`(adminAnalysisTaskService.updateFailedTask(12L, input)).thenReturn(expected)

        val result = controller.updateFailedTask(12L, input)

        assertSame(expected, result)
        verify(adminAnalysisTaskService).updateFailedTask(12L, input)
    }

    @Test
    fun `delete failed task endpoint delegates task id to service`() {
        controller.deleteFailedTask(12L)

        verify(adminAnalysisTaskService).deleteFailedTask(12L)
    }
}
