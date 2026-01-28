package top.phj233.smartdatainsightagent.service.ai

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.deepseek.DeepSeekChatModel
import org.springframework.ai.deepseek.DeepSeekChatOptions
import org.springframework.ai.deepseek.api.ResponseFormat
import org.springframework.stereotype.Service

/**
 *
 * @author phj233
 * @since 2026/1/5 17:09
 * @version
 */
@Service
class DeepseekService(
    private val chatClient: DeepSeekChatModel,
    private val objectMapper: ObjectMapper
) {

    suspend fun chatCompletion(prompt: String): String? {
        val response = chatClient.call(
            Prompt(prompt)
        )

        return response.result.output.text
    }

    suspend fun structuredOutput(prompt: String, responseType: Class<*>): Any {
        val systemPrompt = """
            你是一个数据分析专家。请根据用户查询生成结构化的响应。
            响应必须是有效的JSON格式。
        """

        val userMessage = UserMessage(prompt)
        val systemMessage = SystemMessage(systemPrompt)

        val messages = listOf(systemMessage, userMessage)
        // 配置响应格式为JSON对象
        val response = chatClient.call(Prompt(messages,DeepSeekChatOptions.builder()
            .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build()).build())
        )

        val content = response.result.output.text

        return objectMapper.readValue(content, responseType)
    }
}
