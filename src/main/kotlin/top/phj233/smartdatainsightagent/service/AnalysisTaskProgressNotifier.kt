package top.phj233.smartdatainsightagent.service

import top.phj233.smartdatainsightagent.model.AnalysisTaskProgressEvent

/**
 * 任务进度通知抽象，便于在持久化层解耦通知通道。
 */
interface AnalysisTaskProgressNotifier {
    fun notify(event: AnalysisTaskProgressEvent)
}

