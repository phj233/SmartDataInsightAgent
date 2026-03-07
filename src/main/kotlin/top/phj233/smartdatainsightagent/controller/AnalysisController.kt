package top.phj233.smartdatainsightagent.controller

import cn.dev33.satoken.stp.StpUtil
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.phj233.smartdatainsightagent.model.AnalysisExecuteRequest
import top.phj233.smartdatainsightagent.model.AnalysisRequest
import top.phj233.smartdatainsightagent.model.AnalysisResult
import top.phj233.smartdatainsightagent.service.agent.DataAnalysisAgent

@RestController
@Validated
@RequestMapping("/api/analysis")
class AnalysisController(
    private val dataAnalysisAgent: DataAnalysisAgent
) {

    @PostMapping("/execute")
    suspend fun execute(@Valid @RequestBody request: AnalysisExecuteRequest): AnalysisResult {
        val currentUserId = StpUtil.getLoginIdAsLong()
        return dataAnalysisAgent.analyzeData(
            AnalysisRequest(
                query = request.query.trim(),
                dataSourceId = request.dataSourceId,
                userId = currentUserId
            )
        )
    }
}

