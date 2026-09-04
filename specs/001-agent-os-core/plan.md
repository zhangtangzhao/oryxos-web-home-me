# Implementation Plan: OryxOS 核心阶段运行时内核（五大核心能力）

**Branch**: `001-agent-os-core` | **Date**: 2026-09-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-agent-os-core/spec.md`

## Summary

将需求文档（`docs/DemandAnalysis.md`）核心阶段的五大核心能力落地为可运行的运行时内核：配置即 Agent（`AGENT.md` 目录派生 Profile）、自实现 ReAct 循环、会话 + 长期两层记忆、白名单沙箱保护的内置/插件（MCP）工具体系、10 端点 REST 对外服务、多 Provider 显式映射无锁定切换、定时任务第三触发源。技术路径：Java 21 同步执行模型，Spring Boot 3.4 仅作应用框架与协议转换，ReAct 循环与工具编排完全自控，SQLite 落会话与双审计表，单可执行 JAR 交付。仓库已有 9 模块 Maven 骨架（43 个 Java 源文件），本计划在其上补齐实现。

## Technical Context

**Language/Version**: Java 21（JDK 21，同步执行模型，禁 Reactor/WebFlux/CompletableFuture）

**Primary Dependencies**: Spring Boot 3.4.0（web/validation）、Spring AI BOM 1.0.0-M5 + Spring AI Alibaba 1.0.0-M5（LLM 协议转换与 `@Tool` schema 生成，禁自动 tool 执行）、Picocli 4.7.6 + picocli-spring-boot-starter（CLI）、Jackson 2.18、MCP 官方 Java SDK（MCP Client）、Spring Data JPA + hibernate-community-dialects（SQLite 方言）、SnakeYAML（经 Spring Boot 传递）

**Storage**: SQLite 单文件（`.oryxos/oryxos.db`：sessions、tool_invocations、llm_calls 三表）+ 文件形态（`AGENT.md`、`MEMORY.md`、Bootstrap、Skill 库）

**Testing**: JUnit 5 + Spring Boot Test（`mvn test`，Surefire）；端到端验证以 quickstart.md 场景为准（真实 LLM 凭据 + 两个每日 Demo）

**Target Platform**: Linux 主流发行版（Ubuntu 22.04+、CentOS 8+ 等）单节点部署；开发环境 Windows/macOS/Linux 均可；JDK 21+

**Project Type**: 多模块 Maven 工程：library（core/provider/memory/tool/storage）+ cli（oryxos-cli/channel-cli）+ web-service（oryxos-web）+ boot 聚合启动，单可执行 JAR

**Performance Goals**: 单节点 ≥10 Agent 并存、≥100 并发 Session；Session 创建 P99 ≤ 200ms；底座内部转发开销 ≤ 50ms（LLM 响应时间不计入）

**Constraints**: 审计表 day-one 落库；敏感配置仅环境变量（`${ENV_VAR}` 占位）；工具调用 100% 过白名单沙箱；ReAct 最大迭代默认 10 可配置；会话闲置默认 30 分钟归档（可按 Agent 覆盖）；配置重启生效（无热加载）

**Scale/Scope**: 核心阶段 4 周；9 个 Maven 模块；29 条功能需求（FR-001~029）；10 个 REST 端点、12 个 CLI 命令、9 个内置 Tool、5 个 user story、2 个端到端每日 Demo（发布硬条件）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | 原则 | 门禁判定 | 依据/落地方式 |
|---|------|---------|--------------|
| I | Java 21 同步执行模型 | ✅ PASS | 根 pom `maven.compiler.target=21`；web 模块用 Spring MVC（非 WebFlux）；ReAct 循环同步 for 循环实现 |
| II | ReAct Loop 自实现 | ✅ PASS | `oryxos-core` 内自实现 Reason→Act→Observe；Spring AI 仅做协议转换与 `@Tool` schema 生成，关闭其自动 tool 执行 |
| III | Provider 显式映射 | ✅ PASS | `ProviderService` 内部 `Map<String, ChatModel>` 按 name 注册与查找，不做类型扫描 |
| IV | 白名单沙箱 | ✅ PASS | `Sandbox` 接口 + `WhitelistSandbox` 实现；文件路径/Shell 命令/HTTP 域名三类白名单 + 超时；不使用 SecurityManager |
| V | 审计先行 | ✅ PASS | `ToolInvocationEntity/Repository`、`LlmCallEntity/Repository` 已存在于 oryxos-storage；每次 LLM/Tool 调用强制写入 |
| VI | 敏感配置只走环境变量 | ✅ PASS | `AGENT.md`/Provider 配置仅允许 `${ENV_VAR}` 占位，加载期解析 + 基础校验，缺失即清晰报错 |
| VII | 一个目录 = 一个 Agent | ✅ PASS | `AGENT.md` 目录由 `ContextLoader`/`AgentLoader.deriveProfile()`（core）管理，派生 Profile 注入 system prompt；不做成 Tool |
| VIII | Skill ≠ Tool | ✅ PASS | Skill 仅注入 name/description/路径元数据，不进 `ToolRegistry`；正文经 `read_file` 按需读取 |
| IX | 接口先行 | ✅ PASS | `OryxTool`/`Sandbox`/`MemoryService`/`NotifyChannelAdapter` 均先定接口，每接口一档默认实现（WhitelistSandbox、文件记忆等） |

**Post-design re-check (Phase 1 完成后)**: ✅ 全部 PASS —— research/data-model/contracts 设计未引入任何违反项；无violation 需要记录。

## Project Structure

### Documentation (this feature)

```text
specs/001-agent-os-core/
├── plan.md              # 本文件（/speckit-plan 输出）
├── research.md          # Phase 0 输出：技术决策与备选
├── data-model.md        # Phase 1 输出：实体、字段、状态迁移
├── quickstart.md        # Phase 1 输出：端到端验证指南
├── contracts/           # Phase 1 输出：对外契约
│   ├── rest-api.md      #   10 个 REST 端点契约
│   ├── cli.md           #   12 个 CLI 命令契约
│   └── agent-workspace.md # AGENT.md/工作区/MEMORY.md 文件契约
└── tasks.md             # Phase 2 输出（/speckit-tasks，非本命令创建）
```

### Source Code (repository root)

```text
pom.xml                     # 聚合 pom：9 模块、依赖版本管理（已存在）
oryxos-core/                # 核心抽象：OryxTool/Session/Profile/AgentLoader/ContextLoader/
│                           # ReActLoop/PromptBuilder/ToolExecutor/AgentService/AgentScheduler
oryxos-provider/            # ProviderService：Map<String, ChatModel> 显式映射、Function Calling 适配
oryxos-memory/              # MemoryService 门面、LongTermMemoryStore（MEMORY.md）、MemoryTools
oryxos-tool/                # 内置 Tool（file/shell/http/memory/notify）、McpClientService、
│                           # InMemoryToolRegistry、WhitelistSandbox、NotifyChannelAdapter
oryxos-channel-cli/         # CliChannel：oryxos chat 交互实现
oryxos-web/                 # WebServer、6 个 ApiController、GlobalExceptionHandler、OpenAPI
oryxos-storage/             # SQLite：SessionRepository、ToolInvocationRepository、LlmCallRepository
oryxos-cli/                 # Picocli 主入口 + 12 子命令（init/status/chat/serve/gateway/
│                           # profile×4/provider/tool/session）
oryxos-boot/                # Spring Boot 主类、自动配置、依赖聚合、单可执行 JAR 打包
bin/                        # 启动脚本（已存在）
config/                     # 示例配置（已存在）
website/                    # VitePress 官网（独立交付，不在本计划范围）
```

**Structure Decision**: 沿用仓库既有的 9 模块 Maven 结构（宪法与 CLAUDE.md 定死的模块边界），不新增、不拆分模块。core 模块持有全部核心接口与 ReAct 实现；tool 模块收敛全部工具相关内容（宪法"Tool 模块拆太细"禁令）；boot 为唯一启动聚合点。

## Complexity Tracking

> 无宪法违规项，本表留空。

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| （无） | — | — |
