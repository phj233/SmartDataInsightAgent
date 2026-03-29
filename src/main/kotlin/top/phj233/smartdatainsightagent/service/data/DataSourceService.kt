package top.phj233.smartdatainsightagent.service.data

import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.DataSource
import top.phj233.smartdatainsightagent.entity.copy
import top.phj233.smartdatainsightagent.entity.dto.DataSourceCreateInput
import top.phj233.smartdatainsightagent.entity.dto.DataSourceDetailView
import top.phj233.smartdatainsightagent.entity.dto.DataSourceSummaryView
import top.phj233.smartdatainsightagent.entity.dto.DataSourceUpdateInput
import top.phj233.smartdatainsightagent.exception.DataSourceException
import top.phj233.smartdatainsightagent.repository.DataSourceRepository

/**
 * 数据源服务，负责管理用户的数据源连接信息。
 * @author phj233
 * @since 2026/1/28 15:04
 * @version
 */
@Service
class DataSourceService(
    private val dataSourceRepository: DataSourceRepository,
    private val schemaIntrospectionService: DataSourceSchemaIntrospectionService
) {
    private val logger = LoggerFactory.getLogger(DataSourceService::class.java)

    /**
     * 为当前登录用户创建数据源。
     */
    fun createForUser(userId: Long, input: DataSourceCreateInput): DataSourceDetailView {
        logger.info("[数据源服务] 创建数据源，userId={}, name={}, type={}", userId, input.name, input.type)
        validateCreateInput(input)
        val normalizedName = input.name.trim()
        val normalizedSchemaInfo = normalizeSchemaInfo(input.schemaInfo) ?: emptyList()

        if (dataSourceRepository.existsByUserIdAndName(userId, normalizedName)) {
            throw DataSourceException.dataSourceNameAlreadyExists("数据源名称已存在: $normalizedName")
        }

        val created = dataSourceRepository.save(
            input.toEntity {
                user {
                    id = userId
                }
                name = normalizedName
                schemaInfo = normalizedSchemaInfo
                active = true
            },
            SaveMode.INSERT_ONLY
        )

        val saved = backfillSchemaIfAbsent(created)
        return DataSourceDetailView(saved)
    }

    /**
     * 查询当前用户的数据源摘要列表。
     */
    fun listForUser(userId: Long): List<DataSourceSummaryView> {
        logger.info("[数据源服务] 列出数据源，userId={}", userId)
        return dataSourceRepository.findSummaryViewsByUserId(userId)
    }

    /**
     * 查询当前用户的数据源详情。
     */
    fun getDetailForUser(id: Long, userId: Long): DataSourceDetailView {
        logger.info("[数据源服务] 查询数据源详情，userId={}, dataSourceId={}", userId, id)
        return dataSourceRepository.findDetailViewByIdAndUserId(id, userId)
            ?: throw DataSourceException.dataSourceNotFound("数据源不存在或无访问权限: $id")
    }

    /**
     * 更新当前用户自己的数据源配置。
     */
    fun updateForUser(
        id: Long,
        userId: Long,
        input: DataSourceUpdateInput,
        refreshSchemaOnly: Boolean = false
    ): DataSourceDetailView {
        logger.info(
            "[数据源服务] 更新数据源，userId={}, dataSourceId={}, name={}, refreshSchemaOnly={}",
            userId,
            id,
            input.name,
            refreshSchemaOnly
        )
        validateUpdateInput(input)
        val existing = findOwnedDataSource(id, userId)
        val normalizedName = input.name.trim()

        if (dataSourceRepository.existsByUserIdAndNameAndIdNot(userId, normalizedName, id)) {
            throw DataSourceException.dataSourceNameAlreadyExists("数据源名称已存在: $normalizedName")
        }

        val updated = existing.copy {
            name = normalizedName
            type = input.type
            connectionConfig = input.connectionConfig
            // 更新配置时忽略 schemaInfo，避免前端误传导致结构被覆盖。
            schemaInfo = existing.schemaInfo
        }

        val saved = dataSourceRepository.save(updated, SaveMode.UPDATE_ONLY)
        val finalResult = if (refreshSchemaOnly) {
            refreshSchema(saved)
        } else {
            backfillSchemaIfAbsent(saved)
        }
        return DataSourceDetailView(finalResult)
    }

    /**
     * 停用当前用户自己的数据源（幂等）。
     */
    fun deactivateForUser(id: Long, userId: Long): DataSourceDetailView {
        logger.info("[数据源服务] 停用数据源，userId={}, dataSourceId={}", userId, id)
        val existing = findOwnedDataSource(id, userId)
        if (!existing.active) {
            return DataSourceDetailView(existing)
        }
        return DataSourceDetailView(dataSourceRepository.save(existing.copy {
                active = false
        }, SaveMode.UPDATE_ONLY))
    }

    /**
     * 启用当前用户自己的数据源（幂等）。
     */
    fun activateForUser(id: Long, userId: Long): DataSourceDetailView {
        logger.info("[数据源服务] 启用数据源，userId={}, dataSourceId={}", userId, id)
        val existing = findOwnedDataSource(id, userId)
        if (existing.active) {
            return DataSourceDetailView(existing)
        }
        return DataSourceDetailView(
            dataSourceRepository.save(
                existing.copy {
                    active = true
                },
                SaveMode.UPDATE_ONLY
            )
        )
    }

    /**
     * 根据数据源 ID 查询数据源实体。
     */
    fun getDataSource(id: Long): DataSource {
        return dataSourceRepository.findNullable(id)
            ?: throw DataSourceException.dataSourceNotFound("DataSource not found: $id")
    }

    /**
     * 根据数据源 ID 查询数据源实体，并确保它处于激活状态。
     */
    fun getActiveDataSource(id: Long): DataSource {
        val dataSource = getDataSource(id)
        require(dataSource.active) { "DataSource is inactive: $id" }
        return dataSource
    }

    /**
     * 根据数据源 ID 和用户 ID 查询数据源实体，并确保它属于该用户且处于激活状态。
     */
    fun getAccessibleActiveDataSource(id: Long, userId: Long): DataSource {
        val dataSource = dataSourceRepository.findByIdAndUserId(id, userId)
            ?: throw DataSourceException.dataSourceAccessDenied("DataSource access denied: $id")
        require(dataSource.active) { "DataSource is inactive: $id" }
        return dataSource
    }

    // 根据用户权限返回可用于动态连接的配置。
    fun getConnectionDetails(dataSourceId: Long, userId: Long? = null): Map<String, String> {
        logger.debug("[数据源服务] 获取连接详情，dataSourceId={}, userId={}", dataSourceId, userId)
        val dataSource = userId?.let { getAccessibleActiveDataSource(dataSourceId, it) }
            ?: getActiveDataSource(dataSourceId)
        return ExternalDataSourceSupport.flattenConnectionConfig(dataSource.connectionConfig)
    }

    private fun validateCreateInput(input: DataSourceCreateInput) {
        if (input.name.isBlank()) {
            throw DataSourceException.invalidConnectionConfig("数据源名称不能为空")
        }
        val connectionDetails = ExternalDataSourceSupport.flattenConnectionConfig(input.connectionConfig)
        if (connectionDetails.isEmpty()) {
            throw DataSourceException.invalidConnectionConfig("连接配置不能为空")
        }
    }

    private fun validateUpdateInput(input: DataSourceUpdateInput) {
        if (input.name.isBlank()) {
            throw DataSourceException.invalidConnectionConfig("数据源名称不能为空")
        }
        val connectionDetails = ExternalDataSourceSupport.flattenConnectionConfig(input.connectionConfig)
        if (connectionDetails.isEmpty()) {
            throw DataSourceException.invalidConnectionConfig("连接配置不能为空")
        }
    }

    private fun findOwnedDataSource(id: Long, userId: Long): DataSource {
        return dataSourceRepository.findByIdAndUserId(id, userId)
            ?: throw DataSourceException.dataSourceAccessDenied("数据源无访问权限: $id")
    }

    private fun normalizeSchemaInfo(schemaInfo: List<Map<String, String>>?): List<Map<String, String>>? {
        if (schemaInfo == null) {
            return null
        }
        return schemaInfo
            .map { row ->
                row.mapNotNull { (key, value) ->
                    val normalizedKey = key.trim()
                    val normalizedValue = value.trim()
                    if (normalizedKey.isEmpty() || normalizedValue.isEmpty()) {
                        null
                    } else {
                        normalizedKey to normalizedValue
                    }
                }.toMap()
            }
            .filter { it.isNotEmpty() }
    }

    private fun backfillSchemaIfAbsent(dataSource: DataSource): DataSource {
        if (dataSource.schemaInfo.isNotEmpty()) {
            return dataSource
        }

        return refreshSchema(dataSource)
    }

    private fun refreshSchema(dataSource: DataSource): DataSource {
        logger.info("[数据源服务] 刷新Schema，dataSourceId={}, userId={}", dataSource.id, dataSource.userId)

        val connectionDetails = ExternalDataSourceSupport.flattenConnectionConfig(dataSource.connectionConfig)
        val introspected = schemaIntrospectionService.introspect(dataSource.type, connectionDetails)
        if (introspected.isEmpty()) {
            logger.warn("[数据源服务] Schema 探测结果为空，dataSourceId={}, userId={}", dataSource.id, dataSource.userId)
            return dataSource
        }

        val updated = dataSource.copy {
            schemaInfo = introspected
        }
        return dataSourceRepository.save(updated, SaveMode.UPDATE_ONLY)
    }
}
