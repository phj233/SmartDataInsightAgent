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

    fun getActiveDataSource(id: Long): DataSource {
        val dataSource = getDataSource(id)
        require(dataSource.active) { "DataSource is inactive: $id" }
        return dataSource
    }

    fun getAccessibleActiveDataSource(id: Long, userId: Long): DataSource {
        val dataSource = getActiveDataSource(id)
        require(dataSource.userId == userId) { "DataSource access denied: $id" }
        return dataSource
    }

    // 这里根据 DataSource 配置动态创建 JDBC 连接
    fun getConnectionDetails(dataSourceId: Long, userId: Long? = null): Map<String, String> {
        val dataSource = userId?.let { getAccessibleActiveDataSource(dataSourceId, it) }
            ?: getActiveDataSource(dataSourceId)
        return ExternalDataSourceSupport.flattenConnectionConfig(dataSource.connectionConfig)
    }
}
