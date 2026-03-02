package top.phj233.smartdatainsightagent.entity

import org.babyfish.jimmer.sql.*
import top.phj233.smartdatainsightagent.entity.enums.DataSourceType

/**
 * @author phj233
 * @since 2026/1/28 14:40
 * @version
 */
@Entity
@Table(name = "data_source")
interface DataSource: BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @IdView
    val userId: Long

    @ManyToOne
    val user: User

    val name: String

    val type: DataSourceType

    // 存储连接配置的JSON (例如: host, port, username, password, dbName)
    @Serialized
    @Column(name = "connection_config")
    val connectionConfig: List<Map<String, String>>

    // 存储schema信息 (JSON格式或DDL文本)
    @Serialized
    @Column(name = "schema_info")
    val schemaInfo: List<Map<String, String>>

    val active: Boolean
}
