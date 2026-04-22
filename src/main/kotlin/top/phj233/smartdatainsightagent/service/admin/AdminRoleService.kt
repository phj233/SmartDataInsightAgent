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

@Service
class AdminRoleService(
    private val roleRepository: RoleRepository
) {
    private val logger = LoggerFactory.getLogger(AdminRoleService::class.java)

    fun list(pageable: Pageable): Page<Role> {
        logger.info("[管理员角色服务] 分页查询角色, page={}, size={}", pageable.pageNumber, pageable.pageSize)
        return roleRepository.findAll(pageable)
    }

    fun detail(id: Long): Role {
        logger.info("[管理员角色服务] 查询角色详情, roleId={}", id)
        return roleRepository.findNullable(id)
            ?: throw RoleException.roleNotFound("角色不存在: $id")
    }

    fun create(roleCreate: RoleCreate): Role {
        logger.info("[管理员角色服务] 创建角色, name={}", roleCreate.name)
        val normalizedName = roleCreate.name.trim()
        roleRepository.findByName(normalizedName)?.let {
            throw RoleException.roleAlreadyExists("角色名已存在: $normalizedName")
        }
        return roleRepository.save(roleCreate.copy(name = normalizedName), SaveMode.INSERT_ONLY)
    }

    fun update(id: Long, roleUpdate: RoleUpdate): Role {
        logger.info("[管理员角色服务] 更新角色, roleId={}, name={}", id, roleUpdate.name)
        val existing = roleRepository.findNullable(id)
            ?: throw RoleException.roleNotFound("角色不存在: $id")

        val normalizedName = roleUpdate.name.trim()
        roleRepository.findByName(normalizedName)?.let { matched ->
            if (matched.id != id) {
                logger.warn("[管理员角色服务] 角色名已存在, name={}, roleId={}", normalizedName, matched.id)
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

    fun delete(id: Long) {
        logger.info("[管理员角色服务] 删除角色, roleId={}", id)
        if (!roleRepository.existsById(id)) {
            throw RoleException.roleNotFound("角色不存在: $id")
        }
        roleRepository.deleteById(id)
    }
}
