# SmartDataInsightAgent 前端接入指南

> 面向 `Vue 3 + Pinia + Vue Router + Naive UI + Tailwind + ECharts`。
>
> 本文聚焦“前端如何接入当前后端实现”，接口字段最终以 `/openapi.yml` 或 Jimmer TS 生成产物为准。

## 1. 接入原则

- 优先使用 Jimmer 生成的 TypeScript 代码，不要手写重复 DTO。
- `services/*Controller.ts` 负责发请求，store 负责业务编排，页面只做展示和交互。
- 任务进度以 SSE 驱动，任务详情接口负责最终一致。
- `/api/analysis/tasks` 是正式分析入口；`/api/analysis/execute` 只适合即时预览。
- 管理端接口与用户端接口分开组织，避免路由、鉴权和菜单逻辑耦合。

---

## 2. 推荐目录

```text
src/
  api/                         # Jimmer 生成代码
  stores/
    auth.ts
    analysis.ts
    data-source.ts
    admin-dashboard.ts
  views/
    login/
    analysis/
    data-sources/
    admin/
      dashboard/
      users/
      roles/
      data-sources/
      analysis-tasks/
  components/
    analysis/
      TaskTimeline.vue
      TaskResultCharts.vue
      SseStatusBadge.vue
    admin/
      DashboardCards.vue
      DashboardTrendChart.vue
  utils/
    sse.ts
```

---

## 3. Jimmer TS 使用约定

当前后端已经暴露：

- `services/AnalysisController.ts`
- `services/UserController.ts`
- `services/DataSourceController.ts`
- `services/AdminAnalysisTaskController.ts`
- `services/AdminDataSourceController.ts`
- `services/AdminRoleController.ts`
- `services/AdminUserController.ts`
- `services/AdminVisualizationController.ts`
- `model/static/*`
- `model/enums/*`

推荐做法：

- 请求统一走 `services/*`
- 类型统一引用 `model/static/*` 和 `model/enums/*`
- 通用错误拦截统一放在 `Executor` 或请求封装层
- 页面不要自行拼 URL 或复制后端枚举值

---

## 4. 鉴权与会话

### 基本规则

- `/api/**` 默认要求登录。
- 白名单只有登录、注册、验证码发送、验证码登录、OpenAPI、`/ts.zip`。
- 管理端接口全部要求 `admin` 角色。

### 落地建议

- 应用启动先执行 `authStore.bootstrap()`，调用 `GET /api/user/me` 恢复会话。
- 受保护接口返回 `401` 时，统一清理本地用户态并跳转 `/login`。
- 管理端路由除了登录校验，还要基于 `roles` 判断是否包含 `admin`。

### 用户资料相关注意点

- `PATCH /api/user/me` 的 DTO 包含 `username`、`password`、`email`、`avatar`、`code`。
- 但服务层当前只实际处理密码更新；前端不要假设昵称、邮箱、头像能通过这个接口成功落库。
- 头像上传必须走 `POST /api/user/avatar`，字段名固定为 `file`。
- 头像上传限制 5 MB，仅支持 `jpg/jpeg/png/webp/gif`。
- 验证码默认有效期 5 分钟，改邮箱能力当前后端未真正实现，页面可以先不开放。

---

## 5. 分析任务接入

### 推荐主流程

1. 调用 `POST /api/analysis/tasks`
2. 立即拿到 `taskId` 与初始 `status`
3. 订阅 `GET /api/analysis/tasks/{taskId}/events`
4. 收到 `task-progress` 就更新状态、时间线、SQL、错误信息
5. 低频轮询 `GET /api/analysis/tasks/{taskId}` 兜底
6. 到达 `SUCCESS` / `FAILED` 后补拉详情并停止追踪

### SSE 事件语义

服务端会发送：

- `connected`：握手成功
- `task-progress`：阶段或状态更新
- `onSuccess`：成功补充事件

当前实现特征：

- 订阅建立后会先推送一次任务快照
- 终态任务的晚到订阅者会收到快照后立刻关闭连接
- 任务进入终态后服务端会主动关闭连接
- 未登录访问 SSE 时会收到 `event: error`，`code=AUTH_NOT_LOGIN`

建议的终态判定顺序：

1. 收到 `task-progress` 且 `status=FAILED`
2. 收到 `onSuccess`
3. 连接关闭但没拿到明确终态时，立即补拉一次详情

### 同步执行接口的定位

`POST /api/analysis/execute`：

- 立即返回 `AnalysisResult`
- 内部也会创建并执行任务
- 但响应里没有 `taskId`

结论：

- 要做正式任务列表、SSE 追踪、失败重试，用 `/api/analysis/tasks`
- 要做“立刻看结果”的轻量体验，才考虑 `/api/analysis/execute`

### 重分析流程

- 接口：`POST /api/analysis/tasks/{taskId}/reanalyze`
- 仅 `FAILED` 任务允许调用
- 可选请求体：`{"query": "新的分析问题"}`
- 复用原 `taskId`
- 前端应复用原任务卡片，不要新建本地任务项
- 调用后要清空旧 SQL / 旧图表 / 旧错误展示，等待新进度覆盖

### 任务管理能力

- 任务列表：`GET /api/analysis/tasks`
- 任务详情：`GET /api/analysis/tasks/{taskId}`
- 手动重命名：`PATCH /api/analysis/tasks/{taskId}/name`
- LLM 自动命名：`POST /api/analysis/tasks/{taskId}/name/llm`
- 删除任务：`DELETE /api/analysis/tasks/{taskId}`

---

## 6. Pinia 设计建议

### `useAuthStore`

- 状态：`profile`、`roles`、`isAuthenticated`、`isReady`
- 动作：`bootstrap`、`loginByPassword`、`loginByCode`、`logout`、`uploadAvatar`、`changePassword`

### `useAnalysisStore`

- 状态：`taskList`、`taskDetailMap`、`trackingMap`
- 动作：`createTask`、`trackTask`、`stopTracking`、`fetchTaskList`、`fetchTaskDetail`、`reanalyzeTask`、`renameTask`、`renameTaskByLlm`、`deleteTask`
- 规则：SSE 更新过程态，详情接口覆盖最终态

### `useDataSourceStore`

- 状态：`list`、`detailMap`、`loading`
- 动作：`fetchList`、`fetchDetail`、`create`、`update`、`activate`、`deactivate`、`refreshSchema`

### `useAdminDashboardStore`

- 状态：`dashboard`、`loading`、`query`
- 动作：`fetchDashboard(days, recentFailureLimit)`
- 参数约束：`days` 有效范围 `1..90`，`recentFailureLimit` 有效范围 `1..20`

---

## 7. 数据源表单约定

### 用户侧接口

- `POST /api/data-sources`
- `GET /api/data-sources`
- `GET /api/data-sources/{id}`
- `PUT /api/data-sources/{id}`
- `PATCH /api/data-sources/{id}/activate`
- `PATCH /api/data-sources/{id}/deactivate`

### 表单行为

- `connectionConfig` 是 `List<Map<String, String>>`，不是普通对象。
- 推荐前端维护键值对数组，再序列化提交。
- 创建时 `schemaInfo` 可以不传。
- 更新时即使传了 `schemaInfo`，用户侧服务也会忽略它。
- 强制刷新表结构时，调用 `PUT /api/data-sources/{id}?refreshSchemaOnly=true`。
- 新创建的数据源默认启用。

### 支持的数据源类型

- 已支持：`POSTGRESQL`、`MYSQL`
- 枚举存在但未接通：`CSV`、`EXCEL`

### 常用配置示例

PostgreSQL：

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

MySQL：

```json
[
  {"host": "127.0.0.1"},
  {"port": "3306"},
  {"database": "sales"},
  {"username": "root"},
  {"password": "secret"},
  {"params": "serverTimezone=UTC"},
  {"queryTimeout": "30"}
]
```

---

## 8. 管理端接入

### 管理端接口分组

- 任务管理：`/api/admin/analysis-tasks`
- 数据源管理：`/api/admin/data-sources`
- 角色管理：`/api/admin/roles`
- 用户管理：`/api/admin/users`
- 看板：`/api/admin/visualization/dashboard`

### 管理端页面建议

1. `dashboard`：总览卡片、任务趋势、状态分布、最近失败任务
2. `analysis-tasks`：分页列表、失败任务修正、删除失败任务
3. `data-sources`：分页列表、查看归属用户、管理员代创建/更新
4. `users`：分页列表、创建、更新角色和启用状态
5. `roles`：角色 CRUD

### 看板接口说明

`GET /api/admin/visualization/dashboard?days=7&recentFailureLimit=5`

响应包含：

- `totals`
- `taskStatusDistribution`
- `dataSourceTypeDistribution`
- `taskTrend`
- `dataSourceActivity`
- `taskExecution`
- `recentFailures`

---

## 9. ECharts 渲染约定

- 后端返回 `visualizations[*].option`，前端直接喂给 ECharts。
- 页面只做容器尺寸、空数据和异常 option 兜底。
- 不要在前端二次重写后端 `dataset` / `series.encode`，否则容易和服务端策略漂移。

任务详情页建议分区：

1. 任务概览
2. 阶段时间线
3. SQL / 原始问题 / 错误信息
4. 洞察文本
5. 图表区

---

## 10. 常见坑位

- 只依赖 SSE，不做详情兜底轮询
- 重分析后新建任务卡片，导致同一任务重复显示
- 把 `/api/analysis/execute` 当正式任务流入口
- 把 `PATCH /api/user/me` 当成完整资料编辑接口
- 手写 DTO 覆盖 Jimmer 生成类型
- 创建或编辑数据源时把 `connectionConfig` 误发成普通对象
- 页面离开后不释放 SSE，导致重复订阅和内存泄漏
