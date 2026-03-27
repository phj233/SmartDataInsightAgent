package top.phj233.smartdatainsightagent.service.data

import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.DataSource
import top.phj233.smartdatainsightagent.entity.DataSourceDraft
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
    private val dataSourceRepository: DataSourceRepository
) {
    /**
     * 为当前登录用户创建数据源。
     */
    fun createForUser(userId: Long, input: DataSourceCreateInput): DataSourceDetailView {
        validateCreateInput(input)
        val normalizedName = input.name.trim()

        if (dataSourceRepository.existsByUserIdAndName(userId, normalizedName)) {
            throw DataSourceException.dataSourceNameAlreadyExists("数据源名称已存在: $normalizedName")
        }

        val saved = dataSourceRepository.save(
            input.toEntity {
                user {
                    id = userId
                }
                name = normalizedName
                active = true
            },
            SaveMode.INSERT_ONLY
        )
        return DataSourceDetailView(saved)
    }

    /**
     * 查询当前用户的数据源摘要列表。
     */
    fun listForUser(userId: Long): List<DataSourceSummaryView> {
        return dataSourceRepository.findSummaryViewsByUserId(userId)
    }

    /**
     * 查询当前用户的数据源详情。
     */
    fun getDetailForUser(id: Long, userId: Long): DataSourceDetailView {
        return dataSourceRepository.findDetailViewByIdAndUserId(id, userId)
            ?: throw DataSourceException.dataSourceNotFound("数据源不存在或无访问权限: $id")
    }

    /**
     * 更新当前用户自己的数据源配置。
     */
    fun updateForUser(id: Long, userId: Long, input: DataSourceUpdateInput): DataSourceDetailView {
        validateUpdateInput(input)
        val existing = findOwnedDataSource(id, userId)
        val normalizedName = input.name.trim()

        if (dataSourceRepository.existsByUserIdAndNameAndIdNot(userId, normalizedName, id)) {
            throw DataSourceException.dataSourceNameAlreadyExists("数据源名称已存在: $normalizedName")
        }

        val updated = DataSourceDraft.`$`.produce(existing) {
            name = normalizedName
            type = input.type
            connectionConfig = input.connectionConfig
            schemaInfo = input.schemaInfo
        }
        return DataSourceDetailView(dataSourceRepository.save(updated, SaveMode.UPDATE_ONLY))
    }

    /**
     * 停用当前用户自己的数据源（幂等）。
     */
    fun deactivateForUser(id: Long, userId: Long): DataSourceDetailView {
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
}
