package top.phj233.smartdatainsightagent.service.storage

import io.minio.*
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import top.phj233.smartdatainsightagent.config.MinioProperties
import top.phj233.smartdatainsightagent.exception.UserException
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minio 存储服务
 * @author phj233
 * @since 2026/3/29 15:40
 */
@Service
class MinioService(
    private val minioClient: MinioClient,
    private val minioProperties: MinioProperties
) {
    private val logger = LoggerFactory.getLogger(MinioService::class.java)

    private val allowedExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
    private val bucketInitialized = AtomicBoolean(false)

    /**
     * 上传头像文件并返回可复用的直链。
     * 相同文件内容会映射为相同对象名，从而复用链接。
     */
    fun uploadAvatar(file: MultipartFile): String {
        logger.info("[MinIO] 开始上传头像，userId={}, fileName={}, size={}", MDC.get("userId"), file.originalFilename, file.size)
        val ext = detectAndValidateExtension(file)
        val bytes = readAndValidateBytes(file)

        try {
            ensureBucketReady()

            val digest = sha256Hex(bytes)
            val objectName = "avatar/reuse/$digest.$ext"

            if (!objectExists(objectName)) {
                logger.info("[MinIO] 上传新对象，userId={}, objectName={}", MDC.get("userId"), objectName)
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(minioProperties.bucket)
                        .`object`(objectName)
                        .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                        .contentType(file.contentType ?: "application/octet-stream")
                        .build()
                )
            } else {
                logger.info("[MinIO] 复用已存在对象，userId={}, objectName={}", MDC.get("userId"), objectName)
            }

            return buildDirectUrl(objectName)
        } catch (ex: UserException) {
            throw ex
        } catch (ex: Exception) {
            throw UserException.avatarUploadFailed("头像上传失败", ex)
        }
    }

    /**
     * 读取文件字节并进行基本验证，如非空和大小限制。
     */
    private fun readAndValidateBytes(file: MultipartFile): ByteArray {
        if (file.isEmpty) {
            throw UserException.avatarInvalidFile("头像文件不能为空")
        }
        if (file.size > 5L * 1024 * 1024) {
            throw UserException.avatarInvalidFile("头像文件不能超过5MB")
        }
        return try {
            file.bytes
        } catch (ex: Exception) {
            throw UserException.avatarUploadFailed("读取头像文件失败", ex)
        }
    }

    /**
     * 检测文件扩展名是否合法，并返回小写的扩展名。
     */
    private fun detectAndValidateExtension(file: MultipartFile): String {
        val filename = file.originalFilename ?: throw UserException.avatarInvalidFile("文件名不能为空")
        val ext = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isBlank() || ext !in allowedExtensions) {
            throw UserException.avatarInvalidFile("仅支持 jpg/jpeg/png/webp/gif 图片")
        }
        return ext
    }

    private fun ensureBucketReady() {
        if (bucketInitialized.get()) {
            return
        }
        synchronized(this) {
            if (bucketInitialized.get()) {
                return
            }

            val bucket = minioProperties.bucket
            val exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                    .bucket(bucket)
                    .build()
            )
            if (!exists) {
                logger.info("[MinIO] Bucket 不存在，自动创建，bucket={}", bucket)
                minioClient.makeBucket(
                    MakeBucketArgs.builder()
                        .bucket(bucket)
                        .build()
                )
            }
            bucketInitialized.set(true)
            logger.info("[MinIO] Bucket 初始化完成，bucket={}", bucket)
        }
    }


    private fun objectExists(objectName: String): Boolean {
        return try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(minioProperties.bucket)
                    .`object`(objectName)
                    .build()
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildDirectUrl(objectName: String): String {
        val baseUrl = minioProperties.endpoint.trimEnd('/')
        return "$baseUrl/${minioProperties.bucket}/$objectName"
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

