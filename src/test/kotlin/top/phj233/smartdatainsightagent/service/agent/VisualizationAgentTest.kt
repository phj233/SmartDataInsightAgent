package top.phj233.smartdatainsightagent.service.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.ai.deepseek.DeepSeekChatModel
import top.phj233.smartdatainsightagent.service.ai.DeepseekService
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class VisualizationAgentTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `generate echarts option for bar chart recommendation`() {
        val deepseekService = FakeDeepseekService(
            """
            [
              {
                "chartType": "bar",
                "title": "各地区销售额对比",
                "reason": "用户关注销售对比",
                "dimensionField": "region",
                "metricFields": ["sales"]
              }
            ]
            """.trimIndent()
        )
        val visualizationAgent = VisualizationAgent(deepseekService, objectMapper)

        val visualizations = runSuspend {
            visualizationAgent.suggestVisualizations(
                listOf(
                    mapOf("region" to "华北", "sales" to 1200),
                    mapOf("region" to "华东", "sales" to 1500)
                ),
                "对比各地区销售额"
            )
        }

        assertEquals(1, visualizations.size)
        val visualization = visualizations.first()
        assertEquals("bar", visualization.type)
        assertEquals("各地区销售额对比", visualization.title)

        val option = visualization.option
        val dataset = option["dataset"] as Map<*, *>
        val source = dataset["source"] as List<*>
        assertEquals(2, source.size)
        assertTrue(option.containsKey("xAxis"))
        assertTrue(option.containsKey("yAxis"))
        assertTrue(option.containsKey("tooltip"))
        assertTrue(option.containsKey("title"))

        val series = option["series"] as List<*>
        assertEquals(1, series.size)
        val firstSeries = series.first() as Map<*, *>
        assertEquals("bar", firstSeries["type"])
        val encode = firstSeries["encode"] as Map<*, *>
        assertEquals("region", encode["x"])
        assertEquals("sales", encode["y"])
    }

    @Test
    fun `fallback still generates echarts option when ai response is invalid`() {
        val deepseekService = FakeDeepseekService("not-json")
        val visualizationAgent = VisualizationAgent(deepseekService, objectMapper)

        val visualizations = runSuspend {
            visualizationAgent.suggestVisualizations(
                listOf(
                    mapOf("month" to "1月", "sales" to 100),
                    mapOf("month" to "2月", "sales" to 160)
                ),
                "看一下销售走势"
            )
        }

        assertFalse(visualizations.isEmpty())
        val option = visualizations.first().option
        val series = option["series"] as List<*>
        assertFalse(series.isEmpty())
        val firstSeries = series.first() as Map<*, *>
        assertTrue(firstSeries.containsKey("encode"))
        val encode = firstSeries["encode"] as Map<*, *>
        assertEquals("month", encode["x"])
        assertEquals("sales", encode["y"])
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return outcome!!.getOrThrow()
    }

    private class FakeDeepseekService(
        private val response: String
    ) : DeepseekService(mock(DeepSeekChatModel::class.java), ObjectMapper()) {
        override suspend fun chatCompletion(prompt: String): String = response
    }
}
