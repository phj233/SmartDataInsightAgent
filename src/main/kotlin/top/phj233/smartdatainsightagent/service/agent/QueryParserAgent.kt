package top.phj233.smartdatainsightagent.service.agent

import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.service.ai.DeepseekService
import top.phj233.smartdatainsightagent.service.data.DataSourceService

/**
 * 自然语言查询解析 Agent
 * @author phj233
 * @since 2026/1/5 18:17
 * @version
 */
@Service
class QueryParserAgent(
    private val deepseekService: DeepseekService,
    private val dataSourceService: DataSourceService
) {
    suspend fun parseNaturalLanguageToSQL(
        nlQuery: String,
        dataSourceId: Long,
        userId: Long
    ): String {
        val dataSource = dataSourceService.getAccessibleActiveDataSource(dataSourceId, userId)
        val schema = formatSchemaInfo(dataSource.schemaInfo)

        val prompt = """
            你是一个SQL专家。请将自然语言查询转换为SQL语句。
            数据库类型: ${dataSource.type}
            Schema结构:
            $schema
            
            用户查询: "$nlQuery"
            
            要求:
            1. 只返回一条可执行的只读 SQL 语句，不要包含Markdown标记(如 ```sql)。
            2. 优先生成 SELECT 或 WITH 查询。
            3. 不要添加解释。
        """.trimIndent()

        val rawSql = deepseekService.chatCompletion(prompt) ?: throw RuntimeException("Failed to generate SQL")
        val sql = rawSql.replace("```sql", "").replace("```", "").trim()
        validateSQL(sql)
        return sql
    }

    /**
     * 简单的SQL安全检查，防止生成潜在危险的SQL语句
     */
    fun validateSQL(sql: String) {
        val normalizedSql = sql.trim()
        require(normalizedSql.isNotBlank()) { "SQL 不能为空" }
        val compactSql = normalizedSql.replace(Regex("\\s+"), " ")
        require(compactSql.startsWith("SELECT ", ignoreCase = true) || compactSql.startsWith("WITH ", ignoreCase = true)) {
            "仅支持只读查询"
        }

        val dangerousPatterns = listOf(
            Regex("(^|\\s)(DROP|DELETE|UPDATE|INSERT|ALTER|TRUNCATE|CREATE|MERGE|GRANT|REVOKE|CALL|EXEC)(\\s|$)", RegexOption.IGNORE_CASE),
            Regex("--"),
            Regex("/\\*"),
            Regex(";\\s*\\S")
        )

        dangerousPatterns.forEach { pattern ->
            require(!pattern.containsMatchIn(compactSql)) { "检测到潜在的危险SQL操作" }
        }
    }

    private fun formatSchemaInfo(schemaInfo: List<Map<String, String>>): String {
        if (schemaInfo.isEmpty()) {
            return "Tables: unknown"
        }
        return schemaInfo.joinToString("\n") { tableInfo ->
            tableInfo.entries.joinToString(", ") { (key, value) -> "$key=$value" }
        }
    }
}
