package top.phj233.smartdatainsightagent.repository

import org.babyfish.jimmer.spring.repository.KRepository
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ne
import org.springframework.stereotype.Repository
import top.phj233.smartdatainsightagent.entity.*
import top.phj233.smartdatainsightagent.entity.dto.DataSourceDetailView
import top.phj233.smartdatainsightagent.entity.dto.DataSourceSummaryView

@Repository
interface DataSourceRepository : KRepository<DataSource, Long> {

    fun findByIdAndUserId(id: Long, userId: Long): DataSource? = sql.createQuery(DataSource::class) {
        where(table.id.eq(id))
        where(table.userId.eq(userId))
        select(table)
    }.execute().firstOrNull()

    fun existsByUserIdAndName(userId: Long, name: String): Boolean = sql.createQuery(DataSource::class) {
        where(table.userId.eq(userId))
        where(table.name.eq(name))
        select(table.id)
    }.execute().isNotEmpty()

    fun existsByUserIdAndNameAndIdNot(userId: Long, name: String, id: Long): Boolean =
        sql.createQuery(DataSource::class) {
            where(table.userId.eq(userId))
            where(table.name.eq(name))
            where(table.id.ne(id))
            select(table.id)
        }.execute().isNotEmpty()

    fun findSummaryViewsByUserId(userId: Long): List<DataSourceSummaryView> = sql.createQuery(DataSource::class) {
        where(table.userId.eq(userId))
        orderBy(table.modifiedTimeStamp.desc())
        select(
            table.fetchBy {
                name()
                type()
                active()
                createdTimeStamp()
                modifiedTimeStamp()
            }
        )
    }.execute().map(::DataSourceSummaryView)

    fun findDetailViewByIdAndUserId(id: Long, userId: Long): DataSourceDetailView? =
        sql.createQuery(DataSource::class) {
            where(table.id.eq(id))
            where(table.userId.eq(userId))
            select(
                table.fetchBy {
                    userId()
                    name()
                    type()
                    connectionConfig()
                    schemaInfo()
                    active()
                    createdTimeStamp()
                    modifiedTimeStamp()
                }
            )
        }.execute().firstOrNull()?.let(::DataSourceDetailView)
}

