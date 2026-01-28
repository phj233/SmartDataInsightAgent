package top.phj233.smartdatainsightagent.entity

import org.babyfish.jimmer.sql.*
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import java.time.LocalDateTime

/**
 * @author phj233
 * @since 2026/1/5 18:19
 * @version
 */
@Entity
@Table(name = "analysis_tasks")
interface AnalysisTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val userId: String

    // 用户输入的原始查询
    @Column(name = "original_query")
    val originalQuery: String

    // 生成的SQL
    @Column(name = "generated_sql")
    val generatedSql: String?

    // 任务参数 JSON
    val parameters: String?

    val status: AnalysisStatus

    // 分析结果 JSON (包含数据摘要、洞察等)
    val result: String?

    val executionTime: Long?

    val errorMessage: String?

    @Column(name = "created_at")
    val createdAt: LocalDateTime

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime
}
