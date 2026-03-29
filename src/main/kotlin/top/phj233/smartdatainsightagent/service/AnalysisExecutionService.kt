package top.phj233.smartdatainsightagent.service

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import org.slf4j.MDC
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.model.AnalysisRequest
import top.phj233.smartdatainsightagent.service.agent.DataAnalysisAgent

/**
 * 异步分析任务执行器。
 * @author phj233
 * @since 2026/3/27 18:00
 * @version
 */
@Service
class AnalysisExecutionService(
    private val dataAnalysisAgent: DataAnalysisAgent
) {

    private val logger = LoggerFactory.getLogger(AnalysisExecutionService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun submit(taskId: Long, request: AnalysisRequest) {
        val parentMdc = MDC.getCopyOfContextMap()
        logger.info("[异步执行] 提交任务，taskId={}, userId={}, dataSourceId={}", taskId, request.userId, request.dataSourceId)
        scope.launch {
            applyMdc(parentMdc)
            MDC.put("taskId", taskId.toString())
            MDC.put("userId", request.userId.toString())
            try {
                dataAnalysisAgent.analyzeDataForTask(taskId, request)
            } catch (ex: Exception) {
                logger.error("[异步执行] 任务执行失败，taskId={}, userId={}", taskId, request.userId, ex)
            } finally {
                MDC.remove("taskId")
                MDC.remove("userId")
            }
        }
    }

    private fun applyMdc(contextMap: Map<String, String>?) {
        if (contextMap == null) {
            MDC.clear()
            return
        }
        MDC.setContextMap(contextMap)
    }

    @PreDestroy
    fun shutdown() {
        scope.cancel()
    }
}

