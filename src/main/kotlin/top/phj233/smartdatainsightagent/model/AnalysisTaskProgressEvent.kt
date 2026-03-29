package top.phj233.smartdatainsightagent.model

import com.fasterxml.jackson.databind.JsonNode
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus

/**
 * SSE 推送的分析任务进度事件。
 */
data class AnalysisTaskProgressEvent(
    val taskId: Long,
    val status: AnalysisStatus,
    val stage: String,
    val timestamp: String,
    val details: Map<String, JsonNode> = emptyMap(),
    val generatedSql: String? = null,
    val errorMessage: String? = null
)

