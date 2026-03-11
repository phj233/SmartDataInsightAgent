package top.phj233.smartdatainsightagent.interceptor

import cn.dev33.satoken.stp.StpUtil
import org.babyfish.jimmer.kt.isLoaded
import org.babyfish.jimmer.sql.DraftInterceptor
import org.springframework.stereotype.Component
import top.phj233.smartdatainsightagent.entity.BaseEntity
import top.phj233.smartdatainsightagent.entity.BaseEntityDraft
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * BaseEntityDraftInterceptor
 * 用于在保存继承了 BaseEntity的实体时自动设置 createdTimeStamp、createdBy、modifiedTimeStamp 和 modifiedBy 字段。
 * @author phj233
 * @since 2026/2/11 20:07
 * @version
 */
@Component
class BaseEntityDraftInterceptor : DraftInterceptor<BaseEntity, BaseEntityDraft> {

    override fun beforeSave(draft: BaseEntityDraft, original: BaseEntity?) {
        if (!isLoaded(draft, BaseEntity::modifiedTimeStamp)) {
            // 无论是新增还是修改，都要更新 modifiedTimeStamp 一串数字时间戳
            draft.modifiedTimeStamp = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"))
        }

        if (!isLoaded(draft, BaseEntity::modifiedBy)) {
            draft.modifiedBy {
                id = StpUtil.getLoginIdAsLong()
            }
        }

        if (original === null) {
            if (!isLoaded(draft, BaseEntity::createdTimeStamp)) {
                draft.createdTimeStamp = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"))
            }

            if (!isLoaded(draft, BaseEntity::createdBy)) {
                draft.createdBy {
                    id = StpUtil.getLoginIdAsLong()
                }
            }
        }
    }
}
