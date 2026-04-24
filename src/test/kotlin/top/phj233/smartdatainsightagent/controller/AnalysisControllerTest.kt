package top.phj233.smartdatainsightagent.controller

import cn.dev33.satoken.stp.StpUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskReanalyzeInput
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.model.AnalysisRequest
import top.phj233.smartdatainsightagent.service.AnalysisExecutionService
import top.phj233.smartdatainsightagent.service.AnalysisSseService
import top.phj233.smartdatainsightagent.service.AnalysisTaskService
import top.phj233.smartdatainsightagent.service.agent.DataAnalysisAgent

class AnalysisControllerTest {

    private val dataAnalysisAgent = mock(DataAnalysisAgent::class.java)
    private val analysisTaskService = mock(AnalysisTaskService::class.java)
    private val analysisExecutionService = mock(AnalysisExecutionService::class.java)
    private val analysisSseService = mock(AnalysisSseService::class.java)
    private val controller = AnalysisController(
        dataAnalysisAgent,
        analysisTaskService,
        analysisExecutionService,
        analysisSseService
    )

    @Test
    fun `reanalyze endpoint forwards override query to service`() {
        val input = AnalysisTaskReanalyzeInput(query = "  show me revenue  ")
        val request = AnalysisRequest(query = "show me revenue", dataSourceId = 9L, userId = 88L)

        mockStatic(StpUtil::class.java).use { stpMock ->
            stpMock.`when`<Long> { StpUtil.getLoginIdAsLong() }.thenReturn(88L)
            `when`(analysisTaskService.reopenFailedTask(12L, 88L, "  show me revenue  ")).thenReturn(request)

            val result = controller.reanalyzeFailedTask(12L, input)

            assertEquals(12L, result.taskId)
            assertEquals(AnalysisStatus.PENDING, result.status)
            verify(analysisTaskService).reopenFailedTask(12L, 88L, "  show me revenue  ")
            verify(analysisExecutionService).submit(12L, request)
        }
    }

    @Test
    fun `reanalyze endpoint remains compatible with empty body`() {
        val request = AnalysisRequest(query = "show me sales", dataSourceId = 9L, userId = 77L)

        mockStatic(StpUtil::class.java).use { stpMock ->
            stpMock.`when`<Long> { StpUtil.getLoginIdAsLong() }.thenReturn(77L)
            `when`(analysisTaskService.reopenFailedTask(13L, 77L, null)).thenReturn(request)

            val result = controller.reanalyzeFailedTask(13L, null)

            assertEquals(13L, result.taskId)
            assertEquals(AnalysisStatus.PENDING, result.status)
            verify(analysisTaskService).reopenFailedTask(13L, 77L, null)
            verify(analysisExecutionService).submit(13L, request)
        }
    }
}
