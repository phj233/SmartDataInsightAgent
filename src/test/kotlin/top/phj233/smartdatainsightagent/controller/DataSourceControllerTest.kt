package top.phj233.smartdatainsightagent.controller

import cn.dev33.satoken.stp.StpUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import top.phj233.smartdatainsightagent.entity.dto.DataSourceDetailView
import top.phj233.smartdatainsightagent.entity.dto.DataSourceUpdateInput
import top.phj233.smartdatainsightagent.entity.enums.DataSourceType
import top.phj233.smartdatainsightagent.service.data.DataSourceService

class DataSourceControllerTest {

    private val dataSourceService = mock(DataSourceService::class.java)
    private val controller = DataSourceController(dataSourceService)

    @Test
    fun `update endpoint forwards refreshSchemaOnly true`() {
        val input = updateInput("analytics-db")
        val expected = mock(DataSourceDetailView::class.java)

        mockStatic(StpUtil::class.java).use { stpMock ->
            stpMock.`when`<Long> { StpUtil.getLoginIdAsLong() }.thenReturn(99L)
            `when`(dataSourceService.updateForUser(1L, 99L, input, true)).thenReturn(expected)

            val result = controller.updateMine(1L, input, true)

            assertEquals(expected, result)
            verify(dataSourceService).updateForUser(1L, 99L, input, true)
        }
    }

    @Test
    fun `update endpoint forwards refreshSchemaOnly false`() {
        val input = updateInput("analytics-db-v2")
        val expected = mock(DataSourceDetailView::class.java)

        mockStatic(StpUtil::class.java).use { stpMock ->
            stpMock.`when`<Long> { StpUtil.getLoginIdAsLong() }.thenReturn(100L)
            `when`(dataSourceService.updateForUser(2L, 100L, input, false)).thenReturn(expected)

            val result = controller.updateMine(2L, input, false)

            assertEquals(expected, result)
            verify(dataSourceService).updateForUser(2L, 100L, input, false)
        }
    }

    private fun updateInput(name: String): DataSourceUpdateInput {
        return DataSourceUpdateInput(
            name = name,
            type = DataSourceType.POSTGRESQL,
            connectionConfig = listOf(
                mapOf("host" to "127.0.0.1"),
                mapOf("port" to "5432"),
                mapOf("database" to "insight")
            ),
            schemaInfo = null
        )
    }
}

