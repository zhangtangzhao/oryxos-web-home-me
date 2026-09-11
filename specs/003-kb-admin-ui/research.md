# Research: Web 管理台知识库页面

**Feature**: specs/003-kb-admin-ui | **Date**: 2026-09-10 | **输入**: spec.md 三项澄清（服务端托管静态页 / 物理删除 / 含试检索）

> 取证均来自仓库现状（标注文件位置），无外部依赖引入。

## R1. 静态页托管方式

**Decision**: 静态资源放 `oryxos-web/src/main/resources/static/admin/`，依赖 Spring Boot MVC 默认静态资源映射（`classpath:/static/**` → `/**`）随 fat jar 分发；新增 3 行级 `AdminPageController` 把 `/admin` 跳转到 `/admin/index.html`。

**Rationale**:
- 仓库无任何 `WebMvcConfigurer`/ResourceHandler 定制（已 grep 证实），Spring Boot 默认静态映射原样生效；`oryxos-web` 打进 fat jar 后资源天然携带，私有部署零安装。
- `GlobalExceptionHandler` 的 `NoResourceFoundException` 分支会把**不存在**的路径包成 JSON 信封——静态页入口必须是**真实存在的文件**（`/admin/index.html`），裸 `/admin` 加跳转控制器避免落到该分支。

**Alternatives considered**:
- 独立前端工程（Node/Vite）：构建产物另行部署，违背"零新增前端工具链"澄清决策；拒绝。
- 复用 VitePress 官网：公开营销站与运维界面混杂、权限边界不清；拒绝。
- 放 `oryxos-boot` 模块：boot 是启动聚合模块，页面属 Web 面；放 `oryxos-web` 与 6 个 ApiController 同归属更一致。

## R2. 试检索只读端点契约与调用路径

**Decision**: 新增 `POST /api/v1/kbs/{name}/search`（挂 `KbApiController`），实现直接调用 `KbSearchService.search(List.of(name), name, query, topK)`，随后经 `AuditLog.recordToolInvocation` 落一行 `tool_invocations`（tool_name=`kb_search`、session_id=`admin-ui`、result_json=既有结构化 auditJson）。错误分支按**机器可读字段**映射状态码：`error=true` 且嵌入未配置 → 503 `EMBEDDING_NOT_CONFIGURED`；`degradedReason` 含 `embedding_model_mismatch` → 409 `EMBEDDING_MISMATCH`；`error=true`（参数类）→ 400；其余失败 → 502。

**Rationale**:
- 校验复用：绑定解析（`resolveKb`）、嵌入身份防护（FR-015 比对）、加权 RRF 全部在 `KbSearchService.search` 内部，天然满足 FR-008"复用既有校验"。
- 审计复用：`AuditLog.recordToolInvocation` 正是 `ToolExecutor` 落审计所用的同一方法/同一表/同一 schema；管理面诊断调用无重试与沙箱语义，绕开 `ToolExecutor` 编排不产生行为差异，但审计行与 Agent 工具路径**逐字段一致**（宪法 V）。
- 结构化 hits：`SearchResult` 现状只带 `text`（模型可读文本）与 `auditJson`（无 content 片段），FR-011 要求展示命中片段 → **给 `SearchResult` record 追加 `List<SearchHit> hits` 组件**（成功路径填 hits，错误分支 `List.of()`）。编译影响闭合在 `oryxos-kb` 模块内（`KbTools`/`KbEvalService` 仅读既有组件，不受构造签名变更影响——record 追加组件需同步更新 `KbSearchService` 内部全部构造点，共 error/unavailableLike/modelMismatch/成功 4 处）。

**Alternatives considered**:
- 经 `ToolExecutor` 调用 `kb_search` 工具（合成 Profile + `ToolContext.bind`）：审计完全同路径，但 `ToolResult` 只有 `content`+`auditJson`，无法携带结构化 hits，FR-011 的片段/得分展示只能靠解析文本——脆弱；拒绝。
- 修改 `KbSearchService.audit()` 把 content 塞进 auditJson 再解析：审计行体积膨胀（每行多 ~2.5KB），且 web 层二次解析 JSON 不如直接读 record；拒绝。
- 复用 `POST /agents/{name}/invoke` 让页面走某个"管理 Agent"：强绑一个并不存在的 Agent 实体、引入 LLM 成本与不确定性；拒绝。

## R3. 页面形态

**Decision**: 单页应用（一个 `index.html` + `app.js` ES Module + `style.css`），原生 fetch 调用 `/api/v1/kbs/*`，中文 UI，无框架无构建。

**Rationale**: 澄清决策 1 锁定零工具链；页面规模（列表/详情/三个表单/试检索框/确认弹窗）原生实现约数百行，无需框架；ES2020 浏览器基线覆盖目标环境。

**Alternatives considered**: 轻量框架（petite-vue/Alpine 之类 CDN 引入）——仍引入第三方运行时与版本管理面，规模不匹配；拒绝。

## R4. 现有 REST 能力清单（复用面，来自 specs/002 contracts/rest-api.md 与 KbApiController 现状）

| 页面动作 | 既有端点 | 响应要点 |
|---|---|---|
| 库列表（US1） | `GET /api/v1/kbs` | name/description/document_count/ready_count/failed_count/embedding_model/updated_at |
| 库详情（US1/2） | `GET /api/v1/kbs/{name}` | documents[]（doc_path/status/chunk_count/size_bytes/error_message/ingested_at）+ embedding_dimensions |
| 结构总览（US1） | `GET /api/v1/kbs/{name}/overview` | documents[]（doc_path/headings[]） |
| 建库（US2） | `POST /api/v1/kbs` | 201；409 `KB_CONFLICT`；400 名称规则 |
| 加文档（US2） | `POST /api/v1/kbs/{name}/documents` | 202（path 或 filename+content 二选一）；400/404/409 |
| 摄取（US2） | `POST /api/v1/kbs/{name}/ingest` | processed/skipped/removed/failed/duration_ms/documents[]；409 `EMBEDDING_MISMATCH`；502/503 |
| 删除（US3） | `DELETE /api/v1/kbs/{name}` | 200 `{name, removed:true}`；404 `KB_NOT_FOUND` |
| 错误信封 | `ApiResponse{success,data,error}` + `GlobalExceptionHandler` | 400/404/409/500/502/503 全有映射 |

**唯一缺口** = 试检索端点（R2）。
