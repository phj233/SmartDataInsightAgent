# SmartDataInsightAgent 前端 AI 开发指南（Jimmer TS 优先）

> 面向 Vue3 + Pinia + Naive UI + Tailwind + Vue Router + ECharts。
>
> 本文是“前端实现指南”，不是接口文档。接口参数/响应细节以 Apifox 为准。

## 1. 开发原则（给前端 AI）

- 优先使用 Jimmer 生成的 TypeScript 代码（`src/api/` 目录），不要手写重复 DTO 类型。
- `services/*Controller.ts` 负责发请求；业务层只做组合，不直接拼 URL。
- SSE 是任务进度“实时通道”；任务详情接口是“最终一致来源”。
- 遇到字段不确定时，先从 `model/static/*` 与 `model/enums/*` 取类型，再补 UI 逻辑。

---

## 2. Jimmer TS 产物使用约定

当前你已有生成产物（示例）：

- `Api.ts`
- `Executor.ts`
- `ApiErrors.ts`
- `services/AnalysisController.ts`、`services/UserController.ts`、`services/DataSourceController.ts`
- `model/static/*`（如 `AnalysisTaskCreateResponse.ts`、`AnalysisTaskDetailView.ts`、`UserMeResponse.ts`）
- `model/enums/*`（如 `AnalysisStatus.ts`）

推荐约定：

- **请求调用**：统一通过 `services/*`。
- **类型声明**：统一引用 `model/static/*` 和 `model/enums/*`。
- **错误处理**：统一在 `Executor` 或上层适配器做拦截，页面不散落 try/catch 细节。

---

## 3. 目录约定（不做分层）

建议结构：

```text
src/
  api/                   # Jimmer 生成代码（Api.ts / services / model）
  stores/
    auth.ts
    analysis.ts
    data-source.ts
  views/
    login/
    analysis/
    data-sources/
  components/
    analysis/
      TaskTimeline.vue
      TaskResultCharts.vue
      SseStatusBadge.vue
  utils/
    sse.ts
```

说明：

- 生成代码统一放 `src/api/`，由 store 直接调用 `services/*`。
- 不额外引入 adapter 分层，减少文件跳转成本。
- store 里保留最少业务编排逻辑，页面只做展示和交互。

---

## 4. 鉴权与会话（Sa-Token）

已实现的关键能力：

- `me`（当前登录用户）
- `logout`
- `avatar` 上传（`multipart/form-data`）

落地要求：

- 前端请求默认携带凭据（按你的部署形态配置）。
- 应用启动先 `bootstrap`：调用 `me` 判断会话。
- 受保护接口出现未登录时：清空本地用户态并跳转 `/login`。

---

## 5. 分析任务主流程（异步 + SSE）

推荐标准流程：

1. 调用“创建任务”接口，拿到 `taskId`（和初始状态）。
2. 任务创建时后端会自动生成 `name`（`originalQuery` 前 20 字符）。
3. 立即订阅 `/api/analysis/tasks/{taskId}/events`。
4. 收到 `task-progress` 就更新任务状态和时间线。
5. 同步启动详情轮询兜底（低频）。
6. 到达 `SUCCESS`/`FAILED` 后：拉一次详情并停止全部跟踪。

后端事件：

- `connected`：连接握手成功
- `task-progress`：任务进度变更

注意：后端在终态会关闭 SSE，因此前端需要把“正常关闭”当成可能的完成信号之一。

---

## 6. SSE 实现建议（与 Jimmer 并行）

Jimmer 生成客户端主要用于 HTTP API；SSE 建议独立工具实现：

- 同域简单场景：原生 `EventSource`
- 跨域/凭据复杂场景：`@microsoft/fetch-event-source`

`utils/sse.ts` 只做三件事：

- `connectTaskEvents(taskId, handlers)`
- 自动重连（指数退避）
- `close()` 释放资源

Store 侧只关心订阅结果，不关心底层 SSE 细节。

---

## 7. Pinia 设计（AI 代码生成时遵守）

### `useAuthStore`

- 状态：`profile`、`isAuthenticated`、`isReady`
- 动作：`bootstrap`、`loginByPassword`、`loginByCode`、`logout`、`uploadAvatar`
- 字段：`profile.avatar` 可直接用于头像展示（为空时前端走默认头像）

### `useAnalysisStore`

- 状态：`taskList`、`taskDetailMap`、`trackingMap`
- 动作：`createTask`、`trackTask`、`stopTracking`、`fetchTaskDetail`、`fetchTaskList`、`renameTask`、`deleteTask`
- 规则：SSE 更新“过程态”，详情接口覆盖“最终态”

分析任务管理约定：

- `AnalysisTaskSummaryView` / `AnalysisTaskDetailView` 包含 `name` 字段，可直接用于列表标题与详情头部。
- 重命名接口：`PATCH /api/analysis/tasks/{taskId}/name`，请求体 `AnalysisTaskRenameInput`（`name` 非空）。
- 删除接口：`DELETE /api/analysis/tasks/{taskId}`，成功后前端应从本地列表移除并停止对应 SSE 跟踪。

### `useDataSourceStore`

- 状态：`list`、`detail`、`loading`
- 动作：`fetchList`、`fetchDetail`、`create`、`update`、`activate`、`deactivate`

数据源创建/编辑约定：

- 创建数据源时，`schemaInfo` 为可选字段，前端默认不传或传空。
- 后端会在创建成功后自动探测库表结构并回填 `schemaInfo`。
- 编辑数据源时，后端会忽略请求体中的 `schemaInfo`，防止误传覆盖。
- 仅在需要强制刷新结构时，调用更新接口并追加查询参数 `refreshSchemaOnly=true`。
- UI 建议提供“刷新结构”入口：`PUT /api/data-sources/{id}?refreshSchemaOnly=true` 后重新拉取详情。

用户头像上传约定：

- 上传接口：`POST /api/user/avatar`，`Content-Type: multipart/form-data`。
- 表单字段名固定为 `file`，上传成功返回最新 `UserMeResponse`。
- 后端返回 `avatar` 直链。
- 同内容文件会复用对象链接，前端不需要自行做去重。

---

## 8. ECharts 渲染约定

- 类型从 Jimmer 模型读取（`EChartsVisualization`）。
- 后端给的 `option` 直接渲染，不做大改写。
- 页面仅做兜底：空数据、容器 resize、异常 option 提示。

任务详情页建议分区：

1. 任务概览（状态/耗时/原始问题）
2. 阶段轨迹（时间线）
3. SQL + 洞察
4. 图表区（多图）

---

## 9. 给前端 AI 的执行清单

1. 先接入 Jimmer `services` 与 `model`，禁止手写重复 DTO。
2. 完成 `authStore.bootstrap()` 并打通登录态守卫。
3. 完成“创建任务 -> SSE 订阅 -> 终态收敛”闭环。
4. 用 `AnalysisStatus` 枚举驱动任务 UI 状态标签。
5. 任务详情页使用后端 `option` 渲染 ECharts。

---

## 10. 常见坑位

- 只依赖 SSE 不轮询：断线后可能漏终态。
- 页面离开不释放 SSE：会造成重复订阅和内存泄漏。
- 本地推断成功：应以任务详情接口结果为最终准。
- 手写类型覆盖生成类型：后续后端字段变更会导致双份类型漂移。
- 创建数据源强制手填 `schemaInfo`：会增加录入成本且易过期，当前应以后端自动探测为主。

---

## 11. 联调顺序（建议）

1. `login -> me -> logout`
2. 数据源列表/详情/编辑
3. 创建分析任务
4. SSE 接入（`connected`/`task-progress`）
5. 任务详情 + ECharts

