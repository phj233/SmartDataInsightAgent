# SmartDataInsightAgent

一个基于 **Spring Boot 3 + Kotlin + Jimmer + Spring AI** 的智能数据分析服务。

它的目标是把“自然语言问题 / 原始数据文本 / 描述性数据文本”转换为：

- 结构化分析结果
- AI 洞察文本
- 面向前端的 **ECharts 配置**
- 可追踪的分析任务记录

当前项目已经支持：

- 登录用户发起分析任务
- 按任务阶段持久化分析过程
- 按 `dataSourceId` 访问外部数据库进行分析
- 无数据源时，直接解析 JSON / Markdown 表格 / CSV / TSV 文本
- 对“自然语言描述的数据”进行 LLM 结构化提取并尝试生成可视化
- 查询当前用户的分析任务列表和任务详情

---

## 目录

- [项目特性](#项目特性)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [运行环境](#运行环境)
- [配置说明](#配置说明)
- [启动方式](#启动方式)
- [分析链路说明](#分析链路说明)
- [多数据源说明](#多数据源说明)
- [测试](#测试)
- [当前限制](#当前限制)
- [相关文档](#相关文档)

---

## 项目特性

### 1. 自然语言分析
用户可以直接输入自然语言，例如：

- “查询最近 30 天销售趋势”
- “预测未来三个月销售走势”
- “生成一份销售分析报告”

系统会先识别意图，再进入对应分析流程。

### 2. 多数据源分析
当请求中提供 `dataSourceId` 时，系统会：

- 校验该数据源是否启用
- 校验该数据源是否属于当前用户
- 读取主库中的数据源元数据
- 动态连接外部数据库执行只读 SQL

当前外部数据库直连能力已实现：

- `POSTGRESQL`
- `MYSQL`

### 3. 无数据源原始文本分析
当不传 `dataSourceId` 时，系统会优先将 `query` 视为原始数据文本进行解析。

当前支持：

- JSON 数组
- Markdown 表格
- CSV
- TSV

### 4. 描述性数据文本提取
如果输入不是纯结构化数据，而是类似：

> 今年一季度华北、华东、华南的销售额分别是 120 万、150 万、110 万，利润分别是 18 万、26 万、15 万。

系统会在规则解析失败后，调用 LLM 提取结构化数据，再继续生成分析结果和可视化。

### 5. 分析任务持久化
每次分析都会创建 `AnalysisTask`，并记录阶段轨迹，例如：

- `TASK_CREATED`
- `INTENT_ANALYZING`
- `SQL_GENERATING`
- `QUERY_EXECUTING`
- `INSIGHTS_GENERATING`
- `VISUALIZATION_GENERATING`
- `COMPLETED`

同时保存：

- 当前任务状态（`PENDING / RUNNING / SUCCESS / FAILED`）
- 生成 SQL
- 最终结果
- 执行耗时
- 错误信息

### 6. ECharts 可视化输出
分析结果会结合 `VisualizationAgent` 输出前端可直接消费的结构化图表配置：

- 图表类型
- 图表标题与说明
- 完整 `option`
- `dataset.source`

---

## 技术栈

### 后端框架
- Spring Boot `3.5.9`
- Kotlin `2.1.0`
- Spring Validation
- Spring Mail
- Spring Data Redis

### AI 与数据分析
- Spring AI `1.1.2`
- DeepSeek Chat Model

### 数据访问
- Jimmer `0.9.120`
- PostgreSQL Driver
- MySQL Driver
- JdbcTemplate（外部数据源执行）

### 认证与会话
- Sa-Token `1.44.0`
- Redis 会话/验证码支持

### 构建与生成
- Gradle Kotlin DSL
- KSP
- Jimmer DTO 生成

---

## 项目结构

```text
src/main/
├─ dto/
│  ├─ AnalysisTask.dto
│  └─ User.dto
├─ kotlin/top/phj233/smartdatainsightagent/
│  ├─ config/                  # Sa-Token / CORS 等配置
│  ├─ controller/              # HTTP 接口
│  ├─ entity/                  # Jimmer 实体
│  ├─ exception/               # Jimmer 业务异常族
│  ├─ interceptor/             # BaseEntity 审计字段拦截器
│  ├─ model/                   # 请求/结果/值对象
│  ├─ repository/              # Jimmer Repository
│  ├─ service/
│  │  ├─ agent/                # 分析/SQL/可视化 Agent
│  │  ├─ ai/                   # DeepSeek 封装
│  │  └─ data/                 # 多数据源、原始文本解析、SQL 执行
│  └─ util/
└─ resources/
   ├─ application.yml
   ├─ application-secrets.yml
   └─ templates/mail.html
```

---

## 运行环境

建议环境：

- JDK 21
- Gradle Wrapper（项目已提供 `gradlew.bat`）
- PostgreSQL（主库）
- Redis

当前项目中提供了一个基础 `compose.yaml`，可用于快速启动本地 PostgreSQL 和 Redis。

---

## 配置说明

### 1. 主配置文件
主配置位于：

- `src/main/resources/application.yml`

其中通过：

```yaml
spring:
  config:
    import: optional:classpath:application-secrets.yml
```

引入敏感配置文件。

### 2. 敏感配置文件
敏感配置位于：

- `src/main/resources/application-secrets.yml`

当前主配置依赖以下键：

- `DEEPSEEK_API_KEY`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`

### 3. 邮件配置
邮件服务当前使用：

- SMTP Host: `smtp.office365.com`
- Port: `587`

主要用于验证码邮件发送。

---

## 启动方式

### 方式一：先启动依赖，再本地运行

```powershell
Set-Location "D:\programing\SmartDataInsightAgent"
docker compose up -d
.\gradlew.bat bootRun
```

### 方式二：运行测试验证项目基础可用性

```powershell
Set-Location "D:\programing\SmartDataInsightAgent"
.\gradlew.bat test -x processTestAot --no-daemon
```

> 说明：当前项目测试链路中存在 `processTestAot` 相关因素，因此日常定向验证时常使用 `-x processTestAot`。

---

> 当前 README 重点说明项目能力、运行方式和实现边界；接口清单建议在功能完整后统一整理。

---

## 分析链路说明

### 有 `dataSourceId` 时
1. 从当前登录态读取 `userId`
2. 校验数据源是否属于当前用户且已启用
3. 识别用户意图：
   - `DATA_QUERY`
   - `TREND_ANALYSIS`
   - `PREDICTION`
   - `REPORT_GENERATION`
4. 基于数据源 schema 生成只读 SQL
5. 执行查询
6. 生成 AI 洞察
7. 生成 ECharts 配置
8. 保存 `AnalysisTask`

### 无 `dataSourceId` 时
1. 优先按结构化文本解析输入
2. 如果解析失败，则调用 LLM 提取结构化数据
3. 生成洞察
4. 生成可视化
5. 保存 `AnalysisTask`

---

## 多数据源说明

### 当前支持的外部数据源类型
- `POSTGRESQL`
- `MYSQL`

### 当前实体定义中存在但尚未实现直连的类型
- `CSV`
- `EXCEL`

### `connection_config` 建议结构
项目中的 `data_source.connection_config` 当前是：

- `List<Map<String, String>>`

推荐按如下结构存储：

#### PostgreSQL

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

#### MySQL

```json
[
  {"host": "127.0.0.1"},
  {"port": "3306"},
  {"database": "sales"},
  {"username": "root"},
  {"password": "secret"}
]
```

也支持直接提供 JDBC URL。

---

## 测试

当前项目中已经有针对以下能力的测试：

- `DataAnalysisAgent` 分析任务与阶段记录
- `VisualizationAgent` 的 ECharts 配置生成
- `QueryExecutorService` 的只读 SQL 校验
- `NaturalLanguageDataExtractionService` 的 LLM JSON 解析
- `AnalysisTaskService` 的任务状态与查询逻辑
- Spring Boot 基础上下文加载

常用定向测试命令：

```powershell
Set-Location "D:\programing\SmartDataInsightAgent"
.\gradlew.bat test --tests top.phj233.smartdatainsightagent.service.agent.DataAnalysisAgentTest --tests top.phj233.smartdatainsightagent.service.agent.VisualizationAgentTest -x processTestAot --no-daemon
```

---

## 当前限制

以下内容基于当前源码可确认的实现状态：

1. **主库仍是单一 Spring DataSource**
   - Jimmer 与系统实体仍使用 `spring.datasource`
   - 外部数据源仅用于分析查询执行

2. **外部查询只允许只读 SQL**
   - 仅允许 `SELECT` / `WITH`
   - 禁止危险 SQL、注释绕过、多语句拼接等场景

3. **文件型数据源未实现直连查询**
   - `DataSourceType` 中定义了 `CSV` / `EXCEL`
   - 但当前分析链路里尚未实现这两类作为外部数据源直连执行

4. **自然语言数据提取效果依赖 LLM**
   - 如果用户文本里没有明确可提取的数据，可能无法生成有效可视化

5. **分析任务持久化是同步数据库写入**
   - 当前已在 `DataAnalysisAgent` 内将任务写入切到 `Dispatchers.IO`
   - 但整体仍基于阻塞式持久化模型，不是响应式链路

---

## 相关文档

项目内已有文档：

- `docs/multi-datasource.md`：多数据源能力说明
- `docs/multi-datasource-changes.md`：多数据源改动记录

---

## 说明

本文档只描述当前代码中已经存在且可从源码/测试中确认的功能；如果你后续继续扩展，例如：

- 数据源管理接口
- 任务分页查询
- OpenAPI 文档
- CSV / Excel 直连数据源
- 前端对接示例

建议同步更新 `README.md`。
