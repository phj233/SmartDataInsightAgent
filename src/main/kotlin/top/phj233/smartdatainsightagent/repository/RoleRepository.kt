package top.phj233.smartdatainsightagent.repository

import org.babyfish.jimmer.spring.repository.KRepository
import org.springframework.stereotype.Repository
import top.phj233.smartdatainsightagent.entity.Role

/**
 * @author phj233
 * @since 2026/4/22 17:45
 * @version
 */
@Repository
interface RoleRepository : KRepository<Role, Long> {
    fun findByName(name: String): Role?
}
