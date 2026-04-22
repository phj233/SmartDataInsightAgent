package top.phj233.smartdatainsightagent.service.admin

import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.copy
import top.phj233.smartdatainsightagent.entity.dto.*
import top.phj233.smartdatainsightagent.exception.DataSourceException
import top.phj233.smartdatainsightagent.exception.UserException
import top.phj233.smartdatainsightagent.repository.DataSourceRepository
import top.phj233.smartdatainsightagent.repository.UserRepository
import top.phj233.smartdatainsightagent.service.data.DataSourceService

@Service
class AdminDataSourceService(
    private val dataSourceRepository: DataSourceRepository,
    private val userRepository: UserRepository,
    private val dataSourceService: DataSourceService
) {
    private val logger = LoggerFactory.getLogger(AdminDataSourceService::class.java)

    fun list(pageable: Pageable): Page<DataSourceDetailView> {
        logger.info("[管理员数据源服务] 分页查询数据源, page={}, size={}", pageable.pageNumber, pageable.pageSize)
        return dataSourceRepository.findAll(pageable).map(::DataSourceDetailView)
    }

    fun detail(id: Long): DataSourceDetailView {
        logger.info("[管理员数据源服务] 查询数据源详情, dataSourceId={}", id)
        val entity = dataSourceRepository.findNullable(id)
            ?: throw DataSourceException.dataSourceNotFound("数据源不存在: $id")
        return DataSourceDetailView(entity)
    }

    fun create(input: AdminDataSourceCreateInput): DataSourceDetailView {
        logger.info("[管理员数据源服务] 创建数据源, userId={}, name={}", input.userId, input.name)
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

    fun update(id: Long, input: AdminDataSourceUpdateInput, refreshSchemaOnly: Boolean): DataSourceDetailView {
        logger.info(
            "[管理员数据源服务] 更新数据源, dataSourceId={}, targetUserId={}, refreshSchemaOnly={}",
            id,
            input.userId,
            refreshSchemaOnly
        )
        ensureUserExists(input.userId)

        val existing = dataSourceRepository.findNullable(id)
            ?: throw DataSourceException.dataSourceNotFound("数据源不存在: $id")

        val updated = dataSourceService.updateForUser(
            id = id,
            userId = existing.userId,
            input = DataSourceUpdateInput(
                name = input.name,
                type = input.type,
                connectionConfig = input.connectionConfig,
                schemaInfo = input.schemaInfo
            ),
            refreshSchemaOnly = refreshSchemaOnly
        )

        if (existing.userId == input.userId) {
            return updated
        }

        logger.info("[管理员数据源服务] 变更数据源归属用户, dataSourceId={}, fromUserId={}, toUserId={}", id, existing.userId, input.userId)
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

    fun delete(id: Long) {
        logger.info("[管理员数据源服务] 删除数据源, dataSourceId={}", id)
        if (!dataSourceRepository.existsById(id)) {
            throw DataSourceException.dataSourceNotFound("数据源不存在: $id")
        }
        dataSourceRepository.deleteById(id)
    }

    private fun ensureUserExists(userId: Long) {
        if (!userRepository.existsById(userId)) {
            logger.warn("[管理员数据源服务] 用户不存在, userId={}", userId)
            throw UserException.userNotFound("用户不存在: $userId")
        }
    }
}
