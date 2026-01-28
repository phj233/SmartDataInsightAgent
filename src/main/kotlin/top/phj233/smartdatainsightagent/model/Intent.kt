package top.phj233.smartdatainsightagent.model

import top.phj233.smartdatainsightagent.entity.enums.IntentType

data class Intent(
    val type: IntentType,
    val parameters: Map<String, Any>? = null, // 可空，避免解析失败
    val confidence: Double = 0.0
)
