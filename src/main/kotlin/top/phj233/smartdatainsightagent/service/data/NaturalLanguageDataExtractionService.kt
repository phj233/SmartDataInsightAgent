package top.phj233.smartdatainsightagent.service.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.service.ai.DeepseekService

@Service
class NaturalLanguageDataExtractionService(
    private val deepseekService: DeepseekService,
    private val objectMapper: ObjectMapper
) {

    suspend fun extract(rawText: String): List<Map<String, Any>> {
        val prompt = """
            你是一个数据结构化专家。
            用户会提供一段自然语言描述，其中可能包含已经给出的统计数据、对比数据、趋势数据或分类数据。
            请你只提取用户明确给出的数据，不要补造、不要推测。
            
            请将内容整理成适合可视化的结构化数据，并仅返回 JSON。
            优先返回以下格式：
            {
              "rows": [
                {"category": "A", "value": 123},
                {"category": "B", "value": 456}
              ]
            }
            
            要求：
            1. 只返回 JSON，不要 Markdown。
            2. 数值字段保持数字类型。
            3. 如果文本中有时间、地区、产品等维度，请保留为字段。
            4. 如果能识别多个指标，可以保留多个数值字段。
            5. 如果提取不到明确数据，则返回 {"rows": []}。
            
            用户输入：
            $rawText
        """.trimIndent()

        val response = deepseekService.chatCompletion(prompt) ?: "{\"rows\": []}"
        val rows = parseExtractionResponse(response)
        require(rows.isNotEmpty()) { "未能从自然语言中提取出可视化数据" }
        return rows
    }

    internal fun parseExtractionResponse(response: String): List<Map<String, Any>> {
        val cleaned = response
            .replace("```json", "")
            .replace("```", "")
            .trim()

        if (cleaned.isBlank()) {
            return emptyList()
        }

        val root = objectMapper.readTree(cleaned)
        val rowsNode = when {
            root.isArray -> root
            root.isObject && root.has("rows") -> root.get("rows")
            else -> null
        } ?: return emptyList()

        if (!rowsNode.isArray || rowsNode.isEmpty) {
            return emptyList()
        }

        return rowsNode.mapNotNull { rowNode ->
            if (!rowNode.isObject) {
                null
            } else {
                rowNode.properties().associate { (key, value) ->
                    key to convertJsonNode(value)
                }
            }
        }
    }

    private fun convertJsonNode(node: JsonNode): Any {
        return when {
            node.isIntegralNumber -> node.longValue()
            node.isFloatingPointNumber -> node.doubleValue()
            node.isBoolean -> node.booleanValue()
            node.isTextual -> node.textValue().trim()
            node.isNull -> ""
            else -> node.toString()
        }
    }
}

