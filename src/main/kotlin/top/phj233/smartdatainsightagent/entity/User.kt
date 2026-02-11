package top.phj233.smartdatainsightagent.entity

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.GeneratedValue
import org.babyfish.jimmer.sql.GenerationType
import org.babyfish.jimmer.sql.Id

/**
 * 用户实体
 * @author phj233
 * @since 2026/2/11 20:00
 * @version
 */
@Entity
interface User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val roleId: Long

    val username: String

    val password: String

    val email: String

    val enabled: Boolean

}
