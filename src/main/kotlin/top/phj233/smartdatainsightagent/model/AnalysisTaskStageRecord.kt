package top.phj233.smartdatainsightagent.model

/**
 * 分析任务阶段记录
 */
data class AnalysisTaskStageRecord(
    val stage: String,
    val timestamp: String,
    val details: Map<String, Any> = emptyMap()
)

