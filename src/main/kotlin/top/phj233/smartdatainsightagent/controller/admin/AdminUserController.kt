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

@RestController
@SaCheckRole("admin")
@RequestMapping("/api/admin/users")
class AdminUserController(
    private val adminUserService: AdminUserService
) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): Page<UserMeResponse> {
        return adminUserService.list(PageRequest.of(page, size))
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): UserMeResponse {
        return adminUserService.detail(id)
    }

    @PostMapping
    fun create(@Valid @RequestBody input: AdminUserCreateInput): UserMeResponse {
        return adminUserService.create(input)
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody input: AdminUserUpdateInput
    ): UserMeResponse {
        return adminUserService.update(id, input)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        adminUserService.delete(id)
    }
}
