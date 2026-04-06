# SmartDataInsightAgent

基于 `Spring Boot 3 + Kotlin + Jimmer + Spring AI + Sa-Token` 的智能数据分析后端。

项目目标是把用户的自然语言分析请求转成可追踪、可回放、可视化的分析任务结果，并支持两种输入模式：

- 外部数据源分析：基于 `dataSourceId` 连接用户自己的 PostgreSQL 或 MySQL 数据库，生成只读 SQL 并执行。
- 原始文本分析：用户直接提交 JSON、Markdown 表格、CSV、TSV 或自然语言数据描述，系统提取结构化数据后生成洞察和图表。

---

## 当前能力

- 支持异步任务模式：`POST /api/analysis/tasks`
- 支持同步执行模式：`POST /api/analysis/execute`
- 支持任务 SSE 进度订阅：`GET /api/analysis/tasks/{taskId}/events`
- 支持任务列表、详情、重命名、LLM 自动命名、删除
- 支持用户数据源管理、启停、Schema 探测和强制刷新
- 支持登录、邮箱验证码、登出、当前用户信息、头像上传
- 输出分析文本和可直接渲染的 ECharts `option`
- 对未登录的普通接口返回 `401 JSON`，对未登录的 SSE 返回 `event: error`

---

## 技术栈

### 后端基础

- Spring Boot `3.5.9`
- Kotlin `2.1.0`
- Java `21`
- Coroutines

### 数据与存储

- Jimmer `0.10.6`
- PostgreSQL
- MySQL
- Redis
- MinIO

### AI 能力

- Spring AI `1.1.2`
- DeepSeek Chat Model

### 认证与接口契约

- Sa-Token `1.44.0`
- Jimmer OpenAPI: `/openapi.yml`、`/openapi.html`
- Jimmer TypeScript Zip: `/ts.zip`

---

## 项目结构

```text
src/main/
├─ dto/                                # Jimmer DTO 定义
├─ kotlin/top/phj233/smartdatainsightagent/
│  ├─ config/                          # Sa-Token / MinIO / Jimmer / Trace
│  ├─ controller/                      # Analysis / DataSource / User API
│  ├─ entity/                          # Jimmer 实体与枚举
│  ├─ exception/                       # 业务异常与全局异常处理
│  ├─ interceptor/                     # 审计字段拦截
│  ├─ model/                           # 请求、结果、进度事件、可视化模型
│  ├─ repository/                      # Jimmer Repository
│  └─ service/
│     ├─ agent/                        # Query / Analysis / Visualization Agent
│     ├─ ai/                           # DeepSeek 封装
│     ├─ data/                         # 外部数据源、Schema 探测、执行器、文本解析
│     └─ storage/                      # MinIO 服务
└─ resources/
   ├─ application.yml
   ├─ application-secrets.yml
   ├─ db/schema/
   └─ templates/mail.html
```

---

## 核心流程

### 1. 异步任务主流程

推荐使用异步任务 + SSE。

调用链：

`AnalysisController.createTask`
-> `AnalysisTaskService.createTask`
-> `AnalysisExecutionService.submit`
-> `DataAnalysisAgent.analyzeDataForTask`

执行步骤：

1. 前端调用 `POST /api/analysis/tasks`，提交 `query` 和可选的 `dataSourceId`。
2. 后端会先对 `query` 做 `trim()`，并从当前登录态中读取 `userId`。
3. 创建 `AnalysisTask` 记录，初始状态为 `PENDING`，并写入第一条阶段记录 `TASK_CREATED`。
4. 接口立即返回 `taskId` 和当前状态，不等待 AI 或 SQL 执行完成。
5. `AnalysisExecutionService` 使用 `SupervisorJob + Dispatchers.Default` 在后台协程执行分析任务。
6. 分析过程中，每到一个关键阶段都会落库阶段记录，并通过 SSE 推送 `task-progress`。
7. 成功时写入 `generatedSql`、`result`、`executionTime`，状态变为 `SUCCESS`。
8. 失败时写入 `errorMessage` 和失败阶段，状态变为 `FAILED`。
9. 任务进入终态后，SSE 服务端会主动关闭该任务的订阅连接。

### 2. 分析链路分支

#### 有 `dataSourceId`

适用于结构化数据库分析。

1. `DataAnalysisAgent` 先识别用户意图。
2. 根据意图选择对应流程：
   - `DATA_QUERY`
   - `TREND_ANALYSIS`
   - `PREDICTION`
   - `REPORT_GENERATION`
3. `QueryParserAgent` 基于用户问题、数据源和 Schema 生成 SQL。
4. `QueryExecutorService` 对 SQL 做只读校验后执行。
5. 生成洞察文本和图表建议。

#### 无 `dataSourceId`

适用于文本直传分析。

1. 先进入 `RAW_TEXT_PARSING`。
2. `RawTextDataParserService` 优先按规则解析：
   - JSON 数组
   - Markdown 表格
   - CSV
   - TSV
3. 如果规则解析失败，则进入 `NATURAL_LANGUAGE_EXTRACTION`。
4. `NaturalLanguageDataExtractionService` 调用 LLM 从自然语言中提取结构化数据。
5. 生成洞察文本和图表建议。

### 3. 任务状态和阶段

任务状态：

- `PENDING`：任务已创建，等待执行
- `RUNNING`：任务执行中
- `SUCCESS`：执行成功
- `FAILED`：执行失败

常见阶段：

- 通用阶段：`TASK_CREATED`、`INSIGHTS_GENERATING`、`VISUALIZATION_GENERATING`、`COMPLETED`
- 数据源分析：`INTENT_ANALYZING`、`INTENT_RESOLVED`、`SQL_GENERATING`、`SQL_GENERATED`、`QUERY_EXECUTING`、`QUERY_EXECUTED`
- 原始文本分析：`RAW_TEXT_PARSING`、`NATURAL_LANGUAGE_EXTRACTION`
- 特殊意图：`PREDICTION_GENERATING`、`REPORT_GENERATING`

### 4. SSE 订阅模型

订阅端点：

`GET /api/analysis/tasks/{taskId}/events`

事件类型：

- `connected`：SSE 握手成功
- `task-progress`：任务阶段或状态发生变化

`task-progress` 关键字段：

- `taskId`
- `status`
- `stage`
- `timestamp`
- `details`
- `generatedSql`
- `errorMessage`

前端推荐流程：

1. 创建任务，拿到 `taskId`
2. 立即建立 SSE 订阅
3. 同时低频轮询 `GET /api/analysis/tasks/{taskId}` 兜底
4. 收到 `SUCCESS` 或 `FAILED` 后关闭轮询
5. 再拉一次详情作为最终一致结果

---

## 接口概览

### 分析任务

- `POST /api/analysis/tasks`：创建异步分析任务
- `GET /api/analysis/tasks/{taskId}/events`：订阅任务 SSE
- `POST /api/analysis/execute`：同步执行分析
- `GET /api/analysis/tasks`：查询当前用户任务列表
- `GET /api/analysis/tasks/{taskId}`：查询任务详情
- `PATCH /api/analysis/tasks/{taskId}/name`：手动重命名任务
- `POST /api/analysis/tasks/{taskId}/name/llm`：LLM 自动命名
- `DELETE /api/analysis/tasks/{taskId}`：删除任务

### 用户

- `POST /api/user/sendCode`
- `POST /api/user/register`
- `POST /api/user/login`
- `POST /api/user/loginByCode`
- `GET /api/user/me`
- `PATCH /api/user/me`
- `POST /api/user/logout`
- `POST /api/user/avatar`

### 数据源

- `POST /api/data-sources`
- `GET /api/data-sources`
- `GET /api/data-sources/{id}`
- `PUT /api/data-sources/{id}`
- `PATCH /api/data-sources/{id}/activate`
- `PATCH /api/data-sources/{id}/deactivate`

---

## 关键请求示例

### 1. 创建异步分析任务

```json
{
  "query": "统计最近30天订单金额趋势",
  "dataSourceId": 1
}
```

返回：

```json
{
  "taskId": 123,
  "status": "PENDING"
}
```

### 2. 同步执行分析

```json
{
  "query": "统计最近30天订单金额趋势",
  "dataSourceId": 1
}
```

### 3. 直接分析原始文本

```json
{
  "query": "month,sales,profit\nJan,1200,200\nFeb,1500,260"
}
```

### 4. 自然语言数据描述

```json
{
  "query": "今年一季度华北、华东、华南的销售额分别是120万、150万、110万，利润分别是18万、16万、15万，请给出对比分析。"
}
```

---

## 多数据源说明

### 当前支持的外部数据源类型

- `POSTGRESQL`
- `MYSQL`

枚举里存在但当前没有打通直连执行链路：

- `CSV`
- `EXCEL`

### `connectionConfig` 结构

项目使用 `List<Map<String, String>>` 存储连接信息，运行时会被扁平化成普通键值对。

PostgreSQL 示例：

```json
[
  {"host": "127.0.0.1"},
  {"port": "5432"},
  {"database": "analytics"},
  {"username": "postgres"},
  {"password": "secret"},
  {"schema": "public"}
]
```

MySQL 示例：

```json
[
  {"host": "127.0.0.1"},
  {"port": "3306"},
  {"database": "sales"},
  {"username": "root"},
  {"password": "secret"}
]
```

也可以直接传 JDBC URL：

```json
[
  {"url": "jdbc:postgresql://127.0.0.1:5432/analytics?currentSchema=public"},
  {"username": "postgres"},
  {"password": "secret"}
]
```

### Schema 探测规则

- 创建数据源后，如果 `schemaInfo` 为空，会自动探测并回填
- 更新数据源时，会忽略请求中的 `schemaInfo`，避免误覆盖
- 如需强制刷新 Schema，可调用：

`PUT /api/data-sources/{id}?refreshSchemaOnly=true`

---

## 认证与异常处理

### 默认认证策略

`/api/**` 默认要求登录。

白名单：

- `/api/user/login`
- `/api/user/register`
- `/api/user/sendCode`
- `/api/user/loginByCode`
- `/openapi.html`
- `/openapi.yml`
- `/ts.zip`
- `/error`

`OPTIONS` 预检请求直接放行。

### 未登录响应

- 普通 HTTP 接口：返回 `401 JSON`
- SSE 接口：返回 `event: error`，错误码为 `AUTH_NOT_LOGIN`

这样前端可以区分“认证失败”和“网络断开”。

---

## 配置说明

主配置文件：

`src/main/resources/application.yml`

通过：

`spring.config.import=optional:classpath:application-secrets.yml`

加载敏感配置。

至少需要准备以下环境变量：

- `DEEPSEEK_API_KEY`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MINIO_ENDPOINT`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `MINIO_BUCKET`
- `MINIO_PUBLIC_BASE_URL`

对外开放的契约地址：

- `GET /openapi.html`
- `GET /openapi.yml`
- `GET /ts.zip`

---

## 启动与测试

### 1. 启动依赖

```powershell
Set-Location "D:\programing\SmartDataInsightAgent"
docker compose up -d
```

`compose.yaml` 当前包含：

- PostgreSQL
- Redis
- MinIO

### 2. 启动应用

```powershell
Set-Location "D:\programing\SmartDataInsightAgent"
.\gradlew.bat bootRun
```

### 3. 运行测试

```powershell
Set-Location "D:\programing\SmartDataInsightAgent"
.\gradlew.bat test -x processTestAot --no-daemon
```

当前测试覆盖主要包括：

- `AnalysisTaskServiceTest`
- `DataAnalysisAgentTest`
- `VisualizationAgentTest`
- `DataSourceServiceTest`
- `DataSourceControllerTest`
- `QueryExecutorServiceTest`
- `RawTextDataParserServiceTest`
- `NaturalLanguageDataExtractionServiceTest`
- `GlobalExceptionHandlerTest`

---

## 当前限制

1. 主库仍然只有一个 `spring.datasource`，Jimmer 只操作系统主库。
2. 外部数据源目前只用于分析查询，不参与主库事务。
3. 外部查询仅允许只读 SQL，核心目标是防止危险语句执行。
4. `CSV`、`EXCEL` 虽然存在枚举定义，但未实现数据源直连执行。
5. 原始文本提取和分析质量会受 LLM 输出稳定性影响。
6. 任务执行使用协程异步 + 阻塞式持久化，不是全链路响应式架构。

---

## 相关文档

- `docs/frontend-vue3-sse-guide.md`：前端对接 SSE 与 Jimmer TS 的建议
- `docs/multi-datasource.md`：多数据源接入说明
- `docs/multi-datasource-changes.md`：多数据源改动记录
