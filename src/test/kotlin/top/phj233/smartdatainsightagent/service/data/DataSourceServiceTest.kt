package top.phj233.smartdatainsightagent.service.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import top.phj233.smartdatainsightagent.entity.DataSource
import top.phj233.smartdatainsightagent.repository.DataSourceRepository

class DataSourceServiceTest {

    private val dataSourceRepository = mock(DataSourceRepository::class.java)
    private val service = DataSourceService(dataSourceRepository)

    @Test
    fun `allow owner to access active datasource`() {
        val dataSource = mock(DataSource::class.java)
        `when`(dataSourceRepository.findByIdAndUserId(1L, 99L)).thenReturn(dataSource)
        `when`(dataSource.active).thenReturn(true)

        val result = service.getAccessibleActiveDataSource(1L, 99L)

        assertEquals(dataSource, result)
    }

    @Test
    fun `reject access when datasource belongs to another user`() {
        `when`(dataSourceRepository.findByIdAndUserId(2L, 99L)).thenReturn(null)

        assertThrows(RuntimeException::class.java) {
            service.getAccessibleActiveDataSource(2L, 99L)
        }
    }
}
