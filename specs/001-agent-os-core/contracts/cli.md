# Contract: CLI（核心阶段 12 命令）

**Feature**: `001-agent-os-core` | **入口**: `oryxos`（bin 脚本 → 单可执行 JAR） | **框架**: Picocli

## 通用约定

- 退出码：`0` 成功；`1` 一般错误（含校验失败、工作区未初始化）；`2` 用法错误（参数/子命令不存在）
- 配置变更（`AGENT.md`、白名单、Provider、`mcp_servers.yaml`）在下次启动/新会话生效，无热加载（FR-028）
- 所有命令在当前目录无 `.oryxos/` 工作区时，除 `init` 外均报错退出（清晰提示先执行 `oryxos init`）
- `--help` 全命令可用，含中文说明与示例

## 启动与状态（5）

### `oryxos init`
在当前目录创建 `.oryxos/` 工作区（六个子目录 + 三个 Bootstrap 文件，见 agent-workspace.md）。幂等：已存在一律跳过不覆盖，输出创建/跳过清单。

### `oryxos status`
输出工作区路径、Agent 数量、已配置 Provider、会话统计（active/archived）、数据库与调度器状态。

### `oryxos chat [--profile <name>] [--message "<text>"]`
交互多轮对话（默认取第一个可用 Agent）；`--message` 发送单条消息后退出。交互内命令：`/tools` 查看工具调用记录、`/exit` 退出。会话标识 = `cli+<user>+<agent>`；无活跃会话自动新建（FR-029）。

### `oryxos serve [--port 8080]`
前台启动 REST 服务（端点契约见 rest-api.md）。启动时校验全部 Agent/Provider 配置，非法即退出（exit 1，清晰报错）。

### `oryxos gateway`
常驻守护进程：REST 服务 + 定时任务（`AgentScheduler`）同进程运行，服务多渠道（核心阶段=CLI 已由 chat 承担、REST、scheduler）。

## Profile 管理（4）——操作 `.oryxos/agents/` 下的 Agent 目录

| 命令 | 行为 |
|---|---|
| `oryxos profile create <name>` | 生成 `.oryxos/agents/<name>/AGENT.md` 最小模板；重名报错（exit 1） |
| `oryxos profile list` | 列出全部 Agent：name / provider / model / tools 数 |
| `oryxos profile show <name>` | 输出该 Agent 的 `AGENT.md` 全文；不存在报错（exit 1） |
| `oryxos profile delete <name>` | 删除整个 Agent 目录（含软连接，不动公共 Skill 库）；不存在报错 |

## 查询（3）

| 命令 | 行为 |
|---|---|
| `oryxos provider list` | 列出已配置 Provider：name / model / base_url / 密钥来源环境变量名（永不回显密钥明文，FR-008） |
| `oryxos tool list` | 列出已注册 Tool：name / 来源（builtin/mcp） / 白名单摘要 |
| `oryxos session list [--profile <name>]` | 列出会话：session_id / profile / channel / status / last_active_at |
