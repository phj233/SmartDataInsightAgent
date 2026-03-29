package top.phj233.smartdatainsightagent.controller

import cn.dev33.satoken.annotation.SaIgnore
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import top.phj233.smartdatainsightagent.entity.dto.*
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
     * 获取当前登录用户信息。
     */
    @GetMapping("/me")
    fun me(): UserMeResponse {
        return userService.getCurrentUser()
    }

    /**
     * 上传当前登录用户头像。
     */
    @PostMapping("/avatar", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadAvatar(@RequestPart("file") file: MultipartFile): UserMeResponse {
        return userService.uploadAvatar(file)
    }

    /**
     * 注销当前登录会话。
     */
    @PostMapping("/logout")
    fun logout() {
        userService.logout()
    }

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
