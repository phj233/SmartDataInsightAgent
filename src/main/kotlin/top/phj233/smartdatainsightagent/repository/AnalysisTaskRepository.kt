package top.phj233.smartdatainsightagent.repository

import org.babyfish.jimmer.spring.repository.KRepository
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.springframework.stereotype.Repository
import top.phj233.smartdatainsightagent.entity.*
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskDetailView
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskSummaryView
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus

@Repository
interface AnalysisTaskRepository : KRepository<AnalysisTask, Long> {

	fun findDetailViewByIdAndUserId(id: Long, userId: Long): AnalysisTaskDetailView? =
		sql.createQuery(AnalysisTask::class) {
			where(table.id.eq(id))
			where(table.userId.eq(userId))
			select(
				table.fetchBy {
					userId()
					originalQuery()
					generatedSql()
					parameters()
					status()
					result()
					executionTime()
					errorMessage()
				}
			)
		}.execute().firstOrNull()?.let(::AnalysisTaskDetailView)

	fun findSummaryViewsByUserId(userId: Long): List<AnalysisTaskSummaryView> = sql.createQuery(AnalysisTask::class) {
		where(table.userId.eq(userId))
		orderBy(table.createdTimeStamp.desc())
		select(
			table.fetchBy {
				userId()
				originalQuery()
				status()
				executionTime()
				errorMessage()
			}
		)
	}.execute().map(::AnalysisTaskSummaryView)

	fun findSummaryViewsByUserIdAndStatus(userId: Long, status: AnalysisStatus): List<AnalysisTaskSummaryView> =
		sql.createQuery(AnalysisTask::class) {
			where(table.userId.eq(userId))
			where(table.status.eq(status))
			orderBy(table.createdTimeStamp.desc())
			select(
				table.fetchBy {
					userId()
					originalQuery()
					status()
					executionTime()
					errorMessage()
				}
			)
		}.execute().map(::AnalysisTaskSummaryView)
}

