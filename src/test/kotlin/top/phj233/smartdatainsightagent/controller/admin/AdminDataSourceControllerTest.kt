package top.phj233.smartdatainsightagent.controller.admin

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import top.phj233.smartdatainsightagent.entity.dto.AdminDataSourceCreateInput
import top.phj233.smartdatainsightagent.entity.dto.AdminDataSourceUpdateInput
import top.phj233.smartdatainsightagent.entity.dto.DataSourceDetailView
import top.phj233.smartdatainsightagent.entity.enums.DataSourceType
import top.phj233.smartdatainsightagent.service.admin.AdminDataSourceService

class AdminDataSourceControllerTest {

    private val adminDataSourceService = mock(AdminDataSourceService::class.java)
    private val controller = AdminDataSourceController(adminDataSourceService)

    @Test
    fun `list endpoint delegates pagination to service`() {
        val expected = PageImpl(listOf(mock(DataSourceDetailView::class.java)))
        `when`(adminDataSourceService.list(PageRequest.of(2, 10))).thenReturn(expected)

        val result = controller.list(2, 10)

        assertSame(expected, result)
        verify(adminDataSourceService).list(PageRequest.of(2, 10))
    }

    @Test
    fun `detail endpoint delegates data source id to service`() {
        val expected = mock(DataSourceDetailView::class.java)
        `when`(adminDataSourceService.detail(3L)).thenReturn(expected)

        val result = controller.detail(3L)

        assertSame(expected, result)
        verify(adminDataSourceService).detail(3L)
    }

    @Test
    fun `create endpoint delegates request body to service`() {
        val input = createInput()
        val expected = mock(DataSourceDetailView::class.java)
        `when`(adminDataSourceService.create(input)).thenReturn(expected)

        val result = controller.create(input)

        assertSame(expected, result)
        verify(adminDataSourceService).create(input)
    }

    @Test
    fun `update endpoint forwards id input and refreshSchemaOnly`() {
        val input = updateInput()
        val expected = mock(DataSourceDetailView::class.java)
        `when`(adminDataSourceService.update(11L, input, true)).thenReturn(expected)

        val result = controller.update(11L, input, true)

        assertSame(expected, result)
        verify(adminDataSourceService).update(11L, input, true)
    }

    @Test
    fun `delete endpoint delegates data source id to service`() {
        controller.delete(11L)

        verify(adminDataSourceService).delete(11L)
    }

    private fun createInput(): AdminDataSourceCreateInput {
        return AdminDataSourceCreateInput(
            userId = 2L,
            name = "warehouse-main",
            type = DataSourceType.POSTGRESQL,
            connectionConfig = listOf(
                mapOf("host" to "127.0.0.1"),
                mapOf("port" to "5432"),
                mapOf("database" to "analytics")
            ),
            schemaInfo = null
        )
    }

    private fun updateInput(): AdminDataSourceUpdateInput {
        return AdminDataSourceUpdateInput(
            userId = 2L,
            name = "warehouse-main",
            type = DataSourceType.POSTGRESQL,
            connectionConfig = listOf(
                mapOf("host" to "127.0.0.1"),
                mapOf("port" to "5432"),
                mapOf("database" to "analytics")
            ),
            schemaInfo = null
        )
    }
}
