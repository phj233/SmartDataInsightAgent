package top.phj233.smartdatainsightagent.service.data

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.exception.DataSourceException

/**
 * 查询执行服务
 * @author phj233
 * @since 2026/1/28 15:04
 * @version
 */
@Service
class QueryExecutorService(
    private val jdbcTemplate: JdbcTemplate,
    private val externalJdbcTemplateProvider: ExternalJdbcTemplateProvider
) {
    private val logger = LoggerFactory.getLogger(QueryExecutorService::class.java)

    fun executeQuery(sql: String): List<Map<String, Any>> {
        validateReadOnlySql(sql)
        logger.info("[查询执行] 使用默认数据源执行只读SQL")
        return try {
            jdbcTemplate.queryForList(sql)
        } catch (ex: CannotGetJdbcConnectionException) {
            logger.error("[查询执行] 默认数据源连接失败, sql={}", sql, ex)
            throw DataSourceException.invalidConnectionConfig("默认数据源连接失败，请检查数据库服务状态与连接配置")
        } catch (ex: DataAccessException) {
            logger.error("[查询执行] 默认数据源执行SQL失败, sql={}", sql, ex)
            throw ex
        }
    }

    fun executeQuery(sql: String, dataSourceId: Long, userId: Long? = null): List<Map<String, Any>> {
        validateReadOnlySql(sql)
        logger.info("[查询执行] 执行只读SQL, dataSourceId={}, userId={}", dataSourceId, userId)
        return try {
            externalJdbcTemplateProvider.getJdbcTemplate(dataSourceId, userId).queryForList(sql)
        } catch (ex: CannotGetJdbcConnectionException) {
            logger.error("[查询执行] 外部数据源连接失败, dataSourceId={}, userId={}, sql={}", dataSourceId, userId, sql, ex)
            throw DataSourceException.invalidConnectionConfig(
                "数据源($dataSourceId)连接失败，请检查host、port、用户名、密码以及数据库服务是否可达"
            )
        } catch (ex: DataAccessException) {
            logger.error("[查询执行] 外部数据源执行SQL失败, dataSourceId={}, userId={}, sql={}", dataSourceId, userId, sql, ex)
            throw ex
        }
    }

    internal fun validateReadOnlySql(sql: String) {
        val normalizedSql = sql.trim()
        require(normalizedSql.isNotBlank()) { "SQL 不能为空" }

        val compactSql = normalizedSql.replace(Regex("\\s+"), " ")
        require(
            compactSql.startsWith("SELECT ", ignoreCase = true) ||
                compactSql.startsWith("WITH ", ignoreCase = true)
        ) {
            "仅支持只读查询"
        }

        val dangerousPatterns = listOf(
            Regex(
                "(^|\\s)(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|MERGE|GRANT|REVOKE|CALL|EXEC)(\\s|$)",
                RegexOption.IGNORE_CASE
            ),
            Regex("--"),
            Regex("/\\*"),
            Regex(";\\s*\\S")
        )
        dangerousPatterns.forEach { pattern ->
            require(!pattern.containsMatchIn(compactSql)) { "检测到潜在危险SQL操作" }
        }
    }
}
