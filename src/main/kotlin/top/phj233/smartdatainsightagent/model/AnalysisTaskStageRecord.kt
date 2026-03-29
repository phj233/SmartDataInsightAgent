package top.phj233.smartdatainsightagent.model

import com.fasterxml.jackson.databind.JsonNode

/**
 * 分析任务阶段记录
 */
data class AnalysisTaskStageRecord(
    val stage: String,
    val timestamp: String,
    val details: Map<String, JsonNode> = emptyMap()
)

