package top.phj233.smartdatainsightagent.config

import cn.dev33.satoken.interceptor.SaInterceptor
import cn.dev33.satoken.stp.StpInterface
import cn.dev33.satoken.stp.StpUtil
import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import top.phj233.smartdatainsightagent.repository.UserRepository

/**
 * SaTokenConfig
 * - 配置 Sa-Token 的拦截器和权限验证逻辑，以及全局的 CORS 设置。
 * @author phj233
 * @since 2026/2/14 19:38
 * @version
 */
@Configuration
class SaTokenConfig : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        val saInterceptor = SaInterceptor {
            StpUtil.checkLogin()
        }

        registry.addInterceptor(object : HandlerInterceptor {
            override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
                // 只对 DispatcherType.REQUEST 的请求进行登录检查，避免对静态资源等非请求类型的访问进行拦截
                if (request.dispatcherType != DispatcherType.REQUEST) {
                    return true
                }

                // 预检请求不携带业务 token，直接放行。
                if (HttpMethod.OPTIONS.matches(request.method)) {
                    return true
                }

                return saInterceptor.preHandle(request, response, handler)
            }
        })
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/user/login",
                "/api/user/register",
                "/api/user/sendCode",
                "/api/user/loginByCode",
                "/openapi.html",
                "/openapi.yml",
                "/ts.zip",
                "/error"
            )

    }

    @Bean
    fun corsFilter() = CorsFilter(UrlBasedCorsConfigurationSource().apply {
        registerCorsConfiguration("/**", CorsConfiguration().apply {
            allowCredentials = true // 允许携带 Cookie
            addAllowedOriginPattern("*") // 允许的请求来源
            addAllowedMethod("*") // 允许的请求方法类型
            addAllowedHeader("*") // 允许的请求头类型
        })
    })
}

/**
 * StpInterFaceImpl
 * - 实现 Sa-Token 的 StpInterface 接口，用于提供用户权限和角色信息。
 * @author phj233
 * @since 2026/2/14 19:38
 * @version
 */
@Component
class StpInterFaceImpl(val userRepository: UserRepository): StpInterface{
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
    override fun getPermissionList(
        loginId: Any?,
        loginType: String?
    ): List<String?>? {
        TODO()
    }

    override fun getRoleList(
        loginId: Any?,
        loginType: String?
    ): List<String?>? {
        logger.info("""
            -----已进入 StpInterFaceImpl.getRoleList 方法-----
            loginId: $loginId - loginType: $loginType
            tokenInfo： ${StpUtil.getTokenInfo()}
            """.trimIndent())
        userRepository.findUserRolesById(loginId as Long).flatMap {
            it.roles.map { role ->
                role.name
            }
        }.also {
            logger.info("${StpUtil.getLoginId()} 用户角色列表：$it")
            return it
        }
    }
}
