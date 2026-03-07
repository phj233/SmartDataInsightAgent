package top.phj233.smartdatainsightagent.service.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import top.phj233.smartdatainsightagent.entity.enums.DataSourceType

class ExternalDataSourceSupportTest {

    @Test
    fun `flatten connection config merges non blank values`() {
        val flattened = ExternalDataSourceSupport.flattenConnectionConfig(
            listOf(
                mapOf("host" to " db.example.com ", "port" to "5432"),
                mapOf("database" to "analytics", "username" to " analyst "),
                mapOf("password" to "secret", "ignored" to "")
            )
        )

        assertEquals(
            mapOf(
                "host" to "db.example.com",
                "port" to "5432",
                "database" to "analytics",
                "username" to "analyst",
                "password" to "secret"
            ),
            flattened
        )
    }

    @Test
    fun `build postgres jdbc url from structured config`() {
        val jdbcUrl = ExternalDataSourceSupport.buildJdbcUrl(
            DataSourceType.POSTGRESQL,
            mapOf(
                "host" to "pg.internal",
                "port" to "5432",
                "database" to "insight",
                "schema" to "public",
                "params" to "sslmode=require"
            )
        )

        assertEquals(
            "jdbc:postgresql://pg.internal:5432/insight?sslmode=require&currentSchema=public",
            jdbcUrl
        )
    }

    @Test
    fun `build mysql jdbc url adds defaults and keeps custom params`() {
        val jdbcUrl = ExternalDataSourceSupport.buildJdbcUrl(
            DataSourceType.MYSQL,
            mapOf(
                "host" to "mysql.internal",
                "database" to "sales",
                "params" to "serverTimezone=UTC&allowPublicKeyRetrieval=true"
            )
        )

        assertEquals(
            "jdbc:mysql://mysql.internal:3306/sales?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true",
            jdbcUrl
        )
    }

    @Test
    fun `prefer explicit jdbc url when provided`() {
        val jdbcUrl = ExternalDataSourceSupport.buildJdbcUrl(
            DataSourceType.POSTGRESQL,
            mapOf("url" to "jdbc:postgresql://direct-host:5432/direct_db")
        )

        assertEquals("jdbc:postgresql://direct-host:5432/direct_db", jdbcUrl)
    }

    @Test
    fun `csv direct connection is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExternalDataSourceSupport.buildJdbcUrl(DataSourceType.CSV, emptyMap())
        }
    }
}

