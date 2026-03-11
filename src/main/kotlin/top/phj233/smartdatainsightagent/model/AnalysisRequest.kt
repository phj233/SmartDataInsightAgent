package top.phj233.smartdatainsightagent.model

/**
 * 分析请求数据类，包含分析请求的详细信息。
 * @param query 用户输入的分析查询文本，描述用户希望分析的数据和问题。
 * @param dataSourceId 可选的数据源ID，指定要分析的数据来源，如果为null则使用默认数据源。
 * @param userId 发起分析请求的用户ID，用于记录和权限控制。
 * @author phj233
 * @since 2026/2/28 14:56
 * @version
 */
data class AnalysisRequest(
    val query: String,
    val dataSourceId: Long?,
    val userId: Long
)
