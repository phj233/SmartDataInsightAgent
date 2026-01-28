package top.phj233.smartdatainsightagent.model.visualization

/**
 * 发送给 AI 的数据元信息
 */
data class DataSchema(
    val fieldName: String,
    val fieldType: String, // NUMERIC, STRING, DATE
    val exampleValue: Any?
)
