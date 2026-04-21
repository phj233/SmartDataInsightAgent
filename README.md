# SmartDataInsightAgent

基于 `Spring Boot 3 + Kotlin + Jimmer + Spring AI + Sa-Token` 的智能数据分析后端。

项目目标是把用户自然语言分析请求转换为 **可追踪、可回放、可订阅** 的分析任务，并支持两种输入模式：

- **外部数据源分析**：基于 `dataSourceId` 连接用户自己的 PostgreSQL / MySQL，生成只读 SQL 并执行
- **原始文本分析**：直接提交 JSON、Markdown 表格、CSV、TSV 或自然语言数据描述，自动提取结构化数据并生成洞察与图表建议

---

## 当前能力

- 异步任务模式：`POST /api/analysis/tasks`
- 失败任务原地重试：`POST /api/analysis/tasks/{taskId}/reanalyze`
- 同步执行模式：`POST /api/analysis/execute`
- 任务 SSE 订阅：`GET /api/analysis/tasks/{taskId}/events`
- 任务列表、详情、重命名、LLM 自动命名、删除
- 用户数据源管理、启停、Schema 自动探测与手动刷新
- 登录、邮箱验证码、登出、当前用户信息、头像上传
- 输出分析文本与可直接渲染的 ECharts `option`
- 未登录时：普通接口返回 `401 JSON`，SSE 返回 `event: error`（`AUTH_NOT_LOGIN`）

---

## 技术栈

### 后端基础

- Spring Boot `3.5.9`
- Kotlin `2.1.0`
- Java `21`
- Coroutines

### 数据与存储

- Jimmer `0.10.6`
- PostgreSQL（主库）
- MySQL（外部分析数据源）
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

### 1) 异步任务主流程（推荐）

调用链：

`AnalysisController.createTask`
-> `AnalysisTaskService.createTask`
-> `AnalysisExecutionService.submit`
-> `DataAnalysisAgent.analyzeDataForTask`

执行步骤：

1. 前端调用 `POST /api/analysis/tasks`，提交 `query` 和可选 `dataSourceId`
2. 后端 `trim()` 查询文本，绑定当前登录 `userId`
3. 创建任务记录，状态 `PENDING`，写入首个阶段 `TASK_CREATED`
4. 接口立即返回 `taskId` 与状态
5. 后台协程（`SupervisorJob + Dispatchers.Default`）异步执行分析
6. 每个关键阶段落库并通过 SSE 推送 `task-progress`
7. 成功时写入 `generatedSql`、`result`、`executionTime`，状态 `SUCCESS`
8. 失败时写入 `errorMessage` 与失败阶段，状态 `FAILED`
9. 任务终态后 SSE 服务端主动关闭订阅

### 2) 失败任务原地重试

调用：`POST /api/analysis/tasks/{taskId}/reanalyze`

- 仅允许对 `FAILED` 任务重试
- 不创建新任务，复用原 `taskId`
- 状态重置为 `PENDING`
- 增加阶段 `REANALYZE_REQUESTED`

### 3) 分析链路分支

#### 有 `dataSourceId`（结构化数据源）

1. `DataAnalysisAgent` 先识别用户意图
2. 根据意图进入对应流程：
   - `DATA_QUERY`
   - `TREND_ANALYSIS`
   - `PREDICTION`
   - `REPORT_GENERATION`
3. `QueryParserAgent` 结合问题与 Schema 生成 SQL
4. `QueryExecutorService` 做只读校验后执行
5. 生成洞察文本和可视化建议

#### 无 `dataSourceId`（文本直传）

1. 进入 `RAW_TEXT_PARSING`
2. `RawTextDataParserService` 优先规则解析：JSON / Markdown 表格 / CSV / TSV
3. 规则解析失败时进入 `NATURAL_LANGUAGE_EXTRACTION`
4. `NaturalLanguageDataExtractionService` 调用 LLM 提取结构化数据
5. 生成洞察文本和可视化建议

### 4) 任务状态与阶段

任务状态：

- `PENDING`
- `RUNNING`
- `SUCCESS`
- `FAILED`

常见阶段：

- 通用：`TASK_CREATED`、`INSIGHTS_GENERATING`、`VISUALIZATION_GENERATING`、`COMPLETED`
- 数据源分析：`INTENT_ANALYZING`、`INTENT_RESOLVED`、`SQL_GENERATING`、`SQL_GENERATED`、`QUERY_EXECUTING`、`QUERY_EXECUTED`
- 原始文本分析：`RAW_TEXT_PARSING`、`NATURAL_LANGUAGE_EXTRACTION`
- 特殊意图：`PREDICTION_GENERATING`、`REPORT_GENERATING`
- 重试与兜底：`REANALYZE_REQUESTED`、`ASYNC_EXECUTION_FAILED`

### 5) SSE 事件模型

订阅端点：

`GET /api/analysis/tasks/{taskId}/events`

事件类型：

- `connected`：握手成功
- `task-progress`：阶段或状态更新
- `onSuccess`：任务成功补充事件

`task-progress` 核心字段：

- `taskId`
- `status`
- `stage`
- `timestamp`
- `details`
- `generatedSql`
- `errorMessage`

---

## 接口概览

### 分析任务

- `POST /api/analysis/tasks`：创建异步任务
- `POST /api/analysis/tasks/{taskId}/reanalyze`：失败任务原地重试
- `GET /api/analysis/tasks/{taskId}/events`：订阅 SSE
- `POST /api/analysis/execute`：同步执行分析
- `GET /api/analysis/tasks`：任务列表
- `GET /api/analysis/tasks/{taskId}`：任务详情
- `PATCH /api/analysis/tasks/{taskId}/name`：手动重命名
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

## 多数据源说明

### 当前支持的外部数据源

- `POSTGRESQL`
- `MYSQL`

枚举里存在但未打通直连执行：

- `CSV`
- `EXCEL`

### `connectionConfig` 结构

项目使用 `List<Map<String, String>>` 存储连接信息，运行时会扁平化为普通键值对。

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

也可直接传 JDBC URL：

```json
[
  {"url": "jdbc:postgresql://127.0.0.1:5432/analytics?currentSchema=public"},
  {"username": "postgres"},
  {"password": "secret"}
]
```

### Schema 探测规则

- 创建数据源后，若 `schemaInfo` 为空则自动探测并回填
- 更新数据源时会忽略请求中的 `schemaInfo`，避免误覆盖
- 强制刷新 Schema：`PUT /api/data-sources/{id}?refreshSchemaOnly=true`

---

## 安全与认证

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

### SQL 安全策略

外部查询仅允许只读 SQL：

- 必须以 `SELECT` 或 `WITH` 开头
- 拦截 `INSERT/UPDATE/DELETE/DROP/...` 等危险语句
- 拦截注释注入与多语句拼接（如 `--`、`/*`、`; ...`）

### 未登录响应

- 普通 HTTP：`401 JSON`
- SSE：`event: error`，错误码 `AUTH_NOT_LOGIN`

---

## 配置说明

主配置文件：`src/main/resources/application.yml`

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

对外契约地址：

- `GET /openapi.html`
- `GET /openapi.yml`
- `GET /ts.zip`

---

## 启动与测试

### 1) 启动依赖

```powershell
Set-Location "D:\programing\SmartDataInsightAgent"
docker compose up -d
```

`compose.yaml` 当前包含：

- PostgreSQL
- Redis
- MinIO

### 2) 启动应用

```powershell
Set-Location "D:\programing\SmartDataInsightAgent"
.\gradlew.bat bootRun
```

### 3) 运行测试

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
- `ExternalDataSourceSupportTest`
- `RawTextDataParserServiceTest`
- `NaturalLanguageDataExtractionServiceTest`
- `GlobalExceptionHandlerTest`
- `UserControllerTest`

---

## 当前限制

1. 主库仍为单 `spring.datasource`，Jimmer 仅操作系统主库。
2. 外部数据源用于分析查询，不参与主库事务。
3. 外部查询仅允许只读 SQL，核心目标是防止危险语句执行。
4. `CSV`、`EXCEL` 枚举存在，但未实现直连执行链路。
5. 原始文本提取与分析质量受 LLM 输出稳定性影响。
6. 任务执行为协程异步 + 阻塞式持久化，非全链路响应式架构。

---

## 相关文档

- `docs/frontend-vue3-sse-guide.md`：前端对接 SSE 与 Jimmer TS 建议
- `docs/multi-datasource.md`：多数据源接入说明
- `docs/multi-datasource-changes.md`：多数据源改动记录
