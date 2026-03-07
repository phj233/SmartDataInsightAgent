package top.phj233.smartdatainsightagent.service.data

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.stereotype.Component
import top.phj233.smartdatainsightagent.entity.enums.DataSourceType
import java.util.concurrent.ConcurrentHashMap

@Component
class ExternalJdbcTemplateProvider(
    private val dataSourceService: DataSourceService
) {

    private val templateCache = ConcurrentHashMap<Long, CachedJdbcTemplate>()

    fun getJdbcTemplate(dataSourceId: Long, userId: Long? = null): JdbcTemplate {
        val dataSource = userId?.let { dataSourceService.getAccessibleActiveDataSource(dataSourceId, it) }
            ?: dataSourceService.getActiveDataSource(dataSourceId)
        val connectionDetails = dataSourceService.getConnectionDetails(dataSourceId, userId)
        val fingerprint = buildFingerprint(dataSource.type, connectionDetails)
        val cached = templateCache[dataSourceId]
        if (cached != null && cached.fingerprint == fingerprint) {
            return cached.jdbcTemplate
        }

        val jdbcTemplate = JdbcTemplate(createSpringDataSource(dataSource.type, connectionDetails)).apply {
            queryTimeout = connectionDetails["queryTimeout"]?.toIntOrNull() ?: 30
        }
        templateCache[dataSourceId] = CachedJdbcTemplate(fingerprint, jdbcTemplate)
        return jdbcTemplate
    }

    private fun createSpringDataSource(
        type: DataSourceType,
        connectionDetails: Map<String, String>
    ): javax.sql.DataSource {
        return DriverManagerDataSource().apply {
            setDriverClassName(ExternalDataSourceSupport.resolveDriverClassName(type, connectionDetails))
            url = ExternalDataSourceSupport.buildJdbcUrl(type, connectionDetails)
            username = resolveCredential(connectionDetails, "username", "user")
            password = resolveCredential(connectionDetails, "password")
        }
    }

    private fun buildFingerprint(type: DataSourceType, connectionDetails: Map<String, String>): String {
        val normalized = connectionDetails.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        return "${type.name}:${normalized.entries.joinToString("|") { (key, value) -> "$key=$value" }}"
    }

    private fun resolveCredential(connectionDetails: Map<String, String>, vararg keys: String): String? {
        keys.forEach { key ->
            connectionDetails.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.let { return it }
        }
        return null
    }

    private data class CachedJdbcTemplate(
        val fingerprint: String,
        val jdbcTemplate: JdbcTemplate
    )
}
