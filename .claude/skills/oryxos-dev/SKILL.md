---
name: oryxos-dev
description: OryxOS 项目开发技能。凡是在本仓库做任何开发工作都必须使用：写/改 Java 模块代码（ReAct、Provider、Memory、Tool、Sandbox、Web、CLI、Storage）、定义或调试 Agent 目录（AGENT.md）、构建打包运行验证、排查启动或会话问题、新增内置 Tool 或 Provider、演进 SQLite 表结构。
---

# OryxOS 开发技能

操作手册级别的工作流知识。概念与约束见根目录 `CLAUDE.md`（宪法、模块职责、常见陷阱），本技能不重复，只补"怎么动手"。

## 权威文档地图

回答任何设计问题前先查对应文档章节，不要凭空发明机制：

| 主题 | 出处 |
|------|------|
| 关键技术决策（7 条）、Spring AI 边界 | `docs/TechnicalSolution.md` §1.1 |
| 各能力实现细节 | 同文件 §3~§8（Provider=§3, ReAct=§4, Memory=§5, Tool/Sandbox/Notify=§6, Web=§7） |
| 数据库表结构 | §9.2；数据只放 `.oryxos/` 下的规则见 §9.3 |
| Agent 目录与 Skill 绑定语义 | §11 |
| 三个验收 Demo | §12（天气=光杆/科技日报=+Skill 软连接/GitHub 日报=+scripts） |
| CLI 行为细节 | `docs/CliGuide.md` |
| 每个特性的原理解析课 | `docs/class/第16节~第32节` |

## 构建与运行

```bash
# 构建 fat jar（仓库根目录；只编 boot 及其依赖链）
mvn -pl oryxos-boot -am package -DskipTests

# 运行入口（JAR 位于 oryxos-boot/target/）
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar <command>
```

- **仓库当前没有单元测试**（无 `src/test`），改动正确性的验证手段 = 构建通过 + 端到端跑通；新增测试放各模块 `src/test/java`
- 发布走 `./scripts/package.sh [version]`：clean 打包 + 构建 VitePress 官网 + 汇总 `release/` 归档
- 改官网/文档站：`cd website && npm run docs:dev` 本地预览；服务启停脚本在 `bin/start.sh` / `stop.sh`

- 所有命令默认在**当前目录**找 `.oryxos/` 工作区与 `oryxos.db`——验证时固定用同一目录，否则会话查不到
- 工作区根覆盖：环境变量 `ORYXOS_ROOT` 或 `-Doryxos.root=<path>`，配置的根自动加入文件沙箱白名单
- 对外配置在 `config/application.yml`（勿提交真实 key；安全模板为 `config/application.yml.example`）

**轻/重命令**决定验证成本：轻命令（init/status/profile/provider/tool/session list）不起 Spring、亚秒返回，适合快速核对文件与状态；重命令（chat/serve/gateway）起完整运行时（2~4s），改动涉及引擎/Provider/存储时才需要。

## 端到端验证一套新功能

```bash
rm -rf /tmp/ws && ORYXOS_ROOT=/tmp/ws java -jar <jar> init          # 全新工作区，防脏数据
export DEEPSEEK_API_KEY=sk-xxx                                      # 凭证只走环境变量
ORYXOS_ROOT=/tmp/ws java -jar <jar> profile create demo             # 建 Agent
ORYXOS_ROOT=/tmp/ws java -jar <jar> chat --profile demo             # 重命令跑对话
curl http://localhost:8080/api/v1/health                            # serve 后冒烟
```

验收三个 Demo（§12）是标准回归：能完整跑通"定时触发 → http_get/shell → notify 推送 → Session 可查"即核心链路无恙。

## 常见改动的落点与自检

### 改动涉及 Tool（新增内置 Tool 等）

1. 新 Tool 实现 `OryxTool` 四方法（`getName/getDescription/getInputSchema/execute`），注册进 `ToolRegistry`
2. `execute` 开头必须过 `Sandbox.enforce(SandboxAction{type, target})`——FILE_READ/FILE_WRITE/SHELL_COMMAND/HTTP_REQUEST 四值之一；拒绝抛 `SandboxViolationException`
3. 白名单默认值在 `config/application.yml` 的 `oryxos.sandbox` 段（首启种子进 SQLite）
4. 自检：执行经 `ToolExecutor` 会写入 `tool_invocations`（含失败），不要绕过这条审计路径

### 改动涉及 LLM 调用

- 只用 Spring AI 做**协议转换 + `@Tool` schema 生成**；其自动 tool 执行必须禁用（否则 tool 被调两次，这是最高频 bug）
- 多 Provider 靠显式 `Map<providerName, ChatModel>` 映射，声明在 `application.yml` 的 `oryxos.providers`（name/base-url/api-key-env）；Profile 只引用 name
- api-key 缺失时启动要**点名报错**，不允许静默

### 改动涉及 ReAct 循环 / Prompt 组装

- 循环顺序：追加历史 → PromptBuilder（system prompt 含当前日期时间 + Bootstrap + Memory + 截断后的历史 ≤ max_history_turns 默认 20 + Tool 列表）→ LLM → 有 Tool 调用则执行回填继续，否则返回；上限 max_iterations 默认 10
- 所有触发源（cli/web/scheduler）都汇入同一个 `AgentService.process`；不要给某一入口单独造链路
- 会话身份 = `channel:user:profile` 三元组拼一个 session_id，钟推固定 `scheduler:scheduler:<profile>`

### 改动涉及数据库表

- `hibernate.ddl-auto=update` 在 SQLite 上对**已有表演进无效**——改字段/加列必须手写建表脚本或迁移逻辑，不能指望 update 自动迁移
- 核心五表：`sessions` / `tool_invocations` / `llm_calls` / `scheduled_tasks` / `task_executions`；装配是否完整看启动日志 `Found 3 JPA repository interfaces`（少于 3 属于装配残缺 bug）

### 定义/修改一个 Agent（业务侧）

Agent = `.oryxos/agents/<name>/` 目录，核心 `AGENT.md`：

- YAML frontmatter 派生 Profile，可用字段：`name`、`description`、`identity`(agent_name/prompt)、`provider`(name/model/temperature)、`tools`、`mcp_servers`、`channels`、`schedules`(cron/zone/message)、`bootstrap`、`settings`(max_iterations/max_history_turns)。**没有** `skills:` 字段——Skill 可见性由 `skills/<name>` 相对软连接决定（指向公共实体 `.oryxos/skills/<name>/`），**没有** `notify_channels` 字段——通知渠道走全局注册表按名引用
- 正文 = 任务指令，由 `ContextLoader` 注入 system prompt；正文每次现读、改完下轮即生效，无需重启
- `profile create <name>` 生成最小模板，直接编辑即可

## 排障速查

| 现象 | 先查 |
|------|------|
| chat 报 Profile 不存在 | `oryxos status` + `profile list`；确认运行目录对不对 |
| 报 api-key 未解析 | 对应 env 是否 export；providers 的 `api-key-env` 名字对不对 |
| session list 无会话 | 库在当前目录 `.oryxos/oryxos.db`，回当初运行的目录 |
| tool 执行被拒 | `SandboxViolationException` 信息 + SQLite 里 sandbox 白名单实际生效值 |
| 轻命令很慢 | 说明误起了 Spring，属回归 bug |