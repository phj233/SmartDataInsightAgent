package top.phj233.smartdatainsightagent.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "minio")
class MinioProperties {
    lateinit var endpoint: String
    lateinit var accessKey: String
    lateinit var secretKey: String
    lateinit var bucket: String
    lateinit var publicBaseUrl: String
}

