# 多数据源改动记录

> 日期：2026-03-07
> 
> 目标：在**尽量保留现有代码不变**的前提下，为项目补充多数据源连接能力，让分析链路可以按 `dataSourceId` 访问外部数据库，而系统主库仍继续使用当前 `spring.datasource + Jimmer`。

## 本次改动概览

本次改动采用了**最小侵入方案**：

- 保留现有主数据源配置不变
- 保留现有 `KSqlClient` / `KRepository` / Jimmer 主库访问逻辑不变
- 仅为“分析查询”新增外部数据源连接能力
- 根据 `dataSourceId` 动态解析连接配置并执行 SQL
- 新增正式分析 API 入口，查询链路可以从 HTTP 请求直达外部数据源
- 新增按当前登录用户校验数据源归属的能力，避免越权访问
- 新增无数据源自然语言描述数据的 LLM 提取兜底，支持“文字描述数据 → 可视化”

## 改动文件清单

### 1. `build.gradle.kts`

新增 MySQL 驱动依赖：

- `runtimeOnly("com.mysql:mysql-connector-j")`

用途：

- 让外部数据源支持 MySQL 连接
- 保留现有 PostgreSQL 主库依赖不变

---

### 2. `src/main/kotlin/top/phj233/smartdatainsightagent/model/AnalysisExecuteRequest.kt`

本次新增。

改动内容：

- 新增分析执行请求 DTO
- 约束 `query` 不能为空
- `dataSourceId` 改为可选

作用：

- 作为分析接口的正式入参模型
- 在控制器层提前完成基础参数校验
- 支持无数据源时直接传原始数据或自然语言描述

---

### 3. `src/main/kotlin/top/phj233/smartdatainsightagent/controller/AnalysisController.kt`

本次新增。

改动内容：

- 新增 `POST /api/analysis/execute`
- 从当前登录态读取用户 ID
- 组装 `AnalysisRequest` 并调用 `DataAnalysisAgent`

作用：

- 补齐多数据源分析的正式 HTTP 入口
- 不再信任前端传入的 `userId`
- 让查询真正形成“请求 -> 权限 -> 解析 -> 执行”的完整链路

---

### 4. `src/main/kotlin/top/phj233/smartdatainsightagent/service/data/DataSourceService.kt`

改动内容：

- 新增 `getActiveDataSource(id: Long)`
- 新增 `getAccessibleActiveDataSource(id: Long, userId: Long)`
- 在读取数据源时校验 `active` 状态
- 在读取数据源时校验归属用户
- `getConnectionDetails(dataSourceId, userId)` 返回扁平化后的连接配置

作用：

- 统一从主库 `data_source` 表读取外部数据源元数据
- 避免对未启用数据源发起连接
- 防止用户访问不属于自己的数据源

---

### 5. `src/main/kotlin/top/phj233/smartdatainsightagent/service/data/QueryExecutorService.kt`

改动内容：

- 保留原有：`executeQuery(sql: String)`
- 新增重载：`executeQuery(sql: String, dataSourceId: Long, userId: Long? = null)`
- 增加 `validateReadOnlySql(sql)`
- 执行前统一校验只读 SQL

作用：

- 原有默认 `JdbcTemplate` 调用路径不受影响
- 新分析链路可按 `dataSourceId` 切换到外部数据源执行 SQL
- 拒绝非只读 SQL、注释绕过、多语句拼接等危险场景

---

### 6. `src/main/kotlin/top/phj233/smartdatainsightagent/service/agent/DataAnalysisAgent.kt`

改动内容：

将分析链路拆分为两类：

- 有数据源：透传 `request.dataSourceId + request.userId`
- 无数据源：先走规则解析，失败后走 LLM 结构化提取

覆盖的分析场景：

- 普通查询
- 趋势分析
- 预测
- 报告生成
- 原始文本直连分析
- 自然语言描述数据提取后分析

作用：

- 保证分析请求真正落到用户选择的数据源上执行
- 保证 SQL 生成阶段与 SQL 执行阶段使用同一份授权上下文
- 支持用户只给“描述性数据文本”时也能继续出图

---

### 7. `src/main/kotlin/top/phj233/smartdatainsightagent/service/agent/QueryParserAgent.kt`

改动内容：

- 改为使用 `getAccessibleActiveDataSource(dataSourceId, userId)`
- SQL 生成时带入当前用户上下文
- 修正 `schemaInfo` 的格式化逻辑
- 强化只读 SQL 安全校验逻辑

作用：

- 让 SQL 生成时能结合对应数据源类型和 schema 信息
- 避免原先对非空字段误用 Elvis 操作符的问题
- 防止 AI 生成非只读或危险 SQL

---

### 8. `src/main/kotlin/top/phj233/smartdatainsightagent/service/data/ExternalDataSourceSupport.kt`

本次新增。

核心能力：

- 扁平化 `connection_config`
- 根据 `DataSourceType` 构建 JDBC URL
- 解析驱动类名
- 支持字段别名，如：
  - `database` / `dbName` / `dbname` / `databaseName`
  - `url` / `jdbcUrl`
  - `driverClassName` / `driver-class-name` / `driver`

当前支持：

- `POSTGRESQL`
- `MYSQL`

当前不支持直连：

- `CSV`
- `EXCEL`

作用：

- 将数据库连接参数处理逻辑从业务代码中抽离
- 作为多数据源连接构建的公共工具层
- 现在已被 `DataSourceService` 和 `ExternalJdbcTemplateProvider` 实际引用

---

### 9. `src/main/kotlin/top/phj233/smartdatainsightagent/service/data/ExternalJdbcTemplateProvider.kt`

本次新增。

核心能力：

- 根据 `dataSourceId` 获取外部 `JdbcTemplate`
- 支持携带 `userId` 做数据源归属校验
- 根据连接配置生成 Spring `DataSource`
- 基于配置指纹缓存 `JdbcTemplate`
- 支持 `queryTimeout` 配置

作用：

- 避免每次分析查询都重复构建连接组件
- 将“外部数据源连接创建”和“业务分析执行”解耦
- 让 `ExternalDataSourceSupport` 真正进入运行时查询链路

---

### 10. `src/main/kotlin/top/phj233/smartdatainsightagent/service/data/RawTextDataParserService.kt`

本次新增。

核心能力：

- 解析 JSON 数组
- 解析 Markdown 表格
- 解析 CSV / TSV / 分隔文本
- 将结果统一转换为 `List<Map<String, Any>>`

作用：

- 让无数据源场景下的结构化文本可以直接生成可视化
- 作为自然语言提取前的首层规则解析器

---

### 11. `src/main/kotlin/top/phj233/smartdatainsightagent/service/data/NaturalLanguageDataExtractionService.kt`

本次新增。

核心能力：

- 当规则解析失败时，调用 LLM 读取描述性文本中的明确数据
- 约束 LLM 仅返回 JSON
- 清洗 Markdown 包裹的 JSON
- 将 `rows` 或顶层数组转换为 `List<Map<String, Any>>`

作用：

- 支持“不是纯数据、而是自然语言描述的数据”也能被结构化
- 让无数据源场景下的描述性文本也能进入现有可视化链路

---

### 12. `src/test/kotlin/top/phj233/smartdatainsightagent/service/data/ExternalDataSourceSupportTest.kt`

本次新增测试。

覆盖内容：

- 连接配置扁平化
- PostgreSQL JDBC URL 生成
- MySQL JDBC URL 生成
- 显式 JDBC URL 优先
- 不支持类型时报错

作用：

- 为新接入的多数据源工具层提供基础回归保障

---

### 13. `src/test/kotlin/top/phj233/smartdatainsightagent/service/data/DataSourceServiceTest.kt`

本次新增测试。

覆盖内容：

- 数据源归属用户访问通过
- 非归属用户访问被拒绝

作用：

- 为数据源访问权限校验提供回归保障

---

### 14. `src/test/kotlin/top/phj233/smartdatainsightagent/service/data/QueryExecutorServiceTest.kt`

本次新增测试。

覆盖内容：

- 允许 `SELECT`
- 允许 `WITH`
- 拒绝 `UPDATE`
- 拒绝多语句拼接

作用：

- 为只读 SQL 执行约束提供回归保障

---

### 15. `src/test/kotlin/top/phj233/smartdatainsightagent/service/data/RawTextDataParserServiceTest.kt`

本次新增测试。

覆盖内容：

- 原始文本 JSON 解析
- 原始文本 Markdown 表格解析
- 原始文本 CSV 解析
- 无法识别的原始文本拦截

作用：

- 为无数据源结构化文本输入提供回归保障

---

### 16. `src/test/kotlin/top/phj233/smartdatainsightagent/service/data/NaturalLanguageDataExtractionServiceTest.kt`

本次新增测试。

覆盖内容：

- `rows` 对象格式的 LLM 响应解析
- Markdown 包裹 JSON 的 LLM 响应解析
- 空 `rows` 响应处理

作用：

- 为自然语言描述数据提取链路提供回归保障

---

### 17. `docs/multi-datasource.md`

本次新增文档。

内容包括：

- 多数据源设计思路
- 当前实现位置
- `connection_config` 推荐结构
- PostgreSQL / MySQL 示例
- 无数据源原始文本与自然语言描述示例
- 当前能力边界说明

作用：

- 方便后续继续接入、录入数据源配置和排查问题

## 设计原则

本次实现遵循以下原则：

1. **主库不动**
   - 系统主库仍使用现有 `spring.datasource`
   - Jimmer 继续只负责主库

2. **外部数据源只服务分析链路**
   - 不把全局默认数据源替换成动态路由
   - 不影响现有用户、角色、权限、元数据等主业务逻辑

3. **最小侵入**
   - 尽量通过新增类和新增重载方法完成能力扩展
   - 避免大面积改仓库层和事务层

4. **权限前置**
   - 先校验当前登录用户是否拥有数据源访问权
   - 再进行 SQL 生成和外部查询执行

5. **多级解析**
   - 规则解析优先
   - 规则无法识别时再让 LLM 提取结构化数据

## 当前支持能力

### 已支持

- 通过 `data_source` 表存储外部数据源元信息
- 基于 `dataSourceId` 获取对应连接配置
- PostgreSQL 外部数据源连接
- MySQL 外部数据源连接
- 分析链路按数据源执行查询
- 分析 API 正式入口：`POST /api/analysis/execute`
- 基于当前登录用户的数据源归属校验
- SQL 生成与 SQL 执行双重只读约束
- 无数据源结构化文本直连分析
- 无数据源自然语言描述数据提取后可视化

### 暂未支持

- `CSV` / `EXCEL` 直连查询
- 多数据源写操作事务一致性
- 外部数据源连接健康检查接口
- 连接池化优化（当前仍是 `DriverManagerDataSource`）

## 验证结果

### 已验证

- 主代码已成功编译，新增 class 已生成到 `build/classes/kotlin/main`
- 新增的多数据源工具层单元测试通过
- `DataAnalysisAgent` 中 4 个分析流程都已切换到按 `dataSourceId + userId` 执行
- `ExternalDataSourceSupport` 已被实际接入运行时查询链路
- `RawTextDataParserServiceTest` 4 项通过
- `NaturalLanguageDataExtractionServiceTest` 3 项通过（`failures=0 errors=0`）

### 发现的现有项目问题

完整测试流程中存在**非本次改动引入**的问题：

- `processTestAot` 失败
- 相关异常：`ClassNotFoundException: tools.jackson.databind.JavaType`

说明：

- 该问题出现在项目现有测试 AOT 处理阶段
- 不属于本次多数据源接入逻辑本身的问题
- 在跳过该步骤后，新增多数据源相关验证可正常通过

## 后续建议

推荐下一步继续补充以下能力：

1. **数据源连通性测试接口**
   - 保存或启用前先测试连接是否可达

2. **连接池升级**
   - 当前使用 `DriverManagerDataSource`
   - 若后续连接量增长，建议升级为池化实现

3. **CSV / EXCEL 数据源支持**
   - 通过文件解析方式纳入分析链路

4. **更细粒度的数据源授权**
   - 如管理员跨用户查看、共享数据源等场景

5. **自然语言数据描述提示优化**
   - 对同一句子中的多指标、多时间维度做更稳定的抽取模板

## 结论

本次改动已经完成了一个可工作的多数据源查询基础版本：

- 不破坏现有主库访问模式
- 不改变现有 Jimmer 主业务代码结构
- 新增外部数据库动态连接能力
- 新增正式分析 API 入口
- 新增数据源归属权限校验
- 新增无数据源结构化文本直连分析
- 新增无数据源自然语言描述数据提取后可视化
- 分析请求已经可以根据 `dataSourceId` 执行到不同数据库，或在无数据源时直接生成图表

适合作为后续“数据源管理、连接测试、权限隔离、更多类型支持”的基础版本。
