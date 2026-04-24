package top.phj233.smartdatainsightagent.service.admin

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import org.mockito.invocation.InvocationOnMock
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import top.phj233.smartdatainsightagent.entity.DataSourceDraft
import top.phj233.smartdatainsightagent.entity.dto.*
import top.phj233.smartdatainsightagent.entity.enums.DataSourceType
import top.phj233.smartdatainsightagent.exception.DataSourceException
import top.phj233.smartdatainsightagent.repository.DataSourceRepository
import top.phj233.smartdatainsightagent.repository.UserRepository
import top.phj233.smartdatainsightagent.service.data.DataSourceService

class AdminDataSourceServiceTest {

    private val dataSourceRepository = mock(DataSourceRepository::class.java) { invocation: InvocationOnMock ->
        when (invocation.method.name) {
            "save" -> invocation.arguments[0]
            else -> Answers.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val userRepository = mock(UserRepository::class.java)
    private val dataSourceService = mock(DataSourceService::class.java)
    private val service = AdminDataSourceService(dataSourceRepository, userRepository, dataSourceService)

    @Test
    fun `list should map repository page to detail views`() {
        val pageable = PageRequest.of(0, 10)
        `when`(dataSourceRepository.findAll(pageable)).thenReturn(
            PageImpl(listOf(dataSource(id = 11L, userId = 2L, name = "warehouse-main")))
        )

        val result = service.list(pageable)

        assertEquals(1, result.totalElements)
        assertEquals(11L, result.content.first().id)
        assertEquals(2L, result.content.first().userId)
        assertEquals("warehouse-main", result.content.first().name)
    }

    @Test
    fun `detail should throw when data source does not exist`() {
        `when`(dataSourceRepository.findNullable(11L)).thenReturn(null)

        assertThrows(DataSourceException::class.java) {
            service.detail(11L)
        }
    }

    @Test
    fun `create should delegate to datasource service when user exists`() {
        val input = createInput()
        val expected = mock(DataSourceDetailView::class.java)
        `when`(userRepository.existsById(2L)).thenReturn(true)
        `when`(
            dataSourceService.createForUser(
                2L,
                DataSourceCreateInput(
                    name = "warehouse-main",
                    type = DataSourceType.POSTGRESQL,
                    connectionConfig = connectionConfig(),
                    schemaInfo = null
                )
            )
        ).thenReturn(expected)

        val result = service.create(input)

        assertSame(expected, result)
        verify(dataSourceService).createForUser(
            2L,
            DataSourceCreateInput(
                name = "warehouse-main",
                type = DataSourceType.POSTGRESQL,
                connectionConfig = connectionConfig(),
                schemaInfo = null
            )
        )
    }

    @Test
    fun `update should normalize name and delegate when owner is unchanged`() {
        val input = updateInput(userId = 2L, name = "  warehouse-main  ")
        val expected = mock(DataSourceDetailView::class.java)
        `when`(userRepository.existsById(2L)).thenReturn(true)
        `when`(dataSourceRepository.findNullable(11L)).thenReturn(dataSource(id = 11L, userId = 2L, name = "old-name"))
        `when`(
            dataSourceService.updateForUser(
                11L,
                2L,
                DataSourceUpdateInput(
                    name = "warehouse-main",
                    type = DataSourceType.POSTGRESQL,
                    connectionConfig = connectionConfig(),
                    schemaInfo = null
                ),
                true
            )
        ).thenReturn(expected)

        val result = service.update(11L, input, true)

        assertSame(expected, result)
        verify(dataSourceService).updateForUser(
            11L,
            2L,
            DataSourceUpdateInput(
                name = "warehouse-main",
                type = DataSourceType.POSTGRESQL,
                connectionConfig = connectionConfig(),
                schemaInfo = null
            ),
            true
        )
    }

    @Test
    fun `update should reassign owner after delegating update`() {
        val input = updateInput(userId = 5L, name = "  warehouse-main  ")
        val existing = dataSource(id = 11L, userId = 2L, name = "old-name")
        val refreshed = dataSource(id = 11L, userId = 2L, name = "warehouse-main")
        `when`(userRepository.existsById(5L)).thenReturn(true)
        `when`(dataSourceRepository.findNullable(11L)).thenReturn(existing, refreshed)
        `when`(
            dataSourceService.updateForUser(
                11L,
                2L,
                DataSourceUpdateInput(
                    name = "warehouse-main",
                    type = DataSourceType.POSTGRESQL,
                    connectionConfig = connectionConfig(),
                    schemaInfo = null
                ),
                false
            )
        ).thenReturn(mock(DataSourceDetailView::class.java))

        val result = service.update(11L, input, false)

        assertEquals(11L, result.id)
        assertEquals(5L, result.userId)
        assertEquals("warehouse-main", result.name)
    }

    @Test
    fun `update should reject duplicate name when owner changes`() {
        val input = updateInput(userId = 5L, name = " warehouse-main ")
        `when`(userRepository.existsById(5L)).thenReturn(true)
        `when`(dataSourceRepository.findNullable(11L)).thenReturn(dataSource(id = 11L, userId = 2L, name = "old-name"))
        `when`(dataSourceRepository.existsByUserIdAndName(5L, "warehouse-main")).thenReturn(true)

        assertThrows(DataSourceException::class.java) {
            service.update(11L, input, false)
        }
    }

    @Test
    fun `delete should remove existing data source`() {
        `when`(dataSourceRepository.existsById(11L)).thenReturn(true)

        service.delete(11L)

        verify(dataSourceRepository).deleteById(11L)
    }

    private fun createInput() = AdminDataSourceCreateInput(
        userId = 2L,
        name = "warehouse-main",
        type = DataSourceType.POSTGRESQL,
        connectionConfig = connectionConfig(),
        schemaInfo = null
    )

    private fun updateInput(userId: Long, name: String) = AdminDataSourceUpdateInput(
        userId = userId,
        name = name,
        type = DataSourceType.POSTGRESQL,
        connectionConfig = connectionConfig(),
        schemaInfo = null
    )

    private fun dataSource(id: Long, userId: Long, name: String) = DataSourceDraft.`$`.produce {
        this.id = id
        user {
            this.id = userId
        }
        this.name = name
        type = DataSourceType.POSTGRESQL
        connectionConfig = connectionConfig()
        schemaInfo = emptyList()
        active = true
        createdTimeStamp = 1L
        modifiedTimeStamp = 2L
        createdBy = null
        modifiedBy = null
    }

    private fun connectionConfig() = listOf(
        mapOf("host" to "127.0.0.1"),
        mapOf("port" to "5432"),
        mapOf("database" to "analytics")
    )
}
