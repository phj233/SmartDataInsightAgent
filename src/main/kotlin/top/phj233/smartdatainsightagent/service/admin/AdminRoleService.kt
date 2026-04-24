package top.phj233.smartdatainsightagent.service.admin

import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.Role
import top.phj233.smartdatainsightagent.entity.copy
import top.phj233.smartdatainsightagent.entity.dto.RoleCreate
import top.phj233.smartdatainsightagent.entity.dto.RoleUpdate
import top.phj233.smartdatainsightagent.exception.RoleException
import top.phj233.smartdatainsightagent.repository.RoleRepository

/**
 * 管理员角色服务，负责角色的分页查询、详情查看、创建、更新与删除。
 *
 * @author phj233
 * @since 2026/4/24
 */
@Service
class AdminRoleService(
    private val roleRepository: RoleRepository
) {
    private val logger = LoggerFactory.getLogger(AdminRoleService::class.java)

    /**
     * 分页查询角色列表。
     *
     * @param pageable 分页参数
     * @return 分页角色列表
     */
    fun list(pageable: Pageable): Page<Role> {
        logger.info("[管理员角色服务] 分页查询角色，page={}, size={}", pageable.pageNumber, pageable.pageSize)
        return roleRepository.findAll(pageable)
    }

    /**
     * 查询指定角色详情。
     *
     * @param id 角色 ID
     * @return 角色实体
     */
    fun detail(id: Long): Role {
        logger.info("[管理员角色服务] 查询角色详情，roleId={}", id)
        return roleRepository.findNullable(id)
            ?: throw RoleException.roleNotFound("角色不存在: $id")
    }

    /**
     * 创建角色。
     *
     * @param roleCreate 角色创建参数
     * @return 创建后的角色实体
     */
    fun create(roleCreate: RoleCreate): Role {
        val normalizedName = normalizeName(roleCreate.name)
        logger.info("[管理员角色服务] 创建角色，name={}", normalizedName)

        roleRepository.findByName(normalizedName)?.let {
            throw RoleException.roleAlreadyExists("角色名已存在: $normalizedName")
        }

        return roleRepository.save(roleCreate.copy(name = normalizedName), SaveMode.INSERT_ONLY)
    }

    /**
     * 更新角色。
     *
     * @param id 角色 ID
     * @param roleUpdate 角色更新参数
     * @return 更新后的角色实体
     */
    fun update(id: Long, roleUpdate: RoleUpdate): Role {
        val normalizedName = normalizeName(roleUpdate.name)
        logger.info("[管理员角色服务] 更新角色，roleId={}, name={}", id, normalizedName)

        val existing = roleRepository.findNullable(id)
            ?: throw RoleException.roleNotFound("角色不存在: $id")

        roleRepository.findByName(normalizedName)?.let { matched ->
            if (matched.id != id) {
                logger.warn("[管理员角色服务] 角色名已存在，name={}, roleId={}", normalizedName, matched.id)
                throw RoleException.roleAlreadyExists("角色名已存在: $normalizedName")
            }
        }

        return roleRepository.save(
            existing.copy {
                this.name = normalizedName
            },
            SaveMode.UPDATE_ONLY
        )
    }

    /**
     * 删除角色。
     *
     * @param id 角色 ID
     */
    fun delete(id: Long) {
        logger.info("[管理员角色服务] 删除角色，roleId={}", id)
        if (!roleRepository.existsById(id)) {
            throw RoleException.roleNotFound("角色不存在: $id")
        }
        roleRepository.deleteById(id)
    }

    /**
     * 规范化角色名并校验非空。
     *
     * @param name 原始角色名
     * @return 去除首尾空白后的角色名
     */
    private fun normalizeName(name: String): String {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            throw RoleException.invalidRoleName("角色名不能为空")
        }
        return normalizedName
    }
}
