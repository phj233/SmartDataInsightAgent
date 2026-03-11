package top.phj233.smartdatainsightagent.model

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

/**
 * 分析执行请求数据类，包含执行分析任务所需的参数。
 * @param query 分析查询字符串，不能为空。
 * @param dataSourceId 可选的数据源ID，必须大于0，如果提供的话。
 * @author phj233
 * @since 2026/2/28 14:55
 * @version
 */
data class AnalysisExecuteRequest(
    @field:NotBlank(message = "query 不能为空")
    val query: String,
    @field:Min(value = 1, message = "dataSourceId 必须大于 0")
    val dataSourceId: Long? = null
)
