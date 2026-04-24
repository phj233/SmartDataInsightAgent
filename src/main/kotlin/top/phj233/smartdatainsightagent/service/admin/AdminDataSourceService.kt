package top.phj233.smartdatainsightagent.service.admin

import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.copy
import top.phj233.smartdatainsightagent.entity.dto.AdminDataSourceCreateInput
import top.phj233.smartdatainsightagent.entity.dto.AdminDataSourceUpdateInput
import top.phj233.smartdatainsightagent.entity.dto.DataSourceCreateInput
import top.phj233.smartdatainsightagent.entity.dto.DataSourceDetailView
import top.phj233.smartdatainsightagent.entity.dto.DataSourceUpdateInput
import top.phj233.smartdatainsightagent.exception.DataSourceException
import top.phj233.smartdatainsightagent.exception.UserException
import top.phj233.smartdatainsightagent.repository.DataSourceRepository
import top.phj233.smartdatainsightagent.repository.UserRepository
import top.phj233.smartdatainsightagent.service.data.DataSourceService

/**
 * 管理员数据源服务，负责后台数据源的分页查询、创建、更新、归属变更与删除。
 *
 * @author phj233
 * @since 2026/4/24
 */
@Service
class AdminDataSourceService(
    private val dataSourceRepository: DataSourceRepository,
    private val userRepository: UserRepository,
    private val dataSourceService: DataSourceService
) {
    private val logger = LoggerFactory.getLogger(AdminDataSourceService::class.java)

    /**
     * 分页查询数据源列表。
     *
     * @param pageable 分页参数
     * @return 分页数据源详情列表
     */
    fun list(pageable: Pageable): Page<DataSourceDetailView> {
        logger.info("[管理员数据源服务] 分页查询数据源，page={}, size={}", pageable.pageNumber, pageable.pageSize)
        return dataSourceRepository.findAll(pageable).map(::DataSourceDetailView)
    }

    /**
     * 查询指定数据源详情。
     *
     * @param id 数据源 ID
     * @return 数据源详情视图
     */
    fun detail(id: Long): DataSourceDetailView {
        logger.info("[管理员数据源服务] 查询数据源详情，dataSourceId={}", id)
        val entity = dataSourceRepository.findNullable(id)
            ?: throw DataSourceException.dataSourceNotFound("数据源不存在: $id")
        return DataSourceDetailView(entity)
    }

    /**
     * 为指定用户创建数据源。
     *
     * @param input 管理员创建输入
     * @return 创建后的数据源详情
     */
    fun create(input: AdminDataSourceCreateInput): DataSourceDetailView {
        logger.info("[管理员数据源服务] 创建数据源，userId={}, name={}", input.userId, input.name)
        ensureUserExists(input.userId)
        return dataSourceService.createForUser(
            input.userId,
            DataSourceCreateInput(
                name = input.name,
                type = input.type,
                connectionConfig = input.connectionConfig,
                schemaInfo = input.schemaInfo
            )
        )
    }

    /**
     * 更新指定数据源，并在需要时变更归属用户或刷新 Schema。
     *
     * @param id 数据源 ID
     * @param input 管理员更新输入
     * @param refreshSchemaOnly 是否仅刷新 Schema
     * @return 更新后的数据源详情
     */
    fun update(id: Long, input: AdminDataSourceUpdateInput, refreshSchemaOnly: Boolean): DataSourceDetailView {
        logger.info(
            "[管理员数据源服务] 更新数据源，dataSourceId={}, targetUserId={}, refreshSchemaOnly={}",
            id,
            input.userId,
            refreshSchemaOnly
        )
        ensureUserExists(input.userId)

        val existing = dataSourceRepository.findNullable(id)
            ?: throw DataSourceException.dataSourceNotFound("数据源不存在: $id")
        val normalizedName = input.name.trim()

        if (existing.userId != input.userId && dataSourceRepository.existsByUserIdAndName(input.userId, normalizedName)) {
            throw DataSourceException.dataSourceNameAlreadyExists("数据源名称已存在: $normalizedName")
        }

        val updated = dataSourceService.updateForUser(
            id = id,
            userId = existing.userId,
            input = DataSourceUpdateInput(
                name = normalizedName,
                type = input.type,
                connectionConfig = input.connectionConfig,
                schemaInfo = input.schemaInfo
            ),
            refreshSchemaOnly = refreshSchemaOnly
        )

        if (existing.userId == input.userId) {
            return updated
        }

        logger.info(
            "[管理员数据源服务] 变更数据源归属，dataSourceId={}, fromUserId={}, toUserId={}",
            id,
            existing.userId,
            input.userId
        )
        val reassigned = dataSourceRepository.findNullable(id)
            ?: throw DataSourceException.dataSourceNotFound("数据源不存在: $id")
        val saved = dataSourceRepository.save(
            reassigned.copy {
                user {
                    this.id = input.userId
                }
            },
            SaveMode.UPDATE_ONLY
        )
        return DataSourceDetailView(saved)
    }

    /**
     * 删除指定数据源。
     *
     * @param id 数据源 ID
     */
    fun delete(id: Long) {
        logger.info("[管理员数据源服务] 删除数据源，dataSourceId={}", id)
        if (!dataSourceRepository.existsById(id)) {
            throw DataSourceException.dataSourceNotFound("数据源不存在: $id")
        }
        dataSourceRepository.deleteById(id)
    }

    /**
     * 校验目标用户是否存在。
     *
     * @param userId 用户 ID
     */
    private fun ensureUserExists(userId: Long) {
        if (!userRepository.existsById(userId)) {
            logger.warn("[管理员数据源服务] 用户不存在，userId={}", userId)
            throw UserException.userNotFound("用户不存在: $userId")
        }
    }
}
