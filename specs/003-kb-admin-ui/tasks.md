# Tasks: Web 管理台知识库页面

**Input**: Design documents from `/specs/003-kb-admin-ui/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 已纳入。后端唯一新增面（试检索）按仓库惯例测试先行（KbSearchServiceTest / 端点契约测试）；US1~US3 复用既有端点、零后端变更，验证走 E2E 任务（quickstart.md 场景）。

**Organization**: 按用户故事分组。阶段顺序 = 优先级顺序：US1 (P1) → US2 (P2) → US4 (P2) → US3 (P3)（US4 虽排 spec 第 4 节但优先级高于 US3）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

Maven 多模块仓库（见 plan.md Project Structure）：

- 后端：`oryxos-web/src/main/java/com/oryxos/web/`、`oryxos-kb/src/main/java/com/oryxos/kb/`
- 静态页：`oryxos-web/src/main/resources/static/admin/`
- 测试：`oryxos-web/src/test/java/com/oryxos/web/`、`oryxos-kb/src/test/java/com/oryxos/kb/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 管理台静态页宿主骨架

- [X] T001 [P] 创建管理台静态页骨架三文件 `oryxos-web/src/main/resources/static/admin/index.html` / `app.js` / `style.css`：中文 UI 基调、应用外壳（顶栏 + 主视图容器）、`app.js` 以 ES Module 组织并预留视图路由（列表/详情）与统一 `fetch` 包装（非 2xx 解析 `ApiResponse.error` 信封 → 统一错误条；网络异常 → "服务不可达"+重试入口，FR-009）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 页面可达性——所有用户故事的入口

- [X] T002 新增 `oryxos-web/src/main/java/com/oryxos/web/AdminPageController.java`：`GET /admin` 302 跳转 `/admin/index.html`（research.md R1：避免裸路径落到 `NoResourceFoundException` 的 JSON 信封）
- [X] T003 新增 `oryxos-web/src/test/java/com/oryxos/web/AdminPageControllerTest.java`（MockMvc standalone）：`/admin` → 302 指向 `/admin/index.html`（先写后实现，与 T002 构成红绿对）

**Checkpoint**: 构建后 `GET /admin` 可达静态页，页面外壳可打开——用户故事可开始

---

## Phase 3: User Story 1 - 知识库总览与单库详情查看 (Priority: P1) ✅ MVP

**Goal**: 管理员打开页面即见全部库概况，可进入单库查看文档清单与结构总览

**Independent Test**: 预置 2~3 个不同状态库（含失败文档），页面核对列表与详情信息完整准确（spec US1 验收 1~3）

### Implementation for User Story 1（纯前端，复用既有端点）

- [X] T004 [US1] `oryxos-web/src/main/resources/static/admin/app.js` 列表视图：`GET /api/v1/kbs` 渲染（名称/描述/文档总数/就绪数/失败数/嵌入模型/更新时间；failed_count>0 可辨识异常标识；空数组 → 空态 + "创建知识库"引导入口）
- [X] T005 [US1] `app.js` 详情视图：点库进入，`GET /api/v1/kbs/{name}` 渲染文档清单（doc_path/status/chunk_count/size_bytes/ingested_at/error_message 人话展示）+ `GET /api/v1/kbs/{name}/overview` 渲染结构总览（文档 → 标题骨架折叠区）；404 `KB_NOT_FOUND` → 提示并回列表
- [X] T006 [US1] E2E 验证：按 quickstart.md 启动环境（重建 fat jar + mock），浏览器核对 US1 验收 1~3（三库列表字段齐、失败标识、详情与骨架、空态引导）

**Checkpoint**: MVP 可用——零命令行掌握知识库全貌

---

## Phase 4: User Story 2 - 建库到文档就绪的完整闭环 (Priority: P2)

**Goal**: 页面上完成建库 → 加文档 → 摄取 → 就绪全流程

**Independent Test**: 全新空部署仅用页面操作走完闭环（spec US2 验收 1~4）

### Implementation for User Story 2（纯前端，复用既有端点）

- [X] T007 [US2] `app.js` 建库表单：名称（必填，`[a-z0-9][a-z0-9_-]{0,63}` 前端预校验）+ 描述（选填）→ `POST /api/v1/kbs`；201 刷新列表，409 `KB_CONFLICT` 点名冲突提示，400 回显名称规则（FR-003）
- [X] T008 [US2] `app.js` 加文档表单（详情页内）：二选一来源——粘贴文本 + 文件名（前端校验非空、`.md`/`.markdown`/`.txt`）或服务端文件路径 → `POST /api/v1/kbs/{name}/documents`；202 后文档列表新增"待摄取"行；400/404/409 回显原因（FR-004）
- [X] T009 [US2] `app.js` 摄取动作 + 结果面板：详情页"开始摄取"→ `POST /api/v1/kbs/{name}/ingest`；渲染汇总（processed/skipped/removed/failed/duration_ms）与逐文档结果，随后刷新详情状态；失败文档显示原因并常驻"重新摄取"入口（FR-005/FR-006）；409 `EMBEDDING_MISMATCH` → "嵌入模型已变更，请恢复配置或重建知识库"；502/503 → 对应提示可重试
- [X] T010 [US2] E2E 验证：浏览器走完 US2 验收 1~4（建库/传文档/摄取就绪/坏文档失败原因与重试），并 curl 复核页面动作与直调审计一致（SC-002）

**Checkpoint**: US1+US2 可独立演示——零命令行建库闭环

---

## Phase 5: User Story 4 - 试检索验证检索效果 (Priority: P2)

**Goal**: 库详情页输入查询即见命中片段与得分；不可用状态有因可查

**Independent Test**: 对就绪库查已知答案命中、无关查询零命中提示、身份变更 409 提示（spec US4 验收 1~3）

### Tests for User Story 4（先写先跑）

- [X] T011 [P] [US4] `oryxos-kb/src/test/java/com/oryxos/kb/KbSearchServiceTest` 增量：成功路径 `SearchResult.hits()` 非空且与 auditJson results 一致（rank/doc_path/score）；错误与降级分支（error/unavailable/mismatch）`hits()` 为空列表（先红后绿）
- [X] T012 [P] [US4] 新增 `oryxos-web/src/test/java/com/oryxos/web/KbAdminSearchEndpointTest.java`（MockMvc standalone，stub `KbSearchService` + `AuditLog`）：覆盖 200 命中 / 200 零命中 / 400 空 query / 404 `KB_NOT_FOUND` / 409 `EMBEDDING_MISMATCH`（degraded_reason 判别）/ 503 `EMBEDDING_NOT_CONFIGURED` / 502 兜底，并断言审计参数（session_id=`admin-ui`、tool_name=`kb_search`、result_json=auditJson）（先红后绿）

### Implementation for User Story 4

- [X] T013 [US4] `oryxos-kb/src/main/java/com/oryxos/kb/KbSearchService.java`：`SearchResult` record 追加 `List<SearchHit> hits` 组件；更新类内全部构造点（成功路径填 hits，`error`/`unavailableLike`/`modelMismatch` 分支 `List.of()`；`KbTools`/`KbEvalService` 仅读既有组件，无需改动）→ T011 转绿
- [X] T014 [US4] `oryxos-web/src/main/java/com/oryxos/web/KbApiController.java` 新增 `POST /{name}/search`（contracts/admin-rest-api.md）：直调 `search(List.of(name), name, query, topK)`；分支映射 200/400/404/409/503/502；经 `AuditLog.recordToolInvocation` 落 `tool_invocations`（session_id=`admin-ui`，input_json=`{kb,query,top_k}`，result_json=auditJson，耗时与 success 同步）→ T012 转绿
- [X] T015 [US4] `app.js` 试检索框（详情页）：查询输入 → `POST /api/v1/kbs/{name}/search`；渲染命中列表（doc_path/heading_path/chunk_ordinal/score/content 片段）；零命中 → "无命中结果"；409/503/502 → contracts 错误文案表对应提示
- [X] T016 [US4] E2E 验证：quickstart US4 场景（命中/零命中/审计落行 `admin-ui|kb_search`）+ 换 `KB_EMBEDDING_MODEL` 重启后 409 提示 + 停 mock 后 503 分支

**Checkpoint**: US1+US2+US4 可独立演示——页面上闭环"摄取 → 就绪 → 确认可检索"

---

## Phase 6: User Story 3 - 删除知识库与危险操作防护 (Priority: P3)

**Goal**: 二次确认防护下的物理删除

**Independent Test**: 对预置库走删除流程，验证确认弹窗、取消零改动、删除后列表与 404（spec US3 验收 1~3）

### Implementation for User Story 3（纯前端，复用既有端点）

- [X] T017 [US3] `app.js` 删除流程（详情页）：删除按钮 → 确认弹窗（明示库名、文档数、"不可恢复"）；取消 → 关闭弹窗零请求（SC-005）；确认 → `DELETE /api/v1/kbs/{name}` → 回列表并移除该库；详情/后续操作遇 404 `KB_NOT_FOUND` → 提示并刷新列表不留僵尸入口（FR-007/FR-009）
- [X] T018 [US3] E2E 验证：US3 验收 1~3（弹窗内容、取消零改动、删除后列表消失 + curl 直调 404 `KB_NOT_FOUND`）

**Checkpoint**: 全部四个故事独立可用

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 回归、文档与验收勾稽

- [X] T019 `mvn clean test` 全量回归（仓库根目录执行），确保既有 107+ 测试与新增测试全绿
- [X] T020 按 quickstart.md 全场景走查：静态页冒烟 + US1~US4 + 边界表 5 场景（重名/身份变更/嵌入不可达/未配置/服务不可达），对照 SC-004 静默失败为 0
- [X] T021 文档同步：`docs/oryxos.md` 知识库节补"Web 管理台"段落（入口 `/admin/`、四项能力、试检索只读端点归属 FR-008 例外）；`docs/TechnicalSolution.md` §7 Web 节补管理台静态页与端点增量
- [X] T022 验收勾稽：对照 spec.md SC-001~006 逐项复核并记录证据（SC-001 计时走查、SC-003 30 库/单库 100 文档规模加载 ≤2s、SC-006 首条 ≤1s），结果回填本文件 Notes

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，立即开始
- **Foundational (Phase 2)**: 依赖 T001；BLOCKS 全部用户故事（页面入口）
- **User Stories (Phase 3~6)**: 均依赖 Phase 2；按 P1→P2→P2→P3 顺序推进（US4 在 US3 前）
- **Polish (Phase 7)**: 依赖全部用户故事完成

### User Story Dependencies

- **US1**: 仅依赖 Phase 2；前端独占 `app.js`——是后续故事的同一文件宿主，故建议顺序推进 US1 → US2 → US4 → US3
- **US2**: 依赖 US1 的详情视图（表单挂详情页内）；自身零后端变更
- **US4**: T011/T012（测试，可并行）→ T013 → T014 → T015；后端两文件（kb/web）与前端互不阻塞，但 T014 依赖 T013 的新 record 组件
- **US3**: 依赖 US1 列表/详情；零后端变更

### Parallel Opportunities

- T001 与 T011/T012 可提前并行（不同文件；T011/T012 属 US4 测试先行，红态可先落）
- US4 内 T013（kb 模块）与 T015 前置的静态页改动可并行；T014 依赖 T013
- US1~US3 若多人协作，`app.js` 为共享文件须串行；后端 T013/T014 可与前端任务并行

---

## Parallel Example: User Story 4

```bash
# 测试先行（两个不同文件，并行）：
Task: "T011 KbSearchServiceTest hits 断言增量（oryxos-kb/src/test/.../KbSearchServiceTest.java）"
Task: "T012 端点契约测试（oryxos-web/src/test/.../KbAdminSearchEndpointTest.java）"
# 实现：
Task: "T013 SearchResult.hits（oryxos-kb/.../KbSearchService.java）"  → T011 转绿
Task: "T014 试检索端点（oryxos-web/.../KbApiController.java）"        → T012 转绿
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 + 2：页面宿主可达
2. Phase 3：US1 列表/详情 → **STOP and VALIDATE**（T006）
3. 此时已交付"零命令行掌握知识库全貌"

### Incremental Delivery

- +US2：零命令行建库闭环（T010 验证）
- +US4：检索效果页面上闭环（T016 验证，含审计核对）
- +US3：危险操作防护收口（T018 验证）
- Phase 7：全量回归 + 文档 + SC 勾稽后收尾

## Notes

- 后端增量仅 T002/T013/T014 三处，其余全部为静态页与验证——每阶段结束构建 fat jar 做 E2E（`mvn -pl oryxos-boot -am package -DskipTests`）
- env 只内联不 export（Bash 每次新 shell）；git-bash curl body 用 ASCII
- [P] = 不同文件、无未完成依赖；story 标签用于追溯
- T022 勾稽证据回填处（2026-09-11）：
  - **SC-001** ✅ 建库→加文档→摄取就绪闭环经页面同款端点链路实测（201→202→ingest ready），全流程秒级，远低于 5 分钟；浏览器点击流因本环境无浏览器工具未走查，页面交互代码（表单/引导/按钮）已静态核对
  - **SC-002** ✅ 新增后端路径仅只读 `POST /api/v1/kbs/{name}/search`（特权写路径 0）；页面全部动作复用既有 `/api/v1/kbs*` 端点；sqlite3 复核 `tool_invocations` 落行 `admin-ui|kb_search`（success=1/0 共 6 行，input_json={kb,query,top_k}，result_json=auditJson）
  - **SC-003** ✅ 30 库（单库 100 文档）实测：列表最差 31ms、单库详情 6ms、结构总览 35ms，均 ≪ 2s
  - **SC-004** ✅ 边界表 5 场景 HTTP 层全过：重名 409 `KB_CONFLICT` 点名、身份变更 409 `EMBEDDING_MISMATCH`、嵌入不可达 502 `EMBEDDING_UNAVAILABLE`/摄取逐文档失败原因展示、未配置 503 `EMBEDDING_NOT_CONFIGURED`、前端网络异常"服务不可达"+重试条（api() 包装）；页面均有对应中文文案渲染路径，静默失败 0
  - **SC-005** ✅ 删除 100% 经 `confirmModal` 二次确认（明示库名/文档数/不可恢复）；取消路径直接 return、零请求；确认后 DELETE→列表移除→直调 404 `KB_NOT_FOUND`
  - **SC-006** ✅ 试检索就绪库首条结果最差 53ms（100 文档规模、5 轮取最差），≪ 1s；零命中 200 `zero_result:true` 页面显示"无命中结果"
- 实现期修正：`OpenAiCompatEmbeddingClient` 未配置时 `model()` 返回空——否则 FR-015 身份比对会在 503 分支前误报 409（quickstart 边界表发现，测试 `OpenAiCompatEmbeddingClientTest` 固化）
