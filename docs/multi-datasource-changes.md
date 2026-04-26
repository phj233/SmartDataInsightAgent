# 多数据源与任务化分析演进记录

> 更新日期：2026-04-26
>
> 本文不再只记录“最初那次多数据源改动”，而是总结当前仓库里与多数据源、任务执行、SSE、原始文本分析、管理端能力相关的实际落地情况。

## 一、演进目标

当前这条演进线的核心目标是三件事：

1. 保持系统主库稳定，不把动态数据源引入主业务事务链路
2. 让分析请求既支持外部数据库，也支持直接贴原始文本
3. 把分析执行从“一次性调用”升级成“可追踪、可重试、可管理”的任务体系

---

## 二、阶段性变化

### 阶段 1：主库不动，接入外部数据源分析

落地内容：

- 保留 `spring.datasource + Jimmer` 作为系统主库
- 引入 `ExternalDataSourceSupport` 解析外部连接参数
- 引入 `ExternalJdbcTemplateProvider` 动态创建并缓存外部 `JdbcTemplate`
- 通过 `dataSourceId` 访问用户自己的 PostgreSQL / MySQL
- 增加双重只读 SQL 校验

对应文件：

- `service/data/ExternalDataSourceSupport.kt`
- `service/data/ExternalJdbcTemplateProvider.kt`
- `service/data/QueryExecutorService.kt`
- `service/data/DataSourceService.kt`
- `service/agent/QueryParserAgent.kt`

### 阶段 2：把分析能力任务化

落地内容：

- 增加异步任务入口 `POST /api/analysis/tasks`
- 增加 SSE 订阅 `GET /api/analysis/tasks/{taskId}/events`
- 引入 `AnalysisExecutionService` 后台协程执行
- 引入任务阶段轨迹持久化
- 支持任务重命名、删除、LLM 自动命名
- 支持失败任务原地重试，并允许覆盖 `query`

对应文件：

- `controller/AnalysisController.kt`
- `service/AnalysisExecutionService.kt`
- `service/AnalysisSseService.kt`
- `service/AnalysisTaskService.kt`
- `entity/AnalysisTask.kt`
- `model/AnalysisTaskProgressEvent.kt`
- `model/AnalysisTaskStageRecord.kt`

### 阶段 3：补齐无数据源分析

落地内容：

- 新增 `RawTextDataParserService`
- 支持 JSON 数组、Markdown 表格、CSV、TSV、代码块解析
- 新增 `NaturalLanguageDataExtractionService`
- 当规则解析失败时，让 LLM 只提取用户明确给出的数据
- 无数据源模式同样走统一的任务阶段记录和结果结构

对应文件：

- `service/data/RawTextDataParserService.kt`
- `service/data/NaturalLanguageDataExtractionService.kt`
- `service/agent/DataAnalysisAgent.kt`

### 阶段 4：扩展管理端能力

落地内容：

- 管理员任务分页、失败任务编辑与删除
- 管理员数据源分页、代用户创建与更新、归属变更、删除
- 管理员用户与角色 CRUD
- 新增 `/api/admin/visualization/dashboard` 管理端看板

对应文件：

- `controller/admin/*.kt`
- `service/admin/*.kt`
- `model/admin/AdminVisualizationDashboard.kt`

---

## 三、当前实现状态

### 1. 数据源侧

已经具备：

- 用户侧数据源 CRUD
- 管理员侧数据源 CRUD
- 每用户数据源名称唯一约束
- 激活 / 停用能力
- JDBC 元数据自动探测 `schemaInfo`
- `refreshSchemaOnly=true` 强制刷新
- `queryTimeout` 可通过连接配置注入

仍未具备：

- `CSV` / `EXCEL` 直连
- 连接健康检查接口
- 连接池化

### 2. 分析入口侧

已经具备：

- 异步任务入口
- SSE 进度订阅
- 失败任务原地重试
- 同步执行入口
- LLM 自动命名
- 手动重命名与删除

需要特别说明：

- 同步执行入口内部仍会创建任务记录
- 但响应里没有 `taskId`
- 因此前端如果要做可追踪分析，仍应优先使用异步任务入口

### 3. 分析模式侧

已经具备：

- 数据查询 `DATA_QUERY`
- 趋势分析 `TREND_ANALYSIS`
- 预测 `PREDICTION`
- 报告生成 `REPORT_GENERATION`
- 原始文本结构化解析
- 自然语言显式数据提取

输出结构统一为：

- `data`
- `insights`
- `visualizations`
- `sqlQuery`

---

## 四、关键实现决策

### 决策 1：不做全局动态数据源路由

原因：

- 当前主业务数据仍在系统主库
- 用户、角色、任务、元数据管理不应被外部数据源影响
- 动态数据源只服务分析查询，比全局切换更安全

### 决策 2：双层 SQL 安全校验

原因：

- LLM 生成 SQL 不是可信输入
- 仅靠提示词约束不够
- 在 SQL 生成和 SQL 执行两个阶段都拦截危险语句，可以降低误伤风险

### 决策 3：原始文本优先规则解析，再走 LLM

原因：

- JSON / Markdown / CSV 等结构化文本不应浪费模型调用
- 规则解析更快、更稳、成本更低
- 只有当用户输入本身是描述性文本时，才值得启用自然语言提取兜底

### 决策 4：任务轨迹持久化而不是只做日志

原因：

- 前端需要可回放、可订阅、可重试的真实任务流
- 管理端需要查看失败任务和执行阶段
- 单靠日志无法支撑用户界面和后台管理

---

## 五、当前文档与实现已对齐的重点

这次文档整理后，已经明确补上了以下过去容易误解的点：

- `/api/analysis/execute` 的“同步返回 + 仍会建任务”的真实行为
- `PATCH /api/user/me` 当前只真正处理密码更新
- 管理员接口与看板能力已经落地，不再是待规划项
- 用户侧更新数据源时，`schemaInfo` 会被服务层忽略
- 重分析接口支持可选 `query` 覆盖原始问题
- SSE 订阅会先推送快照，终态后会主动关闭连接

---

## 六、建议的下一步

1. 为同步执行接口补一个可选的 `taskId` 返回值，避免“落库了但前端无法追踪”的割裂体验。
2. 把外部连接从 `DriverManagerDataSource` 升级为连接池实现。
3. 为数据源新增连通性测试接口，降低用户录入成本。
4. 补齐 `PATCH /api/user/me` 的昵称、邮箱、头像更新链路。
5. 如果管理端数据量继续增长，把 `AdminVisualizationService` 从“全量加载内存聚合”升级为数据库聚合查询。
