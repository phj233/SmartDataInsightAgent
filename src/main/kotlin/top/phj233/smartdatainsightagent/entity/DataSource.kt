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
interface DataSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val name: String

    val type: DataSourceType

    // 存储连接配置的JSON (例如: host, port, username, password, dbName)
    @Column(name = "connection_config")
    val connectionConfig: String

    // 存储schema信息 (JSON格式或DDL文本)
    @Column(name = "schema_info")
    val schemaInfo: String?

    val active: Boolean
}
