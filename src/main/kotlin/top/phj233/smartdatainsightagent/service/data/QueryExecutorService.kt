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
    private val dataSourceService: DataSourceService
) {
    fun executeQuery(sql: String): List<Map<String, Any>> {
        // 简单实现：直接使用当前配置的 jdbcTemplate
        // 生产环境需根据 dataSourceId 获取对应的 Connection
        return jdbcTemplate.queryForList(sql)
    }
}
