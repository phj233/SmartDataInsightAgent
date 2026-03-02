package top.phj233.smartdatainsightagent.entity

import org.babyfish.jimmer.sql.*
import top.phj233.smartdatainsightagent.entity.enums.AnalysisStatus
import top.phj233.smartdatainsightagent.model.AnalysisResult
import java.time.LocalDateTime

/**
 * @author phj233
 * @since 2026/1/5 18:19
 * @version
 */
@Entity
@Table(name = "analysis_tasks")
interface AnalysisTask: BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @IdView
    val userId: Long

    @ManyToOne
    val user: User

    // 用户输入的原始查询
    @Column(name = "original_query")
    val originalQuery: String

    // 生成的SQL
    @Column(name = "generated_sql")
    val generatedSql: String?

    /**
     * 任务参数 JSON
     * */
    @Serialized
    val parameters: List<Map<String, Any>>

    val status: AnalysisStatus

    /**
     * 分析结果 JSON (包含数据摘要、洞察等)
     */
    @Serialized
    val result: List<AnalysisResult>?

    val executionTime: Long?

    val errorMessage: String?

    @Column(name = "created_at")
    val createdAt: LocalDateTime

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime
}
