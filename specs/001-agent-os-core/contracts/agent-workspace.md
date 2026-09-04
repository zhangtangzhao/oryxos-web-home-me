# Contract: Agent 定义与工作区文件格式

**Feature**: `001-agent-os-core` | **约束**: 一个目录 = 一个 Agent（宪法 VII）；密钥只走环境变量（宪法 VI）

## 1. `AGENT.md`（Agent 唯一 manifests）

位置：`.oryxos/agents/<name>/AGENT.md`。由 YAML frontmatter + Markdown 正文组成；`name` 必须等于目录名。完整字段与校验规则见 [../data-model.md](../data-model.md) §1。

```markdown
---
name: daily-weather
description: 每天早上推送天气与穿搭建议
identity:
  agent_name: 天气助手
  prompt: 你是一个贴心的生活助手
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.7            # 可选
tools:
  - http_get
  - notify
  - save_memory
  - recall_memory
mcp_servers: []                # 可选：引用 mcp_servers.yaml 中的 server 名
channels:
  - name: cli                  # 核心阶段仅 cli
bootstrap:                     # 可选：缺省 AGENTS/SOUL/USER 全启用
settings:
  max_iterations: 10           # 默认 10
  max_history_turns: 20
  session_timeout_minutes: 30  # 澄清 #2：默认 30
schedules: []                  # 可选：cron 表达式，如 "0 7 * * * ?"
---

（正文 = 任务指令，例如：每天早上查询所在城市天气，生成穿搭建议，经 notify 推送。）
```

硬性规则：
- frontmatter 是唯一运行配置来源，由 `AgentLoader.deriveProfile()` 派生 Profile，不存在第二份手写配置（FR-003）
- Skill 绑定不写进 frontmatter，由目录内 `skills/` 相对软连接表达
- 正文经 ContextLoader 注入 system prompt 首位（research R-7 顺序）

## 2. `mcp_servers.yaml`（MCP server 注册表，工作区级）

```yaml
servers:
  - name: github-mcp
    transport: stdio            # stdio | http
    command: npx
    args: ["-y", "@modelcontextprotocol/server-github"]
    env:                        # 仅 ${ENV_VAR} 占位
      GITHUB_TOKEN: ${GITHUB_TOKEN}
  - name: weather-mcp
    transport: http
    url: https://mcp.example.com/weather
```

`name` 全工作区唯一；连接失败的 server 记日志跳过，不阻断启动（data-model §4）。

## 3. `MEMORY.md`（长期记忆，全工作区唯一）

- 路径 `.oryxos/memory/MEMORY.md`；Markdown 自由文本，按条追加写入
- `save_memory(content)`：追加一行/一节；`recall_memory(query)`：大小写不敏感包含匹配，返回命中条目（澄清 #5）
- 注入 system prompt 时超 4000 字截断（FR-018）

## 4. Bootstrap 文件（工作区级）

| 文件 | 角色 | 缺省行为 |
|---|---|---|
| `AGENTS.md` | 项目级 agent 行为说明 | 全 Agent 注入 |
| `SOUL.md` | 默认 agent 人格 | Agent `identity.prompt` 优先 |
| `USER.md` | 用户偏好 | 全 Agent 注入 |

system prompt 组装顺序（research R-7）：`AGENT.md` 正文 → `AGENTS.md` → `SOUL.md` → `USER.md` → 绑定 Skill 元数据 → `MEMORY.md` 全文 → 对话历史 + Tool 列表。

## 5. Skill 实体（`.oryxos/skills/<name>/`）

```
skills/weather-style/
├── SKILL.md        # frontmatter：name / description（元数据，注入 prompt 的唯一内容）
└── resources/      # 可选附属资源，经 read_file 按需读取
```

绑定：`.oryxos/agents/<name>/skills/<skill-name>` 相对软连接。Skill 不进 ToolRegistry（宪法 VIII）。

## 6. 工具白名单配置（Agent/工作区级）

- 文件类（`read_file`/`write_file`/`list_dir`）：允许路径前缀清单（缺省 = 工作区内）
- Shell（`shell`）：允许命令前缀清单 + 超时秒数
- HTTP/notify（`http_get`/`http_post`/`notify`）：允许域名清单
- 白名单外操作 100% 拦截并落审计（FR-013、SC-006）
