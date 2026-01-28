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
        dataSourceId: Long
    ): String {
            val dataSource = dataSourceService.getDataSource(dataSourceId)
            // 简化：如果schema为空，则可能需要实时提取，这里假设已存在
            val schema = dataSource.schemaInfo ?: "Tables: unknown"

            val prompt = """
            你是一个SQL专家。请将自然语言查询转换为SQL语句。
            数据库类型: ${dataSource.type}
            Schema结构:
            $schema
            
            用户查询: "$nlQuery"
            
            要求:
            1. 只返回可执行的SQL语句，不要包含Markdown标记(如 ```sql)。
            2. 使用标准的SQL语法。
            3. 不要添加解释。
        """.trimIndent()

            val sql = deepseekService.chatCompletion(prompt) ?: throw RuntimeException("Failed to generate SQL")

            // 清理可能存在的 markdown 标记
            sql.replace("```sql", "").replace("```", "").trim()
            validateSQL(sql)
            return sql
        }

    fun validateSQL(sql: String) {
        // 防止SQL注入
        val dangerousPatterns = listOf("DROP", "DELETE", "UPDATE", "INSERT", "--", ";")

        dangerousPatterns.forEach { pattern ->
            if (sql.uppercase().contains(pattern)) {
                throw SecurityException("检测到潜在的危险SQL操作")
            }
        }
    }
}
