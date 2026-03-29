# SmartDataInsightAgent

一个基于 **Spring Boot 3 + Kotlin + Jimmer + Spring AI** 的智能数据分析服务。

项目目标：把自然语言分析请求转成可追踪、可回放、可视化的分析任务结果。

## 当前能力概览

- 登录用户发起分析任务，支持同步执行与异步任务模式。
- 异步模式支持 SSE 进度推送：先返回 `taskId`，再订阅实时状态。
- 可按 `dataSourceId` 连接外部数据库进行只读分析（`POSTGRESQL`、`MYSQL`）。
- 无数据源时可直接解析 JSON/Markdown 表格/CSV/TSV，失败后回退 LLM 提取。
- 任务全程持久化阶段轨迹、状态、SQL、结果、耗时、错误信息。
- 输出 AI 洞察文本和前端可直接渲染的 ECharts `option`。
- 用户侧已提供 `me`、`logout`、`avatar` 上传能力。
- Sa-Token 未认证场景已有全局异常处理，普通接口返回 401 JSON，SSE 返回 `event: error`。

---

## 目录

- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [核心流程](#核心流程)
- [认证与异常处理](#认证与异常处理)
- [多数据源说明](#多数据源说明)
- [配置说明](#配置说明)
- [启动与测试](#启动与测试)
- [当前限制](#当前限制)
- [相关文档](#相关文档)

---

## 技术栈

### 后端基础

- Spring Boot `3.5.9`
- Kotlin `2.1.0`
- Spring Validation / Mail / Redis

### 数据与 ORM

- Jimmer `0.10.6`
- PostgreSQL Driver
- MySQL Driver
- JdbcTemplate（外部数据源查询）

### AI 与分析

- Spring AI `1.1.2`
- DeepSeek Chat Model

### 认证与会话

- Sa-Token `1.44.0`
- Redis（会话、验证码）

### 对外契约生成

- Jimmer OpenAPI（`/openapi.yml`, `/openapi.html`）
- Jimmer TypeScript Zip（`/ts.zip`）

### 对象存储

- MinIO（头像上传，按文件内容哈希复用链接）

---

## 项目结构

```text
src/main/
├─ dto/
│  ├─ AnalysisTask.dto
│  ├─ DataSource.dto
│  └─ User.dto
├─ kotlin/top/phj233/smartdatainsightagent/
│  ├─ config/                  # Sa-Token/CORS/MinIO/MDC
│  ├─ controller/              # Analysis/User/DataSource API
│  ├─ entity/                  # Jimmer 实体与枚举
│  ├─ exception/               # 业务异常 + 全局异常处理
│  ├─ interceptor/             # 审计字段拦截
│  ├─ model/                   # 分析请求/结果/进度事件
│  ├─ repository/              # Jimmer Repository
│  ├─ service/
│  │  ├─ agent/                # 分析/SQL/可视化 Agent
│  │  ├─ ai/                   # DeepSeek 封装
│  │  ├─ data/                 # 多数据源/解析/执行
│  │  └─ storage/              # MinIO 服务
│  └─ util/
└─ resources/
   ├─ application.yml
   ├─ application-secrets.yml
   ├─ db/schema/
   └─ templates/mail.html
```

---

## 核心流程

### 1) 分析任务主流程（异步，推荐）

后端实现路径：`AnalysisController.createTask` -> `AnalysisTaskService.createTask` -> `AnalysisExecutionService.submit` -> `DataAnalysisAgent.analyzeDataForTask`。

完整时序如下：

1. 前端调用 `POST /api/analysis/tasks`，提交 `query` 与可选 `dataSourceId`。
2. 后端先做请求归一化（`query.trim()`），并绑定当前登录 `userId`。
3. 创建任务记录：
   - 写入 `AnalysisTask`（初始 `status=PENDING`）
   - 追加首条阶段 `TASK_CREATED`
   - 任务名默认取 `query` 前 20 字符
4. 接口立即返回 `taskId + status`（不会等待 AI/SQL 执行完成）。
5. 后端将任务提交到协程后台执行器（`SupervisorJob + Dispatchers.Default`）。
6. 执行过程中每到关键节点，任务服务会：
   - 持久化最新阶段轨迹（`parameters`）
   - 刷新任务状态（`RUNNING/SUCCESS/FAILED`）
   - 通过 SSE 通知器推送 `task-progress`
7. 成功分支：写入 `result`、`generatedSql`、`executionTime`，状态转为 `SUCCESS`。
8. 失败分支：记录 `errorMessage` 与失败阶段，状态转为 `FAILED`。
9. SSE 在任务到终态（`SUCCESS/FAILED`）后由服务端主动关闭当前任务的订阅连接。

### 2) 任务状态与阶段语义

任务状态机：

- `PENDING`：任务已创建，等待后台执行。
- `RUNNING`：分析链路进行中。
- `SUCCESS`：分析完成，结果可读取。
- `FAILED`：分析失败，可读取失败阶段和错误信息。

常见阶段（按出现频率）：

- 通用：`TASK_CREATED`、`INSIGHTS_GENERATING`、`VISUALIZATION_GENERATING`、`COMPLETED`
- 数据源路径：`INTENT_ANALYZING`、`INTENT_RESOLVED`、`SQL_GENERATING`、`SQL_GENERATED`、`QUERY_EXECUTING`、`QUERY_EXECUTED`
- 原始文本路径：`RAW_TEXT_PARSING`、`NATURAL_LANGUAGE_EXTRACTION`
- 特殊意图：`PREDICTION_GENERATING`、`REPORT_GENERATING`

### 3) SSE 订阅与事件语义

订阅端点：`GET /api/analysis/tasks/{taskId}/events`（`text/event-stream`）。

事件类型：

- `connected`：握手成功，表示订阅建立。
- `task-progress`：任务进度事件，包含阶段与状态变化。

`task-progress` 关键字段：

- `taskId`：任务 ID
- `status`：当前任务状态
- `stage`：当前阶段名
- `timestamp`：事件时间
- `details`：阶段上下文信息（如 `rowCount`、`intentType`）
- `generatedSql`：有 SQL 时返回
- `errorMessage`：失败时返回

未登录访问 SSE 时，统一异常处理会返回 `event: error`（401 语义）。

### 4) 前端推荐编排（SSE + 详情兜底）

1. 调用创建任务接口拿 `taskId`。
2. 立即建立 SSE 订阅，并监听 `connected/task-progress`。
3. 同时以低频率轮询 `GET /api/analysis/tasks/{taskId}` 兜底，防止断线漏事件。
4. 收到终态后主动停止 SSE 与轮询。
5. 再拉取一次详情作为最终一致结果并渲染。

### 5) 数据输入模式与意图

- **有 `dataSourceId`**：先意图识别，再走 SQL 生成与查询流程。
- **无 `dataSourceId`**：先规则解析原始文本，失败后回退 LLM 抽取结构化数据。

支持意图：

- `DATA_QUERY`
- `TREND_ANALYSIS`
- `PREDICTION`
- `REPORT_GENERATION`

### 6) 同步执行接口（补充）

项目仍保留 `POST /api/analysis/execute` 同步执行入口：

- 请求线程等待完整分析结果返回。
- 内部也会创建并更新任务记录。
- 联调和生产场景建议优先使用异步任务 + SSE 模式。

---

## 认证与异常处理

### 认证策略

- `/api/**` 默认要求登录。
- 白名单：`/api/user/login`、`/api/user/register`、`/api/user/sendCode`、`/api/user/loginByCode`、`/openapi.html`、`/openapi.yml`、`/ts.zip`、`/error`。
- 预检请求（`OPTIONS`）放行。

### 用户相关能力

- `GET /api/user/me`：获取当前登录用户。
- `POST /api/user/logout`：注销当前会话。
- `POST /api/user/avatar`：上传头像（`multipart/form-data`, 字段名 `file`）。

### 全局未认证异常

- 捕获 `NotLoginException`。
- 普通 HTTP 返回 401 JSON（`code=AUTH_NOT_LOGIN`）。
- SSE 请求返回 `text/event-stream` 的 `event: error`，避免前端把未认证误判为网络异常。

---

## 多数据源说明

### 已支持直连类型

- `POSTGRESQL`
- `MYSQL`

### 实体枚举中存在但当前未支持直连

- `CSV`
- `EXCEL`

### `connectionConfig` 结构

项目使用 `List<Map<String, String>>` 存储连接信息，运行时会扁平化为键值对。

示例（PostgreSQL）：

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

也可通过 `url/jdbcUrl` 直接提供 JDBC 地址。

### Schema 探测策略

- 创建数据源成功后，若 `schemaInfo` 为空，会自动探测并回填。
- 更新数据源时会忽略请求体中的 `schemaInfo`，避免误覆盖。
- 可通过 `refreshSchemaOnly=true` 触发强制刷新。

---

## 配置说明

主配置：`src/main/resources/application.yml`

通过 `spring.config.import` 引入：`application-secrets.yml`。

建议最少准备以下环境变量：

- `DEEPSEEK_API_KEY`
- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
- `REDIS_HOST`
- `MAIL_USERNAME` / `MAIL_PASSWORD`
- `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET` / `MINIO_PUBLIC_BASE_URL`

默认开放的契约地址：

- `GET /openapi.html`
- `GET /openapi.yml`
- `GET /ts.zip`

---

## 启动与测试

### 启动依赖并运行

```powershell
Set-Location "D:\programing\SmartDataInsightAgent"
docker compose up -d
.\gradlew.bat bootRun
```

> `compose.yaml` 当前包含 PostgreSQL、Redis、MinIO。

### 运行测试

```powershell
Set-Location "D:\programing\SmartDataInsightAgent"
.\gradlew.bat test -x processTestAot --no-daemon
```

常见定向测试包括：

- `DataAnalysisAgentTest`
- `VisualizationAgentTest`
- `AnalysisTaskServiceTest`
- `DataSourceControllerTest`
- `GlobalExceptionHandlerTest`

---

## 当前限制

1. 主库仍是单一 `spring.datasource`，外部数据源仅用于分析查询。
2. 外部查询仅允许只读 SQL（`SELECT` / `WITH`），禁止危险语句。
3. `CSV/EXCEL` 枚举已存在，但未实现外部直连查询链路。
4. 原始文本结构化与洞察质量受 LLM 输出稳定性影响。
5. 任务执行使用协程异步 + 阻塞式持久化，不是响应式全链路。

---

## 相关文档

- `docs/frontend-vue3-sse-guide.md`：面向 Vue3 + Pinia + Naive UI + Tailwind + Vue Router + ECharts 的前端接入指南（Jimmer TS 优先）。
- `docs/multi-datasource.md`：多数据源能力说明。
- `docs/multi-datasource-changes.md`：多数据源改动记录。

