package top.phj233.smartdatainsightagent.service

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.model.AnalysisRequest
import top.phj233.smartdatainsightagent.service.agent.DataAnalysisAgent
import top.phj233.smartdatainsightagent.util.withMdc
import top.phj233.smartdatainsightagent.util.withMdcContext

/**
 * 异步分析任务执行器。
 * @author phj233
 * @since 2026/3/27 18:00
 * @version
 */
@Service
class AnalysisExecutionService(
    private val dataAnalysisAgent: DataAnalysisAgent,
    private val analysisTaskService: AnalysisTaskService
) {

    private val logger = LoggerFactory.getLogger(AnalysisExecutionService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun submit(taskId: Long, request: AnalysisRequest) {
        val parentMdc = MDC.getCopyOfContextMap()
        logger.info("[异步执行] 提交任务，taskId={}, userId={}, dataSourceId={}", taskId, request.userId, request.dataSourceId)
        scope.launch {
            withMdcContext(parentMdc) {
                withMdc(
                    "taskId" to taskId.toString(),
                    "userId" to request.userId.toString()
                ) {
                    try {
                        dataAnalysisAgent.analyzeDataForTask(taskId, request)
                    } catch (ex: Exception) {
                        logger.error("[异步执行] 任务执行失败，taskId={}, userId={}", taskId, request.userId, ex)
                        analysisTaskService.notifyFailureFallback(
                            taskId = taskId,
                            stage = AnalysisTaskService.STAGE_ASYNC_EXECUTION_FAILED,
                            errorMessage = ex.message ?: "分析任务执行失败"
                        )
                    }
                }
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        scope.cancel()
    }
}

