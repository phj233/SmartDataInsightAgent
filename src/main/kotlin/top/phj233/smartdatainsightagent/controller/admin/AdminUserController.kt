package top.phj233.smartdatainsightagent.controller.admin

import cn.dev33.satoken.annotation.SaCheckRole
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.*
import top.phj233.smartdatainsightagent.entity.dto.AdminUserCreateInput
import top.phj233.smartdatainsightagent.entity.dto.AdminUserUpdateInput
import top.phj233.smartdatainsightagent.entity.dto.UserMeResponse
import top.phj233.smartdatainsightagent.service.admin.AdminUserService

/**
 * 管理员用户控制器
 * @author phj233
 * @since 2026/4/22 19:14
 */
@RestController
@SaCheckRole("admin")
@RequestMapping("/api/admin/users")
class AdminUserController(
    private val adminUserService: AdminUserService
) {

    /**
     * 列出用户列表，支持分页。
     * @param page 页码，从0开始
     * @param size 每页大小
     * @return 分页的用户列表
     */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): Page<UserMeResponse> {
        return adminUserService.list(PageRequest.of(page, size))
    }

    /**
     * 获取用户详情。
     * @param id 用户ID
     * @return 用户详情视图
     */
    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): UserMeResponse {
        return adminUserService.detail(id)
    }

    /**
     * 创建用户。
     * @param input 创建输入参数
     * @return 创建成功的用户详情视图
     */
    @PostMapping
    fun create(@Valid @RequestBody input: AdminUserCreateInput): UserMeResponse {
        return adminUserService.create(input)
    }

    /**
     * 更新用户。
     * @param id 用户ID
     * @param input 更新输入参数
     * @return 更新后的用户详情视图
     */
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody input: AdminUserUpdateInput
    ): UserMeResponse {
        return adminUserService.update(id, input)
    }

    /**
     * 删除用户。
     * @param id 用户ID
     */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        adminUserService.delete(id)
    }
}
