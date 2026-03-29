package top.phj233.smartdatainsightagent.controller

import cn.dev33.satoken.stp.StpUtil
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import top.phj233.smartdatainsightagent.entity.dto.DataSourceCreateInput
import top.phj233.smartdatainsightagent.entity.dto.DataSourceDetailView
import top.phj233.smartdatainsightagent.entity.dto.DataSourceSummaryView
import top.phj233.smartdatainsightagent.entity.dto.DataSourceUpdateInput
import top.phj233.smartdatainsightagent.service.data.DataSourceService

/**
 * 用户数据源控制器。
 *
 * 所有接口都要求登录，且只能操作自己的数据源。
 * @author phj233
 * @since 2026/3/24 17:32
 */
@RestController
@Validated
@RequestMapping("/api/data-sources")
class DataSourceController(
    private val dataSourceService: DataSourceService
) {

    /**
     * 添加用户数据源
     * @param input 包含数据源连接信息的输入DTO
     * @return 创建成功的数据源详情视图
     */
    @PostMapping
    fun create(@Valid @RequestBody input: DataSourceCreateInput): DataSourceDetailView {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return dataSourceService.createForUser(currentUserId, input)
    }

    /**
     * 列出用户数据源摘要
     * @return 当前用户的数据源摘要列表
     */
    @GetMapping
    fun listMine(): List<DataSourceSummaryView> {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return dataSourceService.listForUser(currentUserId)
    }

    /**
     * 查询用户数据源详情
     * @param id 数据源ID
     * @return 数据源详情视图
     */
    @GetMapping("/{id}")
    fun detailMine(@PathVariable id: Long): DataSourceDetailView {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return dataSourceService.getDetailForUser(id, currentUserId)
    }

    /**
     * 更新用户数据源
     * @param id 数据源ID
     * @param input 包含更新后数据源连接信息的输入DTO
     * @param refreshSchemaOnly 是否仅刷新 schema（默认 false）
     * @return 更新成功的数据源详情视图
     */
    @PutMapping("/{id}")
    fun updateMine(
        @PathVariable id: Long,
        @Valid @RequestBody input: DataSourceUpdateInput,
        @RequestParam(name = "refreshSchemaOnly", defaultValue = "false") refreshSchemaOnly: Boolean
    ): DataSourceDetailView {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return dataSourceService.updateForUser(id, currentUserId, input, refreshSchemaOnly)
    }

    /**
     * 停用用户数据源
     * @param id 数据源ID
     * @return 停用成功的数据源详情视图
     */
    @PatchMapping("/{id}/deactivate")
    fun deactivateMine(@PathVariable id: Long): DataSourceDetailView {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return dataSourceService.deactivateForUser(id, currentUserId)
    }

    /**
     * 启用用户自己数据源
     * @param id 数据源ID
     * @return 启用成功的数据源详情视图
     */
    @PatchMapping("/{id}/activate")
    fun activateMine(@PathVariable id: Long): DataSourceDetailView {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return dataSourceService.activateForUser(id, currentUserId)
    }
}
