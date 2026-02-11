package top.phj233.smartdatainsightagent.entity

import org.babyfish.jimmer.sql.DissociateAction
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.MappedSuperclass
import org.babyfish.jimmer.sql.OnDissociate

/**
 * @author phj233
 * @since 2026/2/11 20:04
 * @version
 */
@MappedSuperclass
interface BaseEntity {

    val createdTimeStamp: Long

    @ManyToOne
    @OnDissociate(DissociateAction.SET_NULL)
    val createdBy: User

    val modifiedTimeStamp: Long

    @ManyToOne
    @OnDissociate(DissociateAction.SET_NULL)
    val modifiedBy: User
}
