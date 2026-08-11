# CLAUDE.md — OryxOS 项目指南

> 本文档为 AI 编程助手提供 OryxOS 项目的完整上下文。开始任何工作前请先阅读本文档。

## 项目定位

OryxOS 是基于 Java 21 + Spring Boot 3.x 的**企业级 Agent OS**，作为统一底座运行多个业务 Agent，共享：渠道接入、模型路由、工具调用、记忆系统、沙箱执行。

**北极星公式：** 自然语言(md) + Memory + Tool + MCP(Connector) + Skill + 知识库 + Notify = 一个 Agent

一个目录定义一个 Agent，一个底座运行一群 Agent，私有部署，数据不出域。

## 技术栈

| 组件 | 版本/选型 | 用途 |
|------|----------|------|
| JDK | 21 | 运行环境，利用 Virtual Thread |
| Spring Boot | 3.x | 应用框架 |
| Spring AI Alibaba | 最新稳定版 | LLM Provider 抽象与协议转换 |
| Maven | 3.9+ | 多模块构建 |
| SQLite | — | Session/审计持久化 |
| Spring Data JPA | — | 数据访问层 |
| Picocli | 4.x | 命令行工具 |
| SnakeYAML | — | YAML 配置解析 |
| VitePress | 最新版 | 官网文档站点 |
| Logback + SLF4J | — | 结构化日志 |

## 模块结构（9 个 Maven 模块）

| 模块 | 职责 |
|------|------|
| `oryxos-core` | 核心抽象与接口：`OryxTool`、`Session`、`Profile`、`AgentLoader`、`ReActLoop`、`PromptBuilder`、`ToolExecutor`、`AgentService`、`AgentScheduler` |
| `oryxos-provider` | LLM Provider 抽象：`ProviderService`、Function Calling 适配、Provider name → ChatModel 显式映射 |
| `oryxos-memory` | 记忆系统：`MemoryService` 统一门面、`LongTermMemory`、`MemoryTools`（`save_memory`/`recall_memory`） |
| `oryxos-tool` | 工具体系：内置 Tool（File/Shell/HTTP/Notify）、`McpClientService`、`ToolRegistry`、`Sandbox` + `WhitelistSandbox`、`NotifyChannelAdapter` |
| `oryxos-channel-cli` | CLI Channel：`CliChannel`、`oryxos chat` 命令实现 |
| `oryxos-web` | REST API：`WebServer`、6 个 `ApiController`、`GlobalExceptionHandler`、OpenAPI 文档 |
| `oryxos-storage` | 持久化层：SQLite、`SessionRepository`、`ToolInvocationRepository`、`LlmCallRepository` |
| `oryxos-cli` | 命令行入口：Picocli 主入口、12 个子命令 |
| `oryxos-boot` | Spring Boot 启动模块：主类、自动配置、依赖聚合 |

## 不可违背原则（Constitution）

1. **Java 21，不得降版本** — 同步执行模型，不引入 Reactor / WebFlux / CompletableFuture
2. **ReAct Loop 自实现** — 禁用 Spring AI 自动 tool 执行；Spring AI 只用于协议转换 + `@Tool` schema 生成
3. **多 Provider 显式 `Map<String, ChatModel>` 映射** — 不靠类型扫描
4. **沙箱用 `SandboxChecker` 白名单** — 路径/命令/域名白名单，不使用 `SecurityManager`
5. **审计表 `tool_invocations` / `llm_calls` 核心阶段即写入 SQLite** — day one 立数据地基
6. **敏感配置只走环境变量** — 绝不明文写进 YAML / 代码 / 提交记录
7. **`AGENT.md` 一个目录 = 一个 Agent** — Agent 目录归 `ContextLoader`（core 模块），不是 Tool
8. **Skill ≠ Tool** — Skill 不进 `ToolRegistry`，正文经 `read_file`/`shell` 按需读取
9. **接口先行** — Sandbox、Memory、NotifyChannel 等模块先定抽象接口，核心阶段只挂一档实现

## 五大核心能力

1. **对接 LLM** — Provider 抽象统一对接主流大模型，运行时切换无 lock-in
2. **ReAct 循环** — 自实现：Reason → Act → Observe，循环直到无 Tool 调用或达到最大迭代次数
3. **Memory 三层记忆** — 会话记忆(SQLite) + 长期记忆(MEMORY.md) + 情景记忆(扩展阶段)
4. **工具体系** — 内置 9 个 Tool + Plugin Tool 三档接入（零代码/MCP/Java @Tool）
5. **Web Service** — REST API 覆盖会话管理、Agent 调用、Profile/Memory/Tool 信息、系统状态

## ReAct Loop 机制

```
接到用户消息
  └─ 追加到 Session 对话历史
     └─ 组装 Prompt（system prompt + Bootstrap + Memory + 对话历史 + Tool 列表）
        └─ 调用 LLM Provider 获取响应
           ├─ [无 Tool 调用] → 返回最终响应
           └─ [有 Tool 调用] → 执行 Tool → 结果追加到对话历史 → 继续循环
```

- 最大迭代次数默认 10，可在 Profile 覆盖
- Tool 调用经 Sandbox 白名单校验
- 每次 LLM 调用和 Tool 调用写入审计表

## Tool 体系

**内置 Tool（9 个）：**
- `read_file` / `write_file` / `list_dir` — 文件操作（路径白名单）
- `shell` — Shell 命令执行（命令白名单 + 超时）
- `http_get` / `http_post` — HTTP 请求（域名白名单）
- `save_memory` / `recall_memory` — 长期记忆读写
- `notify` — 通知推送（Webhook）

**Plugin Tool 三档接入：**
1. 零代码：Agent 目录 + MCP server 复用
2. 轻代码：自写 MCP server（任何语言）
3. 重代码：`@Tool` 注解 Java Spring Bean

## API 端点（核心阶段 10 个）

| 类别 | 端点 | 说明 |
|------|------|------|
| 会话管理 | `POST /api/v1/sessions` | 创建会话 |
| 会话管理 | `POST /api/v1/sessions/{id}/messages` | 发消息 |
| 会话管理 | `GET /api/v1/sessions/{id}` | 查历史 |
| 会话管理 | `DELETE /api/v1/sessions/{id}` | 归档会话 |
| Agent 调用 | `POST /api/v1/agents/{name}/invoke` | 无状态调用 |
| Profile 信息 | `GET /api/v1/profiles` | 列 Profile |
| Memory 操作 | `GET /api/v1/memory` | 查长期记忆 |
| Tool 信息 | `GET /api/v1/tools` | 列可用 Tool |
| 系统状态 | `GET /api/v1/health` | 健康检查 |
| 系统状态 | `GET /api/v1/info` | 运行信息 |

## CLI 命令（核心阶段 12 个）

```
oryxos init                          # 初始化工作区
oryxos status                        # 查看状态
oryxos chat [--profile <name>]       # 交互对话
oryxos serve [--port 8080]           # 启动 HTTP API
oryxos gateway                        # 守护进程
oryxos profile list / create / show / delete  # Profile 管理
oryxos provider list                  # 列出 Provider
oryxos tool list                      # 列出 Tool
oryxos session list                   # 列出会话
```

## 数据模型

**Session** (SQLite — `sessions`):
- session_id (PK), profile_name, channel, user_id
- messages_json (TEXT), status (active/archived)
- created_at, last_active_at, archived_at

**Tool Invocation** (SQLite — `tool_invocations`):
- id (PK), session_id, tool_name, input_json, result_json
- success, error_message, duration_ms, created_at

**LLM Call** (SQLite — `llm_calls`):
- id (PK), session_id, provider, model
- prompt_tokens, completion_tokens, total_tokens
- duration_ms, created_at

## 开发节奏（4 周）

| 周次 | 主线 | 可演示成果 |
|------|------|-----------|
| W1 | 对接 LLM + ReAct 循环 | `oryxos chat` 多轮对话 + HTTP Tool |
| W2 | Memory + Tool 体系 | 长期记忆 + 内置 Tool + MCP |
| W3 | Web Service | 10 个 REST 端点 |
| W4 | 多 Agent + 工程化收尾 | 多 Agent 并存 + SQLite + 12 个 CLI 命令 + 官网 |

## 工作区结构

```
.oryxos/
├── agents/            # 每个子目录 = 一个 Agent（AGENT.md + skills/ + scripts/ + REFERENCE.md）
├── skills/            # 公共 Skill 实体库
├── memory/
│   └── MEMORY.md      # 长期记忆
├── output/            # Agent 产出物
├── sessions/          # 会话数据
├── logs/              # 结构化日志
├── AGENTS.md          # Bootstrap：项目级行为说明
├── SOUL.md            # Bootstrap：Agent 人格定义
├── USER.md            # Bootstrap：用户偏好
└── oryxos.db          # SQLite 数据库
```

## 常见陷阱

1. **误用 Spring AI 自动 tool 执行** — 导致 tool 被调两次。必须禁用自动执行，由 `ToolExecutor` 控制
2. **Provider 用类型扫描** — 多 Provider 并存时 Bean 类型相同有歧义，必须用显式 `Map<String, ChatModel>` 映射
3. **把 `AgentLoader`/`AGENT.md` 当成 Tool** — Agent 目录归 `ContextLoader`（core 模块），正文注入 system prompt
4. **审计表没落库** — `tool_invocations` 和 `llm_calls` 核心阶段就必须写入，不是只放日志
5. **敏感配置写进 YAML** — API key 必须通过环境变量注入，YAML 中用 `${ENV_VAR}` 占位
6. **把 Skill 和 Tool 混为一谈** — Skill 不进 `ToolRegistry`，由 `ContextLoader` 注入 system prompt
7. **Tool 模块拆太细** — 核心阶段 `oryxos-tool` 一个模块包含所有 Tool 相关内容
8. **忘记 JDK 版本约束** — 全程 JDK 21，不得降版本，不引入异步框架
