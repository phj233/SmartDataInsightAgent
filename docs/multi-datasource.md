# 多数据源与分析入口说明

## 1. 设计目标

项目当前采用“主库固定 + 外部数据源按需连接”的结构：

- 系统主库继续使用 `spring.datasource` + Jimmer
- 外部 PostgreSQL / MySQL 只服务分析链路
- 数据源按 `dataSourceId` 解析连接配置并动态执行查询
- 无数据源时允许直接分析原始文本或自然语言描述的数据
- 不把动态数据源引入主业务事务模型，避免影响用户、角色、任务等主库逻辑

---

## 2. 当前实现位置

### 分析入口

- `AnalysisController.kt`
  - `POST /api/analysis/tasks`
  - `POST /api/analysis/tasks/{taskId}/reanalyze`
  - `GET /api/analysis/tasks/{taskId}/events`
  - `POST /api/analysis/execute`

### 数据源与连接

- `DataSourceService.kt`
  - 用户数据源 CRUD
  - 归属校验
  - 激活状态校验
  - Schema 自动回填 / 刷新
- `ExternalDataSourceSupport.kt`
  - 扁平化 `connectionConfig`
  - JDBC URL 生成
  - 驱动类名解析
- `ExternalJdbcTemplateProvider.kt`
  - 基于 `dataSourceId` 和配置指纹缓存 `JdbcTemplate`
  - 支持 `queryTimeout`
- `DataSourceSchemaIntrospectionService.kt`
  - 通过 JDBC 元数据探测表和列信息

### 分析执行

- `QueryParserAgent.kt`
  - 基于数据源类型和 `schemaInfo` 生成只读 SQL
- `QueryExecutorService.kt`
  - 执行前做只读 SQL 校验
- `DataAnalysisAgent.kt`
  - 统一编排数据源模式、原始文本模式、预测模式、报告模式

### 原始文本与自然语言兜底

- `RawTextDataParserService.kt`
  - JSON 数组
  - Markdown 表格
  - CSV / TSV / 分号分隔文本
  - 代码块中的结构化文本
- `NaturalLanguageDataExtractionService.kt`
  - 当规则解析失败时调用 LLM 提取显式数据

---

## 3. 分析入口如何使用多数据源

### 推荐入口：异步任务

```http
POST /api/analysis/tasks
```

带数据源示例：

```json
{
  "query": "查询最近30天销售趋势",
  "dataSourceId": 1
}
```

无数据源示例：

```json
{
  "query": "month,sales,profit\nJan,1200,200\nFeb,1500,260"
}
```

特点：

- 返回 `taskId`
- 可通过 SSE 订阅进度
- 支持失败原地重试
- 适合作为正式分析入口

### 即时入口：同步执行

```http
POST /api/analysis/execute
```

特点：

- 直接返回 `AnalysisResult`
- 内部也会创建并执行任务记录
- 响应不返回 `taskId`
- 适合即时预览，不适合前端做任务追踪

### 失败任务重试

```http
POST /api/analysis/tasks/{taskId}/reanalyze
```

可选请求体：

```json
{
  "query": "请改成按月份统计并比较利润趋势"
}
```

行为：

- 仅 `FAILED` 任务可调用
- 复用原 `taskId`
- 可覆盖原始 `query`
- 清空旧 SQL / 旧结果 / 旧错误信息

---

## 4. 数据源管理接口

### 用户侧

- `POST /api/data-sources`
- `GET /api/data-sources`
- `GET /api/data-sources/{id}`
- `PUT /api/data-sources/{id}`
- `PATCH /api/data-sources/{id}/activate`
- `PATCH /api/data-sources/{id}/deactivate`

### 管理员侧

- `GET /api/admin/data-sources`
- `GET /api/admin/data-sources/{id}`
- `POST /api/admin/data-sources`
- `PUT /api/admin/data-sources/{id}`
- `DELETE /api/admin/data-sources/{id}`

约束：

- 用户侧只能操作自己的数据源
- 分析执行阶段只允许访问“属于当前用户且已启用”的数据源
- 管理员可以代用户创建、更新、转移归属、删除数据源

---

## 5. `connectionConfig` 结构

实体字段类型为：

- `List<Map<String, String>>`

服务层会扁平化成普通键值表，所以推荐按“键值对数组”提交。

### PostgreSQL 示例

```json
[
  {"host": "127.0.0.1"},
  {"port": "5432"},
  {"database": "analytics"},
  {"username": "postgres"},
  {"password": "secret"},
  {"schema": "public"},
  {"params": "sslmode=require"}
]
```

### MySQL 示例

```json
[
  {"host": "127.0.0.1"},
  {"port": "3306"},
  {"database": "sales"},
  {"username": "root"},
  {"password": "secret"},
  {"params": "serverTimezone=UTC&allowPublicKeyRetrieval=true"},
  {"queryTimeout": "30"}
]
```

### 直接提供 JDBC URL

```json
[
  {"url": "jdbc:postgresql://127.0.0.1:5432/analytics?currentSchema=public"},
  {"username": "postgres"},
  {"password": "secret"}
]
```

### 支持的别名键

- 数据库名：`database` / `dbName` / `dbname` / `databaseName`
- JDBC URL：`url` / `jdbcUrl`
- 驱动：`driverClassName` / `driver-class-name` / `driver`
- PostgreSQL schema：`schema` / `currentSchema`
- 用户名：`username` / `user`
- 自定义超时：`queryTimeout`

---

## 6. Schema 探测规则

- 创建数据源后，如果 `schemaInfo` 为空，会自动探测并回填
- 用户更新数据源时，服务层会忽略请求体中的 `schemaInfo`
- `PUT /api/data-sources/{id}?refreshSchemaOnly=true` 会强制重新探测结构
- 探测结果为空时，不阻断保存流程，只是保留空 `schemaInfo`

探测来源：

- PostgreSQL：按 `schema/currentSchema` 查询元数据，默认 `public`
- MySQL：按 `database` 查询元数据

---

## 7. 查询安全约束

外部查询只允许只读 SQL。

### 允许

- `SELECT ...`
- `WITH ... SELECT ...`

### 拦截

- `INSERT`
- `UPDATE`
- `DELETE`
- `DROP`
- `ALTER`
- `TRUNCATE`
- `CREATE`
- `MERGE`
- `GRANT`
- `REVOKE`
- `CALL`
- `EXEC`
- 注释注入：`--`、`/*`
- 多语句拼接：`; ...`

说明：

- SQL 生成阶段会在 `QueryParserAgent` 做一次校验
- SQL 执行阶段会在 `QueryExecutorService` 再做一次校验

---

## 8. 无数据源模式

当请求里不传 `dataSourceId` 时，系统会把 `query` 当成原始数据文本处理。

### 当前支持的原始输入

- JSON 数组
- Markdown 表格
- CSV
- TSV
- 代码块中的上述格式
- 含明确数据的自然语言描述

### JSON 示例

```json
[
  {"month": "Jan", "sales": 1200, "profit": 200},
  {"month": "Feb", "sales": 1500, "profit": 260}
]
```

### Markdown 表格示例

```markdown
| month | sales | profit |
| --- | --- | --- |
| Jan | 1200 | 200 |
| Feb | 1500 | 260 |
```

### CSV 示例

```csv
month,sales,profit
Jan,1200,200
Feb,1500,260
```

### 自然语言示例

```text
今年一季度华北、华东、华南的销售额分别是 120 万、150 万、110 万，利润分别是 18 万、26 万、15 万。
```

行为：

1. 优先规则解析
2. 规则解析失败时，调用 LLM 提取显式数据
3. 生成洞察与图表建议
4. 此模式下 `sqlQuery` 恒为 `null`

---

## 9. 当前支持与边界

### 已支持

- `POSTGRESQL` 外部数据源直连
- `MYSQL` 外部数据源直连
- 用户级数据源归属校验
- 数据源激活状态校验
- Schema 自动探测与强制刷新
- 异步任务 + SSE + 原地重试
- 原始文本直连分析
- 自然语言数据提取后分析
- 预测与报告生成两类高阶分析模式

### 暂未支持

- `CSV` / `EXCEL` 数据源直连查询
- 外部数据源连接池化
- 外部数据源健康检查接口
- 多数据源写操作事务一致性
- `/api/analysis/execute` 返回 `taskId`

---

## 10. 使用建议

1. 前端正式分析入口优先使用 `/api/analysis/tasks`。
2. 数据源录入时，前端不要强制用户手填 `schemaInfo`。
3. 外部数据源查询统一按“只读分析”定位，不要在产品设计里加入写库预期。
4. 如果用户只提供自然语言数据描述，提示他写出明确的维度和数值，提取成功率会明显更高。
5. 如果需要更高并发或更稳定的外部连接，应优先把 `DriverManagerDataSource` 升级为池化方案。
