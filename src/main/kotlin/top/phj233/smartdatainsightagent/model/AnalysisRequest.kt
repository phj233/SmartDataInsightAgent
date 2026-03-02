package top.phj233.smartdatainsightagent.model

data class AnalysisRequest(
    val query: String,
    val dataSourceId: Long,
    val userId: Long
)
