# Implementation Plan: Web 管理台知识库页面

**Branch**: `003-kb-admin-ui` | **Date**: 2026-09-10 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-kb-admin-ui/spec.md`

## Summary

为已齐备的知识库管理 REST 能力补齐图形管理界面：管理员在浏览器完成库总览/详情查看、建库→传文档→摄取闭环、删除（物理删除+二次确认）与试检索（查询→命中片段+得分）。技术方案 = **服务端托管静态页**（Spring Boot 默认静态资源机制，原生 HTML/CSS/JS 零构建）+ **一个只读试检索端点**（复用 `KbSearchService` 全部校验，经既有 `AuditLog` 落同 schema 审计行），后端零新表、零新依赖。

## Technical Context

**Language/Version**: Java 21（服务端，同步执行模型）；前端为原生 HTML + CSS + JavaScript ES Module（无框架、无构建步骤）

**Primary Dependencies**: Spring Boot 3.4 Web（MVC，既有）；`oryxos-kb`（`KbSearchService`/`DefaultKbService`，既有）；`oryxos-core`（`AuditLog`，既有）。**零新增依赖。**

**Storage**: 既有 SQLite（`kb_knowledge_bases` / `kb_documents` / `kb_chunks` / `tool_invocations`）——本特性**零新表、零列变更**

**Testing**: JUnit 5 + `ApplicationContextRunner`（既有 oryxos-kb/web 测试模式）；端到端按 quickstart.md 以 mock OpenAI 服务回归

**Target Platform**: 私有部署 JVM 服务器；现代桌面浏览器（ES2020+）

**Performance Goals**: SC-003：30 库、单库 100 文档下列表与详情 ≤2s；SC-006：试检索首条结果 ≤1s（同规模）

**Constraints**: 零新增前端工具链（澄清决策 1）；试检索为唯一新增后端端点且只读（FR-008 例外）；管理动作审计与接口直调一致（SC-002）；密钥只走环境变量（宪法 VI，本特性不新增密钥）

**Scale/Scope**: 单管理员/少量管理员低频操作；数十库 × 单库数百文档；1 个静态页（3 个文件）+ 1 个新端点 + 若干测试

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 | 结论 |
|------|------|------|
| I. Java 21 同步执行模型 | 前端为静态资源；端点为同步 MVC 方法，无 Reactor/WebFlux/CompletableFuture | ✅ PASS |
| II. ReAct Loop 自实现 | 不触碰 ReAct/Spring AI 路径 | ✅ N/A |
| III. Provider 显式映射 | 不触碰 Provider | ✅ N/A |
| IV. 白名单沙箱 | 试检索为服务端内部只读（无 FILE/SHELL/HTTP 沙箱动作，与 `KbTools` 现状一致，见其 javadoc） | ✅ PASS |
| V. 审计先行 | 试检索经 `AuditLog.recordToolInvocation` 落 `tool_invocations`（tool_name=`kb_search`，result_json=既有结构化 auditJson），与 Agent 工具路径同 schema 同 sink | ✅ PASS |
| VI. 敏感配置只走环境变量 | 无新增密钥/配置 | ✅ N/A |
| VII. 一个目录 = 一个 Agent | 不触碰 Agent 目录模型 | ✅ N/A |
| VIII. Skill ≠ Tool | 不触碰 Skill | ✅ N/A |
| IX. 接口先行 | 无新抽象接口需求（复用既有 service 门面；静态页无后端扩展点） | ✅ PASS |
| YAGNI（Governance） | 拒绝独立前端工程/回收站/导出备份（澄清决策 1、2） | ✅ PASS |

**Phase 1 复检**：设计未引入违例——新增面 = 静态资源 3 文件 + 1 个只读端点 + `SearchResult` record 追加 `hits` 组件（kb 模块内编译闭合，调用方只读不受影响）。**GATE 通过。**

## Project Structure

### Documentation (this feature)

```text
specs/003-kb-admin-ui/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── admin-rest-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
oryxos-web/
├── src/main/java/com/oryxos/web/
│   ├── KbApiController.java              # 增量：POST /{name}/search 试检索端点（唯一新端点）
│   └── AdminPageController.java          # 新增：/admin → /admin/index.html 跳转
└── src/main/resources/static/admin/      # 新增：管理台静态页（Spring Boot 默认静态资源位置）
    ├── index.html                        # 单页入口（中文 UI）
    ├── app.js                            # ES Module：视图渲染 + fetch 调用
    └── style.css                         # 样式

oryxos-kb/
└── src/main/java/com/oryxos/kb/
    └── KbSearchService.java              # 增量：SearchResult record 追加 hits 组件（错误分支 List.of()）

oryxos-kb/src/test/java/com/oryxos/kb/    # SearchResult.hits / 试检索分支测试增量
oryxos-web/src/test/java/com/oryxos/web/  # 试检索端点契约测试 + 静态页可达性测试
```

**Structure Decision**: 不新建 Maven 模块——管理台页面与端点均收敛在 `oryxos-web`（与"工具内容不过细拆分"的既有边界观一致）；静态资源放 Spring Boot 默认 `classpath:/static/admin/`，fat jar 天然携带、私有部署零额外安装。

## Complexity Tracking

> Constitution Check 无违例，本节留空。

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| （无） | — | — |
