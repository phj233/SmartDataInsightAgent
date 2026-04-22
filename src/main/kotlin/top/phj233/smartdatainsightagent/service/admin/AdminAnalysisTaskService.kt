package top.phj233.smartdatainsightagent.service.admin

import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.dto.AdminFailedAnalysisTaskUpdateInput
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskDetailView
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskSummaryView
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.exception.AnalysisTaskException
import top.phj233.smartdatainsightagent.repository.AnalysisTaskRepository

/**
 * 管理员分析任务服务
 */
@Service
class AdminAnalysisTaskService(
    private val analysisTaskRepository: AnalysisTaskRepository
) {
    private val logger = LoggerFactory.getLogger(AdminAnalysisTaskService::class.java)

    fun list(pageable: Pageable): Page<AnalysisTaskSummaryView> {
        logger.info("[管理员分析任务服务] 分页查询任务, page={}, size={}", pageable.pageNumber, pageable.pageSize)
        return analysisTaskRepository.findAll(pageable).map(::AnalysisTaskSummaryView)
    }

    fun detail(id: Long): AnalysisTaskDetailView {
        logger.info("[管理员分析任务服务] 查询任务详情, taskId={}", id)
        val entity = analysisTaskRepository.findNullable(id)
            ?: throw AnalysisTaskException.taskNotFound("分析任务不存在: $id")
        return AnalysisTaskDetailView(entity)
    }

    fun updateFailedTask(id: Long, input: AdminFailedAnalysisTaskUpdateInput): AnalysisTaskDetailView {
        logger.info("[管理员分析任务服务] 更新失败任务, taskId={}", id)
        val existing = analysisTaskRepository.findNullable(id)
            ?: throw AnalysisTaskException.taskNotFound("分析任务不存在: $id")

        ensureFailed(existing.status, id)
        val updated = analysisTaskRepository.save(input, SaveMode.UPDATE_ONLY)
        return AnalysisTaskDetailView(updated)
    }

    fun deleteFailedTask(id: Long) {
        logger.info("[管理员分析任务服务] 删除失败任务, taskId={}", id)
        val existing = analysisTaskRepository.findNullable(id)
            ?: throw AnalysisTaskException.taskNotFound("分析任务不存在: $id")

        ensureFailed(existing.status, id)
        analysisTaskRepository.deleteById(id)
    }

    private fun ensureFailed(status: AnalysisStatus, taskId: Long) {
        if (status != AnalysisStatus.FAILED) {
            logger.warn("[管理员分析任务服务] 非FAILED任务不允许修改, taskId={}, status={}", taskId, status)
            throw AnalysisTaskException.invalidTaskRequest("仅FAILED状态任务允许修改: $taskId")
        }
    }
}
