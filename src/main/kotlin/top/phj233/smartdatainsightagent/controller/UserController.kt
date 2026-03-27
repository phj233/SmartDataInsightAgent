package top.phj233.smartdatainsightagent.controller

import cn.dev33.satoken.annotation.SaIgnore
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.phj233.smartdatainsightagent.entity.dto.UserLoginByCodeDTO
import top.phj233.smartdatainsightagent.entity.dto.UserLoginDTO
import top.phj233.smartdatainsightagent.entity.dto.UserRegisterDTO
import top.phj233.smartdatainsightagent.entity.dto.UserSendCodeDTO
import top.phj233.smartdatainsightagent.exception.UserException
import top.phj233.smartdatainsightagent.service.RedisService
import top.phj233.smartdatainsightagent.service.UserService

/**
 * 用户控制器
 * @author phj233
 * @since 2026/2/14 19:07
 * @version
 */
@RestController
@RequestMapping("/api/user")
class UserController(
    val userService: UserService,
    val redisService: RedisService
) {
    /**
     * 发送邮箱验证码
     */
    @PostMapping("/sendCode")
    @SaIgnore
    fun sendCode(@Valid @RequestBody dto: UserSendCodeDTO) {
        userService.sendVerificationCode(dto.email)
    }

    /**
     * 用户注册
     * @param registerDTO 用户注册DTO
     * @throws UserException 验证码错误或已过期
     */
    @PostMapping("/register")
    @SaIgnore
    fun register(@Valid @RequestBody registerDTO: UserRegisterDTO) {
        if (!redisService.verifyEmailCode(registerDTO.email, registerDTO.code)) {
            throw UserException.invalidCredentials("验证码错误或已过期")
        }
        userService.register(registerDTO)
    }
    /**
     * 用户登录
     * @param loginDTO 用户登录DTO
     */
    @PostMapping("/login")
    @SaIgnore
    fun login(@Valid @RequestBody loginDTO: UserLoginDTO) {
        userService.login(loginDTO)
    }

    /**
     * 用户通过邮箱验证码登录
     * @param loginDTO 用户登录DTO
     */
    @PostMapping("/loginByCode")
    @SaIgnore
    fun loginByCode(@Valid @RequestBody loginDTO: UserLoginByCodeDTO) {
        if (!redisService.verifyEmailCode(loginDTO.email, loginDTO.code)) {
            throw UserException.invalidCredentials("验证码错误或已过期")
        }
        userService.loginByCode(loginDTO)
    }
}
