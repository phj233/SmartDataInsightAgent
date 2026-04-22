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

/**
 * 管理员数据源控制器
 * @author phj233
 * @since 2026/4/22 19:10
 */
@RestController
@SaCheckRole("admin")
@RequestMapping("/api/admin/data-sources")
class AdminDataSourceController(
    private val adminDataSourceService: AdminDataSourceService
) {

    /**
     * 列出数据源列表，支持分页。
     * @param page 页码，从0开始
     * @param size 每页大小
     * @return 分页的数据源详情列表
     */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): Page<DataSourceDetailView> {
        return adminDataSourceService.list(PageRequest.of(page, size))
    }

    /**
     * 获取数据源详情。
     * @param id 数据源ID
     * @return 数据源详情视图
     */
    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): DataSourceDetailView {
        return adminDataSourceService.detail(id)
    }

    /**
     * 创建数据源。
     * @param input 创建输入参数
     * @return 创建成功的数据源详情视图
     */
    @PostMapping
    fun create(@Valid @RequestBody input: AdminDataSourceCreateInput): DataSourceDetailView {
        return adminDataSourceService.create(input)
    }

    /**
     * 更新数据源。
     * @param id 数据源ID
     * @param input 更新输入参数
     * @param refreshSchemaOnly 是否仅刷新数据源的表结构信息，默认为false
     * @return 更新后的数据源详情视图
     */
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody input: AdminDataSourceUpdateInput,
        @RequestParam(name = "refreshSchemaOnly", defaultValue = "false") refreshSchemaOnly: Boolean
    ): DataSourceDetailView {
        return adminDataSourceService.update(id, input, refreshSchemaOnly)
    }

    /**
     * 删除数据源。
     * @param id 数据源ID
     */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        adminDataSourceService.delete(id)
    }
}
