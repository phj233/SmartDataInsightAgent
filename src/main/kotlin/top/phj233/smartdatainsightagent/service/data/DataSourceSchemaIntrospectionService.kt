package top.phj233.smartdatainsightagent.service.data

import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.enums.DataSourceType

/**
 * 基于 JDBC 元数据探测外部数据源结构。
 * @author phj233
 * @since 2026/3/29 14:13
 */
@Service
class DataSourceSchemaIntrospectionService {

    /**
     * 自动探测 schema，失败时返回空列表。
     */
    fun introspect(
        type: DataSourceType,
        connectionDetails: Map<String, String>
    ): List<Map<String, String>> {
        return runCatching {
            inspectByMetadata(type, connectionDetails)
        }.getOrElse { emptyList() }
    }

    private fun inspectByMetadata(
        type: DataSourceType,
        connectionDetails: Map<String, String>
    ): List<Map<String, String>> {
        if (type == DataSourceType.CSV || type == DataSourceType.EXCEL) {
            return emptyList()
        }

        val dataSource = DriverManagerDataSource().apply {
            setDriverClassName(ExternalDataSourceSupport.resolveDriverClassName(type, connectionDetails))
            url = ExternalDataSourceSupport.buildJdbcUrl(type, connectionDetails)
            username = resolveCredential(connectionDetails, "username", "user")
            password = resolveCredential(connectionDetails, "password")
        }

        dataSource.connection.use { connection ->
            val metadata = connection.metaData
            val catalog = resolveCatalog(type, connectionDetails)
            val schema = resolveSchema(type, connectionDetails)
            val rows = mutableListOf<Map<String, String>>()

            metadata.getTables(catalog, schema, "%", arrayOf("TABLE")).use { tables ->
                while (tables.next()) {
                    val tableName = tables.getString("TABLE_NAME") ?: continue
                    val tableSchema = tables.getString("TABLE_SCHEM") ?: schema.orEmpty()
                    metadata.getColumns(catalog, tableSchema.ifBlank { schema }, tableName, "%").use { columns ->
                        while (columns.next()) {
                            rows += mapOf(
                                "schema" to tableSchema,
                                "table" to tableName,
                                "column" to (columns.getString("COLUMN_NAME") ?: ""),
                                "type" to (columns.getString("TYPE_NAME") ?: ""),
                                "nullable" to (columns.getInt("NULLABLE") != 0).toString()
                            )
                        }
                    }
                }
            }
            return rows
        }
    }

    private fun resolveCredential(connectionDetails: Map<String, String>, vararg keys: String): String? {
        keys.forEach { key ->
            connectionDetails.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.let { return it }
        }
        return null
    }

    private fun resolveCatalog(type: DataSourceType, connectionDetails: Map<String, String>): String? {
        return when (type) {
            DataSourceType.MYSQL -> resolveValue(connectionDetails, "database", "dbName", "dbname", "databaseName")
            else -> null
        }
    }

    private fun resolveSchema(type: DataSourceType, connectionDetails: Map<String, String>): String? {
        return when (type) {
            DataSourceType.POSTGRESQL -> resolveValue(connectionDetails, "schema", "currentSchema") ?: "public"
            else -> null
        }
    }

    private fun resolveValue(connectionDetails: Map<String, String>, vararg keys: String): String? {
        keys.forEach { key ->
            connectionDetails.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.let { return it }
        }
        return null
    }
}

