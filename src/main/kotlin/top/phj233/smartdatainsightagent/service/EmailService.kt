package top.phj233.smartdatainsightagent.service

import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.io.InputStreamReader

/**
 * 邮件服务类
 *
 * @author phj233
 * @since 2026/2/20 19:29
 * @version
 */
@Service
class EmailService(private val mail: JavaMailSender,private val resourceLoader: ResourceLoader) {
    @Value("\${spring.mail.username}")
    private lateinit var from: String

    fun sendVerificationCode(to: String, code: String) {
        val message: MimeMessage = mail.createMimeMessage()
        val helper = MimeMessageHelper(message, true)
        helper.setFrom(from)
        helper.setTo(to)
        helper.setSubject("SmartDataInsight 验证码")

        // 读取HTML模板
        val resource = resourceLoader.getResource("classpath:templates/mail.html")
        val content = resource.inputStream.use { inputStream ->
            InputStreamReader(inputStream).use { reader ->
                reader.readText()
                    .replace("{username}", to)
                    .replace("{code}", code)
            }
        }
        helper.setText(content, true)
        mail.send(message)
    }
}
