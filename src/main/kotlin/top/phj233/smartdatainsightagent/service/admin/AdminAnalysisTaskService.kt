package top.phj233.smartdatainsightagent.service.admin

import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import top.phj233.smartdatainsightagent.entity.copy
import top.phj233.smartdatainsightagent.entity.dto.AdminFailedAnalysisTaskUpdateInput
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskDetailView
import top.phj233.smartdatainsightagent.entity.dto.AnalysisTaskSummaryView
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.exception.AnalysisTaskException
import top.phj233.smartdatainsightagent.repository.AnalysisTaskRepository

/**
 * 管理员分析任务服务，负责后台分页查询任务、查看详情以及维护失败任务。
 *
 * @author phj233
 * @since 2026/4/24
 */
@Service
class AdminAnalysisTaskService(
    private val analysisTaskRepository: AnalysisTaskRepository
) {
    private val logger = LoggerFactory.getLogger(AdminAnalysisTaskService::class.java)

    /**
     * 分页查询分析任务列表。
     *
     * @param pageable 分页参数
     * @return 分页任务摘要列表
     */
    fun list(pageable: Pageable): Page<AnalysisTaskSummaryView> {
        logger.info("[管理员分析任务服务] 分页查询任务，page={}, size={}", pageable.pageNumber, pageable.pageSize)
        return analysisTaskRepository.findAll(pageable).map(::AnalysisTaskSummaryView)
    }

    /**
     * 查询指定分析任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情视图
     */
    fun detail(id: Long): AnalysisTaskDetailView {
        logger.info("[管理员分析任务服务] 查询任务详情，taskId={}", id)
        val entity = analysisTaskRepository.findNullable(id)
            ?: throw AnalysisTaskException.taskNotFound("分析任务不存在: $id")
        return AnalysisTaskDetailView(entity)
    }

    /**
     * 更新失败状态的分析任务。
     *
     * @param id 任务 ID
     * @param input 管理员更新输入
     * @return 更新后的任务详情
     */
    fun updateFailedTask(id: Long, input: AdminFailedAnalysisTaskUpdateInput): AnalysisTaskDetailView {
        logger.info("[管理员分析任务服务] 更新失败任务，taskId={}", id)
        val existing = analysisTaskRepository.findNullable(id)
            ?: throw AnalysisTaskException.taskNotFound("分析任务不存在: $id")

        ensureFailed(existing.status, id)
        input.name?.let {
            if (it.isBlank()) {
                throw AnalysisTaskException.invalidTaskRequest("任务名称不能为空")
            }
        }
        input.originalQuery?.let {
            if (it.isBlank()) {
                throw AnalysisTaskException.invalidTaskRequest("原始查询不能为空")
            }
        }

        val updated = analysisTaskRepository.save(
            existing.copy {
                input.name?.trim()?.let { name = it }
                input.originalQuery?.trim()?.let { originalQuery = it }
                if (input.generatedSql != null) {
                    generatedSql = input.generatedSql.trim().ifBlank { null }
                }
                input.parameters?.let { parameters = it }
                input.status?.let { status = it }
                input.result?.let { result = it }
                input.executionTime?.let { executionTime = it }
                if (input.errorMessage != null) {
                    errorMessage = input.errorMessage.trim().ifBlank { null }
                }
            },
            SaveMode.UPDATE_ONLY
        )
        return AnalysisTaskDetailView(updated)
    }

    /**
     * 删除失败状态的分析任务。
     *
     * @param id 任务 ID
     */
    fun deleteFailedTask(id: Long) {
        logger.info("[管理员分析任务服务] 删除失败任务，taskId={}", id)
        val existing = analysisTaskRepository.findNullable(id)
            ?: throw AnalysisTaskException.taskNotFound("分析任务不存在: $id")

        ensureFailed(existing.status, id)
        analysisTaskRepository.deleteById(id)
    }

    /**
     * 校验任务是否处于失败状态。
     *
     * @param status 任务状态
     * @param taskId 任务 ID
     */
    private fun ensureFailed(status: AnalysisStatus, taskId: Long) {
        if (status != AnalysisStatus.FAILED) {
            logger.warn("[管理员分析任务服务] 仅失败任务允许修改，taskId={}, status={}", taskId, status)
            throw AnalysisTaskException.invalidTaskRequest("仅 FAILED 状态任务允许修改: $taskId")
        }
    }
}
