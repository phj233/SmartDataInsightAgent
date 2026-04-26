# SmartDataInsightAgent

基于 `Spring Boot 3 + Kotlin + Jimmer + Spring AI + Sa-Token` 的智能数据分析后端。

项目目标是把用户自然语言分析请求转换为 **可追踪、可回放、可订阅** 的分析任务，并支持两种输入模式：

- **外部数据源分析**：基于 `dataSourceId` 连接用户自己的 PostgreSQL / MySQL，生成只读 SQL 并执行
- **原始文本分析**：直接提交 JSON、Markdown 表格、CSV、TSV 或自然语言数据描述，自动提取结构化数据并生成洞察与图表建议

---

## 核心能力

### 1. 分析任务

- 异步任务创建：`POST /api/analysis/tasks`
- 失败任务原地重试：`POST /api/analysis/tasks/{taskId}/reanalyze`
- 任务 SSE 订阅：`GET /api/analysis/tasks/{taskId}/events`
- 同步执行：`POST /api/analysis/execute`
- 任务列表、详情、手动重命名、LLM 自动命名、删除
- 任务阶段轨迹持久化，结果包含洞察文本、ECharts `option`、可选 SQL

说明：

- `POST /api/analysis/tasks` 是推荐入口，适合可回放、可订阅、可重试的正式分析流程。
- `POST /api/analysis/execute` 会同步返回 `AnalysisResult`，但内部同样会创建并执行一条任务记录；它不返回 `taskId`，因此更适合即时预览，不适合前端做任务追踪。

### 2. 两种输入模式

- 外部数据源分析：按 `dataSourceId` 连接用户自己的 PostgreSQL / MySQL，生成只读 SQL 并执行
- 原始文本分析：支持 JSON 数组、Markdown 表格、CSV、TSV、代码块中的结构化文本
- 自然语言提取兜底：当文本不是表格时，调用 LLM 只提取用户明确给出的数据，再生成洞察与图表

### 3. 数据源管理

- 用户数据源 CRUD：`/api/data-sources/**`
- 启用 / 停用：`PATCH /api/data-sources/{id}/activate|deactivate`
- JDBC 元数据自动探测 `schemaInfo`
- `PUT /api/data-sources/{id}?refreshSchemaOnly=true` 强制刷新结构
- 只允许访问“属于当前用户且处于激活状态”的外部数据源

### 4. 用户与认证

- 密码登录：`POST /api/user/login`
- 邮箱验证码发送 / 注册 / 验证码登录：`sendCode`、`register`、`loginByCode`
- 当前用户信息：`GET /api/user/me`
- 当前用户资料更新：`PATCH /api/user/me`
- 头像上传：`POST /api/user/avatar`
- 登出：`POST /api/user/logout`

当前实现说明：

- `PATCH /api/user/me` 的 DTO 已包含 `username`、`password`、`email`、`avatar`、`code`，但服务层当前只实际处理密码更新。
- 头像上传走独立接口，限制 5 MB，支持 `jpg/jpeg/png/webp/gif`。
- 邮箱验证码默认有效期 5 分钟。

### 5. 管理员能力

管理员接口均要求 `admin` 角色：

- 分析任务管理：`/api/admin/analysis-tasks`
- 数据源管理：`/api/admin/data-sources`
- 角色管理：`/api/admin/roles`
- 用户管理：`/api/admin/users`
- 可视化看板：`GET /api/admin/visualization/dashboard`

看板返回：

- 用户 / 数据源 / 任务总览卡片
- 任务状态分布
- 数据源类型分布
- 近 N 天任务趋势
- 任务执行耗时统计
- 最近失败任务列表

---

## 技术栈

### 后端基础

- Spring Boot `3.5.9`
- Kotlin `2.1.0`
- Java `21`
- Kotlin Coroutines

### 数据与存储

- Jimmer `0.10.6`
- PostgreSQL（系统主库）
- MySQL 驱动（外部分析数据源）
- Redis（验证码、Sa-Token 持久化）
- MinIO（存储）

### AI 能力

- Spring AI `1.1.2`
- DeepSeek Chat Model

### 认证与接口契约

- Sa-Token `1.44.0`
- Jimmer OpenAPI：`/openapi.yml`、`/openapi.html`
- Jimmer TypeScript SDK：`/ts.zip`

### 交付与部署

- `compose.yaml`：本地 PostgreSQL / Redis / MinIO 依赖
- `Dockerfile`：多阶段构建 Spring Boot 镜像
- `.cnb.yml`：CNB 构建、推送镜像、SSH 部署远端容器

---

## 项目结构

```text
src/main/
├─ dto/                                # Jimmer DTO 定义
├─ kotlin/top/phj233/smartdatainsightagent/
│  ├─ config/                          # Sa-Token / Jimmer / MinIO / Trace
│  ├─ controller/                      # 用户侧 API
│  │  └─ admin/                        # 管理员 API
│  ├─ entity/                          # Jimmer 实体与枚举
│  ├─ exception/                       # 业务异常与全局异常处理
│  ├─ interceptor/                     # BaseEntity 审计字段拦截
│  ├─ model/                           # 请求、结果、阶段事件、看板模型
│  ├─ repository/                      # Jimmer Repository
│  ├─ service/
│  │  ├─ admin/                        # 管理端服务
│  │  ├─ agent/                        # Query / Analysis / Visualization Agent
│  │  ├─ ai/                           # DeepSeek 封装
│  │  ├─ data/                         # 外部数据源、Schema 探测、SQL 执行、文本解析
│  │  └─ storage/                      # MinIO 服务
│  └─ util/                            # MDC 等工具
└─ resources/
   ├─ application.yml
   ├─ application-secrets.yml
   ├─ db/schema/
   └─ templates/mail.html
```

---

## API 概览

### 分析任务

- `POST /api/analysis/tasks`
- `POST /api/analysis/tasks/{taskId}/reanalyze`
- `GET /api/analysis/tasks/{taskId}/events`
- `POST /api/analysis/execute`
- `GET /api/analysis/tasks`
- `GET /api/analysis/tasks/{taskId}`
- `PATCH /api/analysis/tasks/{taskId}/name`
- `POST /api/analysis/tasks/{taskId}/name/llm`
- `DELETE /api/analysis/tasks/{taskId}`

### 用户

- `POST /api/user/sendCode`
- `POST /api/user/register`
- `POST /api/user/login`
- `POST /api/user/loginByCode`
- `GET /api/user/me`
- `PATCH /api/user/me`
- `POST /api/user/avatar`
- `POST /api/user/logout`

### 用户数据源

- `POST /api/data-sources`
- `GET /api/data-sources`
- `GET /api/data-sources/{id}`
- `PUT /api/data-sources/{id}`
- `PATCH /api/data-sources/{id}/activate`
- `PATCH /api/data-sources/{id}/deactivate`

### 管理员

- `GET /api/admin/analysis-tasks`
- `GET /api/admin/analysis-tasks/{id}`
- `PATCH /api/admin/analysis-tasks/{id}`
- `DELETE /api/admin/analysis-tasks/{id}`
- `GET /api/admin/data-sources`
- `GET /api/admin/data-sources/{id}`
- `POST /api/admin/data-sources`
- `PUT /api/admin/data-sources/{id}`
- `DELETE /api/admin/data-sources/{id}`
- `GET /api/admin/roles`
- `GET /api/admin/roles/{id}`
- `POST /api/admin/roles`
- `PUT /api/admin/roles/{id}`
- `DELETE /api/admin/roles/{id}`
- `GET /api/admin/users`
- `GET /api/admin/users/{id}`
- `POST /api/admin/users`
- `PUT /api/admin/users/{id}`
- `DELETE /api/admin/users/{id}`
- `GET /api/admin/visualization/dashboard`

---

## 分析任务流程

### 1. 异步任务主流程

调用链：

`AnalysisController.createTask`
-> `AnalysisTaskService.createTask`
-> `AnalysisExecutionService.submit`
-> `DataAnalysisAgent.analyzeDataForTask`

关键阶段：

- `TASK_CREATED`
- `INTENT_ANALYZING`
- `INTENT_RESOLVED`
- `SQL_GENERATING`
- `SQL_GENERATED`
- `QUERY_EXECUTING`
- `QUERY_EXECUTED`
- `RAW_TEXT_PARSING`
- `NATURAL_LANGUAGE_EXTRACTION`
- `INSIGHTS_GENERATING`
- `VISUALIZATION_GENERATING`
- `PREDICTION_GENERATING`
- `REPORT_GENERATING`
- `REANALYZE_REQUESTED`
- `ASYNC_EXECUTION_FAILED`
- `COMPLETED`

任务状态：

- `PENDING`
- `RUNNING`
- `SUCCESS`
- `FAILED`

### 2. SSE 语义

订阅端点：`GET /api/analysis/tasks/{taskId}/events`

事件类型：

- `connected`：连接建立
- `task-progress`：阶段或状态更新
- `onSuccess`：成功补充事件

当前实现特征：

- 订阅成功后会立即推送一次当前任务快照
- 终态任务的晚到订阅者会收到快照后直接关闭连接
- 任务进入 `SUCCESS` 或 `FAILED` 后，服务端主动关闭该任务的订阅连接
- 未登录访问 SSE 时返回 `event: error`，错误码 `AUTH_NOT_LOGIN`

### 3. 失败任务原地重试

- 仅 `FAILED` 任务允许重试
- 复用原 `taskId`
- 请求体可选：`{"query": "新的分析问题"}`
- 若传新 `query`，会清空旧 SQL / 结果 / 错误，并在必要时刷新默认任务名

### 4. 分析分支

#### 带 `dataSourceId`

1. LLM 识别意图：`DATA_QUERY` / `TREND_ANALYSIS` / `PREDICTION` / `REPORT_GENERATION`
2. `QueryParserAgent` 基于 Schema 生成只读 SQL
3. `QueryExecutorService` 校验 SQL 并执行
4. 生成洞察与 1-2 个图表建议

#### 不带 `dataSourceId`

1. `RawTextDataParserService` 优先解析 JSON / Markdown / CSV / TSV / 代码块
2. 规则解析失败时进入 `NaturalLanguageDataExtractionService`
3. LLM 仅提取用户明确给出的结构化数据
4. 生成洞察与图表建议

---

## 数据源说明

### 当前支持

- `POSTGRESQL`
- `MYSQL`

枚举存在但未支持直连执行：

- `CSV`
- `EXCEL`

### `connectionConfig` 结构

实体字段类型为 `List<Map<String, String>>`，服务层会扁平化为普通键值对。

PostgreSQL 示例：

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

MySQL 示例：

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

也可以直接传 JDBC URL：

```json
[
  {"url": "jdbc:postgresql://127.0.0.1:5432/analytics?currentSchema=public"},
  {"username": "postgres"},
  {"password": "secret"}
]
```

支持的别名键：

- 数据库名：`database` / `dbName` / `dbname` / `databaseName`
- JDBC URL：`url` / `jdbcUrl`
- 驱动：`driverClassName` / `driver-class-name` / `driver`
- PostgreSQL schema：`schema` / `currentSchema`

### Schema 探测

- 创建数据源时，若 `schemaInfo` 为空则自动 JDBC 探测并回填
- 用户更新数据源时，服务层会忽略请求体中的 `schemaInfo`
- `refreshSchemaOnly=true` 时会强制刷新 `schemaInfo`
- 探测失败不会阻断保存，但 `schemaInfo` 会保持空数组

### SQL 安全约束

外部查询仅允许只读 SQL：

- 必须以 `SELECT` 或 `WITH` 开头
- 拦截 `INSERT/UPDATE/DELETE/DROP/...`
- 拦截注释注入与多语句拼接：`--`、`/*`、`; ...`

---

## 运行配置

主配置文件：`src/main/resources/application.yml`

敏感配置通过：

`spring.config.import=optional:classpath:application-secrets.yml`

导入，运行时至少需要准备以下变量：

- `DEEPSEEK_API_KEY`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PASSWORD`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MINIO_ENDPOINT`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `MINIO_BUCKET`

认证与访问控制：

- `/api/**` 默认要求登录
- 白名单：`/api/user/login`、`/api/user/register`、`/api/user/sendCode`、`/api/user/loginByCode`、`/openapi.html`、`/openapi.yml`、`/ts.zip`、`/error`
- 管理端接口使用 `@SaCheckRole("admin")`

---

## 本地启动

### 1. 启动依赖服务

```powershell
Set-Location "D:\programing\SmartDataInsightAgent"
docker compose up -d
```

`compose.yaml` 默认包含：

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

### 4. 构建镜像

```powershell
Set-Location "D:\programing\SmartDataInsightAgent"
docker build -t smart-data-insight-agent:local .
```

### 5. CNB 流水线

`.cnb.yml` 当前流程会：

1. 通过 CNB 制品库构建并推送 Docker 镜像
2. 从 CNB 密钥仓库导入运行时环境变量
3. 通过 SSH 登录远端主机，拉取最新镜像并重建容器

---

## 当前限制

1. 系统主库仍是单 `spring.datasource`，Jimmer 只操作主库。
2. 外部数据源仅用于分析查询，不参与主库事务。
3. 外部连接使用 `DriverManagerDataSource` + 指纹缓存，当前不是连接池方案。
4. `CSV`、`EXCEL` 枚举尚未接入直连执行链路。
5. `/api/analysis/execute` 会创建任务但不返回 `taskId`，因此不适合作为前端正式任务流入口。
6. `PATCH /api/user/me` 当前仅实现密码更新，资料编辑能力尚未补齐。
7. 预测、自然语言提取和可视化推荐依赖 LLM 输出稳定性。

---

## 相关文档

- `docs/frontend-vue3-sse-guide.md`：Vue3 / Jimmer TS / SSE 对接指南
- `docs/multi-datasource.md`：多数据源、任务入口与原始文本分析说明
- `docs/multi-datasource-changes.md`：多数据源与任务化分析演进记录
