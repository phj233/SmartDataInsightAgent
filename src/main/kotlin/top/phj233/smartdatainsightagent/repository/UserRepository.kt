package top.phj233.smartdatainsightagent.repository

import org.babyfish.jimmer.spring.repository.KRepository
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Repository
import top.phj233.smartdatainsightagent.entity.User
import top.phj233.smartdatainsightagent.entity.fetchBy
import top.phj233.smartdatainsightagent.entity.id

/**
 * @author phj233
 * @since 2026/2/11 20:08
 * @version
 */
@Repository
interface UserRepository : KRepository<User, Long> {
    fun findUserRolesById(id: Long): List<User> = sql.createQuery(User::class){
        where(table.id.eq(id))
        select(table.fetchBy {
            roles {
                name()
            }
        })
    }.execute()

    fun findUserByEmail(email: String): User?
}
