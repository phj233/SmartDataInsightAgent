package top.phj233.smartdatainsightagent.model

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class AnalysisExecuteRequest(
    @field:NotBlank(message = "query 不能为空")
    val query: String,
    @field:Min(value = 1, message = "dataSourceId 必须大于 0")
    val dataSourceId: Long? = null
)
