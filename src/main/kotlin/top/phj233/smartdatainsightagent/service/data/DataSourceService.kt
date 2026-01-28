package top.phj233.smartdatainsightagent.service.data

import org.babyfish.jimmer.sql.kt.KSqlClient
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.DataSource

/**
 * @author phj233
 * @since 2026/1/28 15:04
 * @version
 */
@Service
class DataSourceService(
    private val sqlClient: KSqlClient
) {
    fun getDataSource(id: Long): DataSource {
        return sqlClient.findById(DataSource::class, id)
            ?: throw IllegalArgumentException("DataSource not found: $id")
    }

    // 这里通常需要根据 DataSource 配置动态创建 JDBC 连接
    // 为了简化，假设我们返回当前的 Connection 或者根据配置构建
    fun getConnectionDetails(dataSourceId: Long): Map<String, String> {
        // 实现获取连接信息的逻辑
        return emptyMap()
    }
}
