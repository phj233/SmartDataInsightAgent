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

@RestController
@Validated
@SaCheckRole("admin")
@RequestMapping("/api/admin/roles")
class AdminRoleController(
    private val adminRoleService: AdminRoleService
) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): Page<Role> {
        return adminRoleService.list(PageRequest.of(page, size))
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): Role {
        return adminRoleService.detail(id)
    }

    @PostMapping
    fun create(@RequestBody @Valid input: RoleCreate): Role {
        return adminRoleService.create(input)
    }

    @PutMapping("/{id}")
    fun update(
        @RequestBody @Valid roleUpdate: RoleUpdate
    ): Role {
        return adminRoleService.update(roleUpdate)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        adminRoleService.delete(id)
    }
}

