package top.phj233.smartdatainsightagent.controller.admin

import cn.dev33.satoken.annotation.SaCheckRole
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import top.phj233.smartdatainsightagent.entity.Role
import top.phj233.smartdatainsightagent.entity.dto.RoleCreate
import top.phj233.smartdatainsightagent.entity.dto.RoleUpdate
import top.phj233.smartdatainsightagent.service.admin.AdminRoleService

/**
 * 管理员角色控制器
 * @author phj233
 * @since 2026/4/22 19:12
 */
@RestController
@Validated
@SaCheckRole("admin")
@RequestMapping("/api/admin/roles")
class AdminRoleController(
    private val adminRoleService: AdminRoleService
) {

    /**
     * 列出角色列表，支持分页。
     * @param page 页码，从0开始
     * @param size 每页大小
     * @return 分页的角色列表
     */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): Page<Role> {
        return adminRoleService.list(PageRequest.of(page, size))
    }

    /**
     * 获取角色详情。
     * @param id 角色ID
     * @return 角色详情
     */
    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): Role {
        return adminRoleService.detail(id)
    }

    /**
     * 创建角色。
     * @param input 创建输入参数
     * @return 创建成功的角色详情
     */
    @PostMapping
    fun create(@RequestBody @Valid input: RoleCreate): Role {
        return adminRoleService.create(input)
    }

    /**
     * 更新角色。
     * @param id 角色ID
     * @param roleUpdate 更新输入参数
     * @return 更新后的角色详情
     */
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid roleUpdate: RoleUpdate
    ): Role {
        return adminRoleService.update(id, roleUpdate)
    }

    /**
     * 删除角色。
     * @param id 角色ID
     */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        adminRoleService.delete(id)
    }
}
