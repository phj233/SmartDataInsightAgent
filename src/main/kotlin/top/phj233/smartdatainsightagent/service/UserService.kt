package top.phj233.smartdatainsightagent.service

import cn.dev33.satoken.secure.BCrypt
import cn.dev33.satoken.stp.StpUtil
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import top.phj233.smartdatainsightagent.entity.addBy
import top.phj233.smartdatainsightagent.entity.copy
import top.phj233.smartdatainsightagent.entity.dto.*
import top.phj233.smartdatainsightagent.exception.UserException
import top.phj233.smartdatainsightagent.repository.UserRepository
import top.phj233.smartdatainsightagent.service.storage.MinioService
import kotlin.random.Random

/**
 * @author phj233
 * @since 2026/2/20 17:24
 * @version
 */
@Service
class UserService(
    val userRepository: UserRepository,
    val redisService: RedisService,
    val emailService: EmailService,
    val minioService: MinioService
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    /**
     * 获取当前登录用户信息。
     */
    fun getCurrentUser(): UserMeResponse {
        val loginId = StpUtil.getLoginIdAsLong()
        logger.info("[用户服务] 获取当前用户信息，userId={}", loginId)
        val user = userRepository.findMeById(loginId)
            ?: throw UserException.userNotFound("用户不存在")
        return UserMeResponse(
            id = user.id,
            username = user.username,
            email = user.email,
            avatar = user.avatar,
            enabled = user.enabled,
            roles = user.roles.map { it.name }
        )
    }

    /**
     * 上传当前登录用户头像并更新头像链接。
     */
    fun uploadAvatar(file: MultipartFile): UserMeResponse {
        val loginId = StpUtil.getLoginIdAsLong()
        logger.info("[用户服务] 上传头像，userId={}, fileName={}, size={}", loginId, file.originalFilename, file.size)
        val user = userRepository.findNullable(loginId)
            ?: throw UserException.userNotFound("用户不存在")

        val avatarUrl = minioService.uploadAvatar(file)
        userRepository.save(
            user.copy {
                avatar = avatarUrl
            },
            SaveMode.UPDATE_ONLY
        )

        return getCurrentUser()
    }

    /**
     * 注销当前会话。
     */
    fun logout() {
        logger.info("[用户服务] 用户登出，userId={}", StpUtil.getLoginIdDefaultNull())
        StpUtil.logout()
    }

    /**
     * 修改当前登录用户信息（支持用户名、密码、邮箱、头像）。
     */
    fun updateCurrentUser(updateDTO: UserUpdateProfileDTO): UserMeResponse {
        val loginId = StpUtil.getLoginIdAsLong()
        val user = userRepository.findNullable(loginId)
            ?: throw UserException.userNotFound("用户不存在")
        logger.info("[用户服务] 更新用户信息请求，userId={}, updateDTO={}", loginId, updateDTO)
        val updatedUser = user.copy {
            updateDTO.password?.let { password = BCrypt.hashpw(it) }
        }
        userRepository.save(updatedUser, SaveMode.UPDATE_ONLY, AssociatedSaveMode.UPDATE, null)
        logger.info("[用户服务] 用户信息已更新，userId={}", loginId)
        return getCurrentUser()
    }

    /**
     * 用户注册
     * @param userRegisterDTO 用户注册DTO
     * @throws UserException 用户已存在
     */
    fun register(userRegisterDTO: UserRegisterDTO) {
        logger.info("[用户服务] 注册请求，email={}", userRegisterDTO.email)
        userRepository.findUserByEmail(userRegisterDTO.email)?.let {
            throw UserException.userAlreadyExists("用户已存在")
        }
        userRegisterDTO.copy(password = BCrypt.hashpw(userRegisterDTO.password)).let {
            userRepository.save(it.toEntity{
                roles().addBy { id = 2 } // 默认分配普通用户角色
                enabled = true
            }, SaveMode.INSERT_ONLY)
        }
    }

    /**
     * 用户登录
     * @param userLoginDTO 用户登录DTO
     * @throws UserException 用户不存在或用户名或密码错误
     */
    fun login(userLoginDTO: UserLoginDTO) {
        logger.info("[用户服务] 密码登录请求，principal={}", userLoginDTO.username)
        val user = userRepository.findUserByEmailOrUsername(userLoginDTO.username,userLoginDTO.username) ?: throw UserException.userNotFound("用户不存在")
        if (!BCrypt.checkpw(userLoginDTO.password, user.password)) {
            throw UserException.invalidCredentials("用户名或密码错误")
        }
        StpUtil.login(user.id)
        logger.info("[用户服务] 密码登录成功，userId={}", user.id)
    }

    fun loginByCode(loginDTO: UserLoginByCodeDTO) {
        logger.info("[用户服务] 验证码登录请求，email={}", loginDTO.email)
        val user = userRepository.findUserByEmail(loginDTO.email) ?: throw UserException.userNotFound("用户不存在")
        StpUtil.login(user.id)
        logger.info("[用户服务] 验证码登录成功，userId={}", user.id)
    }

    /**
     * 发送邮箱验证码。
     *
     * 生成 6 位数字验证码并写入 Redis，随后发送邮件。
     */
    fun sendVerificationCode(email: String) {
        logger.info("[用户服务] 发送邮箱验证码，email={}", email)
        val code = Random.nextInt(100000, 1000000).toString()
        redisService.generateEmailCode(email, code)
        emailService.sendVerificationCode(email, code)
    }
}
