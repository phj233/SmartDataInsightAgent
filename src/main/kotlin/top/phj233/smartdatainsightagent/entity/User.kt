package top.phj233.smartdatainsightagent.entity

import org.babyfish.jimmer.sql.*

/**
 * 用户实体
 * @author phj233
 * @since 2026/2/11 20:00
 * @version
 */
@Entity
interface User: BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long


    @ManyToMany
    val roles: List<Role>

    val username: String

    val password: String

    @Key
    val email: String

    val enabled: Boolean

}
