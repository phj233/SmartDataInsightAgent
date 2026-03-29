package top.phj233.smartdatainsightagent.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.model.AnalysisTaskProgressEvent
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 分析任务 SSE 服务
 * @author phj233
 * @since 2026/3/16 20:00
 * @version
 */
@Service
class AnalysisSseService : AnalysisTaskProgressNotifier {

    private val logger = LoggerFactory.getLogger(AnalysisSseService::class.java)
    private val emitterRegistry = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    fun subscribe(taskId: Long): SseEmitter {
        logger.info("[SSE] 建立订阅请求，taskId={}", taskId)
        val emitter = SseEmitter(0L)
        val emitters = emitterRegistry.computeIfAbsent(taskId) { CopyOnWriteArrayList() }
        emitters += emitter

        emitter.onCompletion { remove(taskId, emitter) }
        emitter.onTimeout {
            logger.info("[SSE] 连接超时，taskId={}", taskId)
            emitter.complete()
            remove(taskId, emitter)
        }
        emitter.onError {
            logger.info("[SSE] 客户端连接断开，taskId={}", taskId)
            remove(taskId, emitter)
        }

        // 建立连接后立即发一个握手事件，前端可据此确认订阅成功。
        if (!safeSend(
                emitter,
                SseEmitter.event()
                    .name("connected")
                    .id("$taskId-${Instant.now()}")
                    .data(mapOf("taskId" to taskId))
            )
        ) {
            logger.warn("[SSE] 握手事件发送失败，taskId={}", taskId)
            remove(taskId, emitter)
            return emitter
        }

        logger.info("[SSE] 订阅成功，taskId={}, 当前连接数={}", taskId, emitters.size)

        return emitter
    }

    override fun notify(event: AnalysisTaskProgressEvent) {
        logger.info("[SSE] 推送任务进度，taskId={}, status={}, stage={}", event.taskId, event.status, event.stage)
        val emitters = emitterRegistry[event.taskId] ?: return
        val stale = mutableListOf<SseEmitter>()

        for (emitter in emitters) {
            if (!safeSend(
                    emitter,
                    SseEmitter.event()
                        .name("task-progress")
                        .id("${event.taskId}-${event.timestamp}")
                        .data(event)
                )
            ) {
                stale += emitter
            }
        }

        if (stale.isNotEmpty()) {
            emitters.removeAll(stale.toSet())
        }

        if (event.status == AnalysisStatus.SUCCESS || event.status == AnalysisStatus.FAILED) {
            logger.info("[SSE] 任务终态，关闭订阅，taskId={}, status={}", event.taskId, event.status)
            completeTask(event.taskId)
        }
    }

    private fun completeTask(taskId: Long) {
        logger.info("[SSE] 完成并清理任务订阅，taskId={}", taskId)
        emitterRegistry.remove(taskId)?.forEach { emitter ->
            safeComplete(emitter)
        }
    }

    private fun safeSend(emitter: SseEmitter, event: SseEmitter.SseEventBuilder): Boolean {
        return try {
            emitter.send(event)
            true
        } catch (ex: Throwable) {
            logger.info("[SSE] 事件发送失败，关闭失效连接")
            safeComplete(emitter)
            false
        }
    }

    private fun safeComplete(emitter: SseEmitter) {
        try {
            emitter.complete()
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun remove(taskId: Long, emitter: SseEmitter) {
        emitterRegistry[taskId]?.let { emitters ->
            emitters.remove(emitter)
            if (emitters.isEmpty()) {
                emitterRegistry.remove(taskId)
                logger.info("[SSE] 无活跃连接，移除任务注册，taskId={}", taskId)
            }
        }
    }
}

