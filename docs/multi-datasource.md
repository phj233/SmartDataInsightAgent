# Multi-datasource 接入说明

## 设计目标

- 保留现有主数据源配置不变：`spring.datasource` + Jimmer 继续负责系统主库。
- 新增外部数据源执行能力：分析查询按 `dataSourceId` 连接到用户配置的数据源。
- 支持无数据源场景：用户直接粘贴原始表格文本时，也可以直接生成分析结果和可视化。
- 支持自然语言描述的数据：当不是纯表格而是文字描述的数据时，会先尝试让 LLM 提取结构化数据，再生成可视化。
- 不改现有仓库层和主库事务模型，避免影响 `User`、权限、元数据等现有逻辑。

## 当前实现位置

- `src/main/kotlin/top/phj233/smartdatainsightagent/controller/AnalysisController.kt`
  - 提供 `POST /api/analysis/execute`
  - 从当前登录态读取用户 ID
  - `dataSourceId` 可选
- `src/main/kotlin/top/phj233/smartdatainsightagent/model/AnalysisExecuteRequest.kt`
  - 作为分析接口正式入参
  - 支持不传 `dataSourceId`
- `src/main/kotlin/top/phj233/smartdatainsightagent/service/data/RawTextDataParserService.kt`
  - 解析 JSON 数组、Markdown 表格、CSV、TSV 原始文本
- `src/main/kotlin/top/phj233/smartdatainsightagent/service/data/NaturalLanguageDataExtractionService.kt`
  - 当规则解析失败时，调用 LLM 从自然语言描述中提取结构化数据
  - 清洗并解析 LLM 返回的 JSON
- `src/main/kotlin/top/phj233/smartdatainsightagent/service/data/DataSourceService.kt`
  - 读取主库中的 `data_source` 元数据
  - 校验数据源是否启用
  - 校验数据源是否属于当前用户
  - 扁平化连接配置
- `src/main/kotlin/top/phj233/smartdatainsightagent/service/data/ExternalDataSourceSupport.kt`
  - 解析 `connection_config`
  - 构建 PostgreSQL / MySQL JDBC URL
  - 识别驱动类名
- `src/main/kotlin/top/phj233/smartdatainsightagent/service/data/ExternalJdbcTemplateProvider.kt`
  - 按 `dataSourceId` 创建并缓存 `JdbcTemplate`
  - 生成外部库连接
- `src/main/kotlin/top/phj233/smartdatainsightagent/service/data/QueryExecutorService.kt`
  - 新增 `executeQuery(sql, dataSourceId, userId)`
  - 执行前校验只读 SQL
- `src/main/kotlin/top/phj233/smartdatainsightagent/service/agent/DataAnalysisAgent.kt`
  - 有数据源时按 `dataSourceId + userId` 执行 SQL
  - 无数据源时先做规则解析
  - 规则解析失败时交给 LLM 提取结构化数据后生成可视化
- `src/main/kotlin/top/phj233/smartdatainsightagent/service/agent/QueryParserAgent.kt`
  - 根据当前用户可访问的数据源生成 SQL

## connection_config 推荐结构

`data_source.connection_config` 当前实体类型为：

- `List<Map<String, String>>`

系统会把它扁平化成一个普通键值表，所以推荐这样存：

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
  {"params": "serverTimezone=UTC&allowPublicKeyRetrieval=true"}
]
```

### 直接提供 JDBC URL

如果你已经有完整 JDBC URL，也可以直接传：

```json
[
  {"url": "jdbc:postgresql://127.0.0.1:5432/analytics?currentSchema=public"},
  {"username": "postgres"},
  {"password": "secret"}
]
```

## 原始文本直连分析

当你没有配置数据源时，也可以直接把结构化文本或自然语言描述粘贴到 `query` 里。

当前支持的输入形式：

- JSON 数组
- Markdown 表格
- CSV
- TSV
- 含有明确数值关系的自然语言描述

### 原始文本示例（JSON）

```json
[
  {"month": "Jan", "sales": 1200, "profit": 200},
  {"month": "Feb", "sales": 1500, "profit": 260}
]
```

### 原始文本示例（Markdown 表格）

```markdown
| month | sales | profit |
| --- | --- | --- |
| Jan | 1200 | 200 |
| Feb | 1500 | 260 |
```

### 原始文本示例（CSV）

```csv
month,sales,profit
Jan,1200,200
Feb,1500,260
```

### 自然语言描述示例

```text
今年一季度华北、华东、华南的销售额分别是 120 万、150 万、110 万，利润分别是 18 万、26 万、15 万。
```

说明：

- 系统会先尝试按 JSON/表格/CSV 等规则解析。
- 如果规则解析失败，会调用 LLM 从自然语言描述中提取结构化数据。
- 如果 LLM 能提取出成型数据，就会继续生成洞察和可视化。

## 支持情况

- 已支持：`POSTGRESQL`、`MYSQL`
- 已支持无数据源原始文本直连分析：`JSON`、`Markdown Table`、`CSV`、`TSV`
- 已支持无数据源自然语言数据描述提取后可视化
- 暂不支持直连：`CSV/EXCEL` 文件型数据源配置

## 当前边界

- 主库仍然使用默认 `spring.datasource`
- Jimmer 仓库仍然只操作主库
- 外部数据源目前仅用于分析查询执行
- 外部查询不参与主库事务的一致性管理
- 当前会校验数据源归属用户
- 当前只允许只读查询（`SELECT` / `WITH`）
- 无数据源模式下，`sqlQuery` 会返回 `null`
- 自然语言描述模式依赖 LLM 提取效果；如果文本里没有明确可提取的数据，可能无法生成可视化

## 接口入口

### `POST /api/analysis/execute`

#### 使用外部数据源

```json
{
  "query": "查询最近30天销售趋势",
  "dataSourceId": 1
}
```

#### 直接粘贴原始数据文本

```json
{
  "query": "month,sales,profit\nJan,1200,200\nFeb,1500,260"
}
```

#### 直接输入自然语言描述的数据

```json
{
  "query": "今年一季度华北、华东、华南的销售额分别是120万、150万、110万，利润分别是18万、26万、15万，请做一个对比图。"
}
```

说明：

- 不需要前端传 `userId`
- 系统会从当前登录态中获取用户 ID
- 传了 `dataSourceId` 时，会先校验数据源归属，再执行分析查询
- 不传 `dataSourceId` 时，会优先把 `query` 当成原始结构化数据解析
- 如果不是纯数据而是自然语言描述，会尝试让 LLM 提取结构化数据再生成可视化

## 已验证内容

已添加单元测试覆盖：

- 扁平化连接配置
- PostgreSQL JDBC URL 生成
- MySQL JDBC URL 生成
- 显式 JDBC URL 透传
- 不支持的数据源类型校验
- 数据源归属校验
- 只读 SQL 校验
- 原始文本 JSON 解析
- 原始文本 Markdown 表格解析
- 原始文本 CSV 解析
- 无法识别的原始文本拦截
- LLM 提取结果 JSON 解析
- Markdown 包裹的 LLM JSON 响应解析
