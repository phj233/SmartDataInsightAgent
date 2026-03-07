package top.phj233.smartdatainsightagent.service.data

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RawTextDataParserServiceTest {

    private val service = RawTextDataParserService(ObjectMapper())

    @Test
    fun `parse json array`() {
        val result = service.parse(
            """
            [
              {"month":"Jan","sales":1200},
              {"month":"Feb","sales":1500}
            ]
            """.trimIndent()
        )

        assertEquals(2, result.size)
        assertEquals("Jan", result.first()["month"])
        assertEquals(1200L, result.first()["sales"])
    }

    @Test
    fun `parse markdown table`() {
        val result = service.parse(
            """
            | month | sales |
            | --- | --- |
            | Jan | 1200 |
            | Feb | 1500 |
            """.trimIndent()
        )

        assertEquals(2, result.size)
        assertEquals("Feb", result.last()["month"])
        assertEquals(1500L, result.last()["sales"])
    }

    @Test
    fun `parse csv table`() {
        val result = service.parse(
            """
            month,sales,profit
            Jan,1200,200
            Feb,1500,260
            """.trimIndent()
        )

        assertEquals(2, result.size)
        assertEquals(200L, result.first()["profit"])
    }

    @Test
    fun `reject unsupported raw text`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.parse("这是一段描述性文字，但里面没有结构化表格数据")
        }
    }
}

