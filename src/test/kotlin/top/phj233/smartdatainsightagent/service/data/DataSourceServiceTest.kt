package top.phj233.smartdatainsightagent.service.data

import org.babyfish.jimmer.sql.kt.KSqlClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import top.phj233.smartdatainsightagent.entity.DataSource

class DataSourceServiceTest {

    private val sqlClient = mock(KSqlClient::class.java)
    private val service = DataSourceService(sqlClient)

    @Test
    fun `allow owner to access active datasource`() {
        val dataSource = mock(DataSource::class.java)
        `when`(sqlClient.findById(DataSource::class, 1L)).thenReturn(dataSource)
        `when`(dataSource.active).thenReturn(true)
        `when`(dataSource.userId).thenReturn(99L)

        val result = service.getAccessibleActiveDataSource(1L, 99L)

        assertEquals(dataSource, result)
    }

    @Test
    fun `reject access when datasource belongs to another user`() {
        val dataSource = mock(DataSource::class.java)
        `when`(sqlClient.findById(DataSource::class, 2L)).thenReturn(dataSource)
        `when`(dataSource.active).thenReturn(true)
        `when`(dataSource.userId).thenReturn(100L)

        assertThrows(IllegalArgumentException::class.java) {
            service.getAccessibleActiveDataSource(2L, 99L)
        }
    }
}

