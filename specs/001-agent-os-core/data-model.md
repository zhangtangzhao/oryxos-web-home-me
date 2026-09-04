# Data Model: OryxOS 核心阶段运行时内核

**Feature**: `001-agent-os-core` | **Date**: 2026-09-02 | **来源**: spec.md Key Entities + 需求文档 §10

实体分三组：文件形态（Agent 目录、Memory、Skill）、运行时对象（Profile、Provider 配置、Session 上下文）、持久化表（SQLite：sessions / tool_invocations / llm_calls）。

## 1. Agent（Agent 目录，文件形态）

一个目录 = 一个 Agent：`.oryxos/agents/<name>/`。

| 字段（AGENT.md frontmatter） | 类型 | 必填 | 校验规则 |
|---|---|---|---|
| `name` | string | 是 | 必须等于目录名；`[a-z0-9-]`；全实例唯一（重名创建报错） |
| `description` | string | 是 | 非空 |
| `identity.prompt` | string | 是 | 人格/系统提示词，或引用 `SOUL.md` |
| `provider.name` | string | 是 | 必须存在于 Provider 显式映射 |
| `provider.model` | string | 是 | 非空 |
| `provider.temperature` | float | 否 | 0.0–2.0 |
| `tools[]` | string[] | 否 | 每项必须是已注册 Tool 或已连接 MCP server 的工具名 |
| `mcp_servers[]` | string[] | 否 | 每项必须存在于 `mcp_servers.yaml` |
| `channels[]` | object[] | 否 | `{name, config}`；核心阶段仅 `cli` |
| `bootstrap[]` | string[] | 否 | 缺省全启用；仅可选 AGENTS/SOUL/USER |
| `settings.max_iterations` | int | 否 | 默认 10；≥1 |
| `settings.max_history_turns` | int | 否 | 默认 20；≥1 |
| `settings.session_timeout_minutes` | int | 否 | 默认 30；≥1（澄清 #2） |
| `schedules[]` | string[] | 否 | cron 表达式，加载期校验合法性 |

正文 = 任务指令（Markdown）。`skills/` 下相对软连接表达 Skill 绑定。`REFERENCE.md`、`scripts/` 可选。

**加载失败处理**：frontmatter 非法/必填缺失 → 启动或命令执行时清晰报错，不产生半派生 Profile（FR-008、FR-003）。

## 2. Profile（运行时宿主配置，派生对象）

由 `AgentLoader.deriveProfile()` 从 frontmatter 派生，不落盘、不手写。

- 关联 Agent：`agent_name`、`provider(name/model/temperature)`、`tools[]`、`mcp_servers[]`、`channel`、`settings{max_iterations, max_history_turns, session_timeout_minutes}`、`schedules[]`
- 生命周期：随 Agent 加载创建，重启后重新派生（配置重启生效，FR-028）

## 3. ProviderConfig（Provider 注册项，文件形态）

`config/` 下 YAML，密钥仅 `${ENV_VAR}` 占位。

| 字段 | 类型 | 校验 |
|---|---|---|
| `name` | string | 映射主键（deepseek/kimi/qwen…），唯一 |
| `model` | string | 非空 |
| `api_key` | string | MUST 形如 `${ENV_VAR}`；环境变量缺失 → 启动报错（FR-008） |
| `base_url` | string | 可选；OpenAI 兼容端点 |

## 4. McpServerConfig（`mcp_servers.yaml`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `servers[]` | object[] | 每项 `{name, transport(stdio|http), command/url, args/env}` |
| 约束 | — | `name` 唯一；连接失败的 server 记日志并跳过，不阻断启动 |

## 5. Session（SQLite `sessions` 表）

| 字段 | 类型 | 说明 |
|---|---|---|
| `session_id` | VARCHAR PK | channel+user+profile 联合派生（需求文档 §10） |
| `profile_name` | VARCHAR | 关联 Agent |
| `channel` | VARCHAR | `cli` / `http` / `scheduler` |
| `user_id` | VARCHAR | 用户标识 |
| `messages_json` | TEXT | 对话历史（含 tool 消息） |
| `status` | VARCHAR | `active` / `archived` |
| `created_at` | TIMESTAMP | 创建时间 |
| `last_active_at` | TIMESTAMP | 每次消息更新 |
| `archived_at` | TIMESTAMP NULL | 归档时间 |

**状态迁移**：

```
(无活跃会话) --首条消息--> active --每条消息--> active（last_active_at 刷新）
active --闲置 ≥ session_timeout_minutes（默认30，Agent 可覆盖）--> archived（澄清 #2）
active --REST DELETE /api/v1/sessions/{id}--> archived（手动归档）
archived --CLI 渠道再来消息--> 自动开启新会话（FR-029）
archived --REST 再发消息--> 拒绝：明确错误，引导创建新会话（FR-029）
```

**上下文约束**：超出模型窗口 → 截断早期保留近期；归档不删数据，历史可查（FR-017）。

## 6. Memory（长期记忆，文件形态）

- 路径：`.oryxos/memory/MEMORY.md`，全实例共享一个文件
- 写入：`save_memory(content)` 追加；读取：`recall_memory(query)` 大小写不敏感包含匹配，返回命中条目/行（澄清 #5）
- 注入：启动/每轮 system prompt 注入全文，超 4000 字截断（FR-018）
- 无结构化 schema；非数据库表

## 7. ToolInvocation（SQLite `tool_invocations` 表，审计）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 自增 |
| `session_id` | VARCHAR | 关联 Session |
| `tool_name` | VARCHAR | 工具名 |
| `input_json` | TEXT | 调用参数 |
| `result_json` | TEXT | 执行结果 |
| `success` | BOOLEAN | 成败 |
| `error_message` | TEXT NULL | 错误信息 |
| `duration_ms` | BIGINT | 耗时 |
| `created_at` | TIMESTAMP | 时间 |

写入规则：每次工具调用一行，**先审计后回传**；白名单拦截也落一行（success=false，error=violation 详情）——支撑 SC-003/006。含重试时每次尝试一行（FR-014 可追溯）。

## 8. LlmCall（SQLite `llm_calls` 表，审计）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 自增 |
| `session_id` | VARCHAR | 关联 Session |
| `provider` | VARCHAR | Provider 名 |
| `model` | VARCHAR | 模型名 |
| `prompt_tokens` | INT | 输入 token |
| `completion_tokens` | INT | 输出 token |
| `total_tokens` | INT | 合计 |
| `duration_ms` | BIGINT | 耗时 |
| `created_at` | TIMESTAMP | 时间 |

写入规则：ReAct 每轮 LLM 调用一行（FR-025）；`session_id` 允许为系统级调用空置。

## 9. Skill（公共技能，文件形态）

- 实体：`.oryxos/skills/<name>/SKILL.md`（含 name/description 元数据）+ 可选附属资源
- 绑定：Agent 目录 `skills/<name>` 相对软连接；仅元数据进 system prompt，正文经 `read_file` 按需读取
- 硬约束：Skill 不进 `ToolRegistry`（宪法 VIII，FR-005）

## 10. Workspace（`.oryxos/` 布局）

```
.oryxos/
├── agents/<name>/        # Agent 目录（AGENT.md + skills/ + scripts/ + REFERENCE.md）
├── skills/<name>/        # 公共 Skill 库
├── memory/MEMORY.md      # 长期记忆
├── output/               # Agent 产出物
├── sessions/             # 会话附属数据（正文存 oryxos.db）
├── logs/                 # 结构化日志
├── AGENTS.md / SOUL.md / USER.md   # Bootstrap
└── oryxos.db             # SQLite（三表）
```

`oryxos init` 幂等：已存在目录/文件一律不覆盖（FR-001）。

## 实体关系

```
Agent(1) ──派生──> Profile(1) ──绑定──> ProviderConfig(1)
  │                    │
  │ skills/软连接       └──<使用>──> ToolRegistry(内置 Tool ∪ MCP Tool)
  v
Session(N) ──1:N──> ToolInvocation（审计）
    │
    └──────1:N──> LlmCall（审计）
Memory(共享文件) <──读写── save_memory/recall_memory Tool
Skill(N) ──软连接绑定──> Agent(N:M)
```
