package top.phj233.smartdatainsightagent.repository

import org.babyfish.jimmer.spring.repository.KRepository
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.or
import org.springframework.stereotype.Repository
import top.phj233.smartdatainsightagent.entity.*

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

    fun findUserByEmailOrUsername(email: String, username: String): User? = sql. createQuery(User::class){
        where(
            or(
                table.email.eq(email),
                table.username.eq(username)
            )
        )
        select(table)
    }.execute().firstOrNull()

    fun findMeById(id: Long): User? = sql.createQuery(User::class) {
        where(table.id.eq(id))
        select(
            table.fetchBy {
                username()
                email()
                avatar()
                enabled()
                roles {
                    name()
                }
            }
        )
    }.execute().firstOrNull()

    fun findUserByEmail(email: String): User?
}
