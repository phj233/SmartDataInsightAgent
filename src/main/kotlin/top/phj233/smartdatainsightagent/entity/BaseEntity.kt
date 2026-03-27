package top.phj233.smartdatainsightagent.entity

import org.babyfish.jimmer.sql.DissociateAction
import org.babyfish.jimmer.sql.ManyToOne
import org.babyfish.jimmer.sql.MappedSuperclass
import org.babyfish.jimmer.sql.OnDissociate

/**
 * 基础实体接口，包含所有实体共有的字段和关系。
 * - createdTimeStamp: 记录实体创建的时间戳，单位为毫秒
 * - createdBy: 记录实体创建者的用户信息，使用@ManyToOne关系关联到User实体。当关联的User被删除时，使用@OnDissociate注解指定DissociateAction.SET_NULL，即将createdBy字段设置为null。
 * - modifiedTimeStamp: 记录实体最后修改的时间戳，单位为毫
 * - modifiedBy: 记录实体最后修改者的用户信息，使用@ManyToOne关系关联到User实体。当关联的User被删除时，使用@OnDissociate注解指定DissociateAction.SET_NULL，即将modifiedBy字段设置为null。
 * @author phj233
 * @since 2026/2/11 20:04
 * @version
 */
@MappedSuperclass
interface BaseEntity {

    val createdTimeStamp: Long

    @ManyToOne
    @OnDissociate(DissociateAction.SET_NULL)
    val createdBy: User?

    val modifiedTimeStamp: Long

    @ManyToOne
    @OnDissociate(DissociateAction.SET_NULL)
    val modifiedBy: User?
}
