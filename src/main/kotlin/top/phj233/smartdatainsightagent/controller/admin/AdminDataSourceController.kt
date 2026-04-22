package top.phj233.smartdatainsightagent.controller.admin

import cn.dev33.satoken.annotation.SaCheckRole
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.*
import top.phj233.smartdatainsightagent.entity.dto.AdminDataSourceCreateInput
import top.phj233.smartdatainsightagent.entity.dto.AdminDataSourceUpdateInput
import top.phj233.smartdatainsightagent.entity.dto.DataSourceDetailView
import top.phj233.smartdatainsightagent.service.admin.AdminDataSourceService

@RestController
@SaCheckRole("admin")
@RequestMapping("/api/admin/data-sources")
class AdminDataSourceController(
    private val adminDataSourceService: AdminDataSourceService
) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): Page<DataSourceDetailView> {
        return adminDataSourceService.list(PageRequest.of(page, size))
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): DataSourceDetailView {
        return adminDataSourceService.detail(id)
    }

    @PostMapping
    fun create(@Valid @RequestBody input: AdminDataSourceCreateInput): DataSourceDetailView {
        return adminDataSourceService.create(input)
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody input: AdminDataSourceUpdateInput,
        @RequestParam(name = "refreshSchemaOnly", defaultValue = "false") refreshSchemaOnly: Boolean
    ): DataSourceDetailView {
        return adminDataSourceService.update(id, input, refreshSchemaOnly)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        adminDataSourceService.delete(id)
    }
}
