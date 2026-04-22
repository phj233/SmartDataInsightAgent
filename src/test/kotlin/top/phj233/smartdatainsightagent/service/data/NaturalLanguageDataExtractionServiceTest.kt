package top.phj233.smartdatainsightagent.service.data

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import top.phj233.smartdatainsightagent.service.ai.DeepseekService

class NaturalLanguageDataExtractionServiceTest {

    private val service = NaturalLanguageDataExtractionService(
        mock(DeepseekService::class.java),
        ObjectMapper()
    )

    @Test
    fun `parse object response with rows`() {
        val rows = service.parseExtractionResponse(
            """
            {
              "rows": [
                {"month": "一月", "sales": 1200, "profit": 200},
                {"month": "二月", "sales": 1500, "profit": 260}
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, rows.size)
        assertEquals("一月", rows.first()["month"])
        assertEquals(1200L, rows.first()["sales"])
        assertEquals(260L, rows.last()["profit"])
    }

    @Test
    fun `parse markdown wrapped json response`() {
        val rows = service.parseExtractionResponse(
            """
            ```json
            [
              {"region": "华北", "orders": 80},
              {"region": "华东", "orders": 95}
            ]
            ```
            """.trimIndent()
        )

        assertEquals(2, rows.size)
        assertEquals("华东", rows.last()["region"])
        assertEquals(95L, rows.last()["orders"])
    }

    @Test
    fun `return empty list when response has no rows`() {
        val rows = service.parseExtractionResponse("{\"rows\": []}")
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `throw user friendly message when response is invalid json`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.parseExtractionResponse("{\"rows\":[{\"name\":\"A\"")
        }
        assertEquals("自然语言数据提取失败，请简化输入后重试", ex.message)
    }
}

