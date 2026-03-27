package top.phj233.smartdatainsightagent.service

import cn.dev33.satoken.secure.BCrypt
import cn.dev33.satoken.stp.StpUtil
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.addBy
import top.phj233.smartdatainsightagent.entity.dto.UserLoginByCodeDTO
import top.phj233.smartdatainsightagent.entity.dto.UserLoginDTO
import top.phj233.smartdatainsightagent.entity.dto.UserRegisterDTO
import top.phj233.smartdatainsightagent.exception.UserException
import top.phj233.smartdatainsightagent.repository.UserRepository
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
    val emailService: EmailService
) {
    /**
     * 用户注册
     * @param userRegisterDTO 用户注册DTO
     * @throws UserException 用户已存在
     */
    fun register(userRegisterDTO: UserRegisterDTO) {
        userRepository.findUserByEmail(userRegisterDTO.email)?.let {
            throw UserException.userAlreadyExists("用户已存在")
        }
        userRegisterDTO.copy(password = BCrypt.hashpw(userRegisterDTO.password)).let {
            userRepository.save(it.toEntity{
                roles().addBy { id = 1 } // 默认分配普通用户角色
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
        val user = userRepository.findUserByEmailOrUsername(userLoginDTO.username,userLoginDTO.username) ?: throw UserException.userNotFound("用户不存在")
        if (!BCrypt.checkpw(userLoginDTO.password, user.password)) {
            throw UserException.invalidCredentials("用户名或密码错误")
        }
        StpUtil.login(user.id)
    }

    fun loginByCode(loginDTO: UserLoginByCodeDTO) {
        val user = userRepository.findUserByEmail(loginDTO.email) ?: throw UserException.userNotFound("用户不存在")
        StpUtil.login(user.id)
    }

    /**
     * 发送邮箱验证码。
     *
     * 生成 6 位数字验证码并写入 Redis，随后发送邮件。
     */
    fun sendVerificationCode(email: String) {
        val code = Random.nextInt(100000, 1000000).toString()
        redisService.generateEmailCode(email, code)
        emailService.sendVerificationCode(email, code)
    }
}
