package top.phj233.smartdatainsightagent.service.data

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/**
 * @author phj233
 * @since 2026/1/28 15:04
 * @version
 */
@Service
class QueryExecutorService(
    private val jdbcTemplate: JdbcTemplate, // 实际项目中应根据 DataSource 动态切换数据源
    private val externalJdbcTemplateProvider: ExternalJdbcTemplateProvider
) {
    fun executeQuery(sql: String): List<Map<String, Any>> {
        validateReadOnlySql(sql)
        // 简单实现：直接使用当前配置的 jdbcTemplate
        // 生产环境需根据 dataSourceId 获取对应的 Connection
        return jdbcTemplate.queryForList(sql)
    }

    fun executeQuery(sql: String, dataSourceId: Long, userId: Long? = null): List<Map<String, Any>> {
        validateReadOnlySql(sql)
        return externalJdbcTemplateProvider.getJdbcTemplate(dataSourceId, userId).queryForList(sql)
    }

    internal fun validateReadOnlySql(sql: String) {
        val normalizedSql = sql.trim()
        require(normalizedSql.isNotBlank()) { "SQL 不能为空" }

        val compactSql = normalizedSql.replace(Regex("\\s+"), " ")
        require(compactSql.startsWith("SELECT ", ignoreCase = true) || compactSql.startsWith("WITH ", ignoreCase = true)) {
            "仅支持只读查询"
        }

        val dangerousPatterns = listOf(
            Regex("(^|\\s)(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|MERGE|GRANT|REVOKE|CALL|EXEC)(\\s|$)", RegexOption.IGNORE_CASE),
            Regex("--"),
            Regex("/\\*"),
            Regex(";\\s*\\S")
        )
        dangerousPatterns.forEach { pattern ->
            require(!pattern.containsMatchIn(compactSql)) { "检测到潜在的危险SQL操作" }
        }
    }
}
