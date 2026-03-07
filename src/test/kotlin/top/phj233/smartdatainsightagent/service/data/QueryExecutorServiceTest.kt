package top.phj233.smartdatainsightagent.service.data

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate

class QueryExecutorServiceTest {

    private val service = QueryExecutorService(
        mock(JdbcTemplate::class.java),
        mock(ExternalJdbcTemplateProvider::class.java)
    )

    @Test
    fun `allow select query`() {
        assertDoesNotThrow {
            service.validateReadOnlySql("SELECT * FROM orders")
        }
    }

    @Test
    fun `allow common table expression query`() {
        assertDoesNotThrow {
            service.validateReadOnlySql("WITH stats AS (SELECT 1) SELECT * FROM stats")
        }
    }

    @Test
    fun `reject update query`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.validateReadOnlySql("UPDATE orders SET amount = 1")
        }
    }

    @Test
    fun `reject multi statement query`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.validateReadOnlySql("SELECT * FROM orders; DELETE FROM orders")
        }
    }
}

