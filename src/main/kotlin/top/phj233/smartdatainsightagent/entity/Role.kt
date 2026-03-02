package top.phj233.smartdatainsightagent.entity

import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.ManyToMany

/**
 * @author phj233
 * @since 2026/2/20 12:22
 * @version
 */
@Entity
interface Role {
    @Id
    val id: Long
    val name: String
    @ManyToMany(mappedBy = "roles")
    val users: List<User>
}
