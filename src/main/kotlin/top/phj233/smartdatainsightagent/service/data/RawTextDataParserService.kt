package top.phj233.smartdatainsightagent.service.data

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

/**
 * 原始文本数据解析服务
 * 支持从原始文本中智能识别并解析出 JSON 数组、Markdown 表格、CSV 或 TSV 格式的数据。
 * @author phj233
 * @since 2026/3/26 20:00
 * @version
 */

@Service
class RawTextDataParserService(
    private val objectMapper: ObjectMapper
) {

    fun parse(rawText: String): List<Map<String, Any>> {
        val normalized = rawText.trim()
        require(normalized.isNotBlank()) { "原始数据文本不能为空" }

        parseJsonArray(normalized)?.let { return it }
        extractCodeBlocks(normalized).forEach { block ->
            parseJsonArray(block)?.let { return it }
            parseMarkdownTable(block)?.let { return it }
            parseDelimitedTable(block)?.let { return it }
        }
        parseMarkdownTable(normalized)?.let { return it }
        parseDelimitedTable(normalized)?.let { return it }

        throw IllegalArgumentException("未识别到可解析的数据格式，请提供 JSON 数组、Markdown 表格、CSV 或 TSV 数据")
    }

    private fun parseJsonArray(text: String): List<Map<String, Any>>? {
        val candidates = linkedSetOf<String>()
        candidates += text.trim()
        findJsonArrayCandidate(text)?.let { candidates += it }

        candidates.forEach { candidate ->
            try {
                val tree = objectMapper.readTree(candidate)
                if (!tree.isArray || tree.isEmpty) {
                    return@forEach
                }
                val rows = tree.mapNotNull { node ->
                    if (!node.isObject) {
                        null
                    } else {
                        node.fields().asSequence().associate { entry ->
                            entry.key to normalizeValue(entry.value.asText())
                        }
                    }
                }
                if (rows.isNotEmpty()) {
                    return rows
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun parseMarkdownTable(text: String): List<Map<String, Any>>? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val separatorIndex = lines.indexOfFirst { isMarkdownSeparatorLine(it) }
        if (separatorIndex <= 0) {
            return null
        }

        val headerLine = lines[separatorIndex - 1]
        val headers = splitMarkdownRow(headerLine)
        if (headers.size < 2) {
            return null
        }

        val rows = mutableListOf<Map<String, Any>>()
        for (line in lines.drop(separatorIndex + 1)) {
            if (!line.contains("|")) {
                break
            }
            val cells = splitMarkdownRow(line)
            if (cells.size != headers.size) {
                continue
            }
            rows += headers.zip(cells).associate { (header, value) ->
                header to normalizeValue(value)
            }
        }
        return rows.takeIf { it.isNotEmpty() }
    }

    private fun parseDelimitedTable(text: String): List<Map<String, Any>>? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("```") }
        if (lines.size < 2) {
            return null
        }

        val separator = listOf('\t', ',', ';').firstOrNull { delimiter ->
            val counts = lines.map { line -> line.count { it == delimiter } }.filter { it > 0 }
            counts.size >= 2 && counts.distinct().size == 1
        } ?: return null

        val tokenized = lines.map { splitDelimitedLine(it, separator) }
        val headers = tokenized.first()
        if (headers.size < 2 || headers.any { it.isBlank() }) {
            return null
        }
        if (headers.all { isLikelyNumeric(it) }) {
            return null
        }

        val rows = tokenized.drop(1)
            .filter { it.size == headers.size }
            .map { values ->
                headers.zip(values).associate { (header, value) ->
                    header to normalizeValue(value)
                }
            }
        return rows.takeIf { it.isNotEmpty() }
    }

    private fun extractCodeBlocks(text: String): List<String> {
        return Regex("""```(?:json|csv|tsv|text)?\s*(.*?)```""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun findJsonArrayCandidate(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start !in 0..<end) {
            return null
        }
        return text.substring(start, end + 1)
    }

    private fun isMarkdownSeparatorLine(line: String): Boolean {
        val normalized = line.removePrefix("|").removeSuffix("|")
        val parts = normalized.split('|').map { it.trim() }
        return parts.isNotEmpty() && parts.all { it.matches(Regex(":?-{3,}:?")) }
    }

    private fun splitMarkdownRow(line: String): List<String> {
        return line.removePrefix("|").removeSuffix("|")
            .split('|')
            .map { it.trim() }
    }

    private fun splitDelimitedLine(line: String, delimiter: Char): List<String> {
        return line.split(delimiter).map { it.trim().removeSurrounding("\"") }
    }

    private fun normalizeValue(value: String): Any {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        trimmed.toLongOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }
        when (trimmed.lowercase()) {
            "true" -> return true
            "false" -> return false
        }
        return trimmed
    }

    private fun isLikelyNumeric(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.toLongOrNull() != null || trimmed.toDoubleOrNull() != null
    }
}

