package top.phj233.smartdatainsightagent.service.data

import top.phj233.smartdatainsightagent.entity.enums.DataSourceType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ExternalDataSourceSupport {

    fun flattenConnectionConfig(connectionConfig: List<Map<String, String>>): Map<String, String> {
        val normalized = linkedMapOf<String, String>()
        connectionConfig.forEach { item ->
            item.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    normalized[key.trim()] = value.trim()
                }
            }
        }
        return normalized.toMap()
    }

    fun buildJdbcUrl(type: DataSourceType, connectionDetails: Map<String, String>): String {
        resolveValue(connectionDetails, "url", "jdbcUrl")?.takeIf { it.isNotBlank() }?.let { return it }

        return when (type) {
            DataSourceType.POSTGRESQL -> buildPostgresUrl(connectionDetails)
            DataSourceType.MYSQL -> buildMysqlUrl(connectionDetails)
            DataSourceType.EXCEL, DataSourceType.CSV -> {
                throw IllegalArgumentException("暂不支持 ${type.name} 类型的数据源直连")
            }
        }
    }

    fun resolveDriverClassName(type: DataSourceType, connectionDetails: Map<String, String>): String {
        resolveValue(connectionDetails, "driverClassName", "driver-class-name", "driver")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return when (type) {
            DataSourceType.POSTGRESQL -> "org.postgresql.Driver"
            DataSourceType.MYSQL -> "com.mysql.cj.jdbc.Driver"
            DataSourceType.EXCEL, DataSourceType.CSV -> {
                throw IllegalArgumentException("暂不支持 ${type.name} 类型的数据源驱动")
            }
        }
    }

    private fun buildPostgresUrl(connectionDetails: Map<String, String>): String {
        val host = requireValue(connectionDetails, "host")
        val port = resolveValue(connectionDetails, "port") ?: "5432"
        val database = requireValue(connectionDetails, "database", "dbName", "dbname", "databaseName")
        val params = linkedMapOf<String, String>()

        parseRawParams(resolveValue(connectionDetails, "params", "query", "options")).forEach { (key, value) ->
            params.putIfAbsent(key, value)
        }
        resolveValue(connectionDetails, "schema", "currentSchema")?.let { params.putIfAbsent("currentSchema", it) }

        return "jdbc:postgresql://$host:$port/$database${toQueryString(params)}"
    }

    private fun buildMysqlUrl(connectionDetails: Map<String, String>): String {
        val host = requireValue(connectionDetails, "host")
        val port = resolveValue(connectionDetails, "port") ?: "3306"
        val database = requireValue(connectionDetails, "database", "dbName", "dbname", "databaseName")
        val params = linkedMapOf(
            "useUnicode" to "true",
            "characterEncoding" to "UTF-8",
            "serverTimezone" to "Asia/Shanghai"
        )

        parseRawParams(resolveValue(connectionDetails, "params", "query", "options")).forEach { (key, value) ->
            params[key] = value
        }

        return "jdbc:mysql://$host:$port/$database${toQueryString(params)}"
    }

    private fun toQueryString(params: Map<String, String>): String {
        if (params.isEmpty()) {
            return ""
        }
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "?$query"
    }

    private fun parseRawParams(rawParams: String?): Map<String, String> {
        if (rawParams.isNullOrBlank()) {
            return emptyMap()
        }
        return rawParams.split("&")
            .mapNotNull { pair ->
                val normalized = pair.trim().removePrefix("?")
                if (normalized.isBlank()) {
                    null
                } else {
                    val parts = normalized.split("=", limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank()) {
                        parts[0].trim() to parts[1].trim()
                    } else {
                        null
                    }
                }
            }
            .toMap()
    }

    private fun resolveValue(connectionDetails: Map<String, String>, vararg keys: String): String? {
        keys.forEach { key ->
            connectionDetails.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.let { return it }
        }
        return null
    }

    private fun requireValue(connectionDetails: Map<String, String>, vararg keys: String): String {
        return resolveValue(connectionDetails, *keys)
            ?: throw IllegalArgumentException("缺少连接配置: ${keys.joinToString("/")}")
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}

