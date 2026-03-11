package top.phj233.smartdatainsightagent.exception

import org.babyfish.jimmer.error.ErrorFamily

/**
 * 分析任务相关错误枚举类，表示在分析任务执行过程中可能遇到的错误类型。
 * - TASK_NOT_FOUND: 任务未找到，表示请求的分析任务不存在。
 * - INVALID_TASK_REQUEST: 无效的任务请求，表示请求的分析任务参数不合法或不完整。
 * - TASK_ALREADY_FINISHED: 任务已完成，表示请求的分析任务已经完成，无法再次执行。
 *
 * @author phj233
 * @since 2026/3/11 18:30
 * @version
 */
@ErrorFamily
enum class AnalysisTaskError {
    TASK_NOT_FOUND,
    INVALID_TASK_REQUEST,
    TASK_ALREADY_FINISHED
}

