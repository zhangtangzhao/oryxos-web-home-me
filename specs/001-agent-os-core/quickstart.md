# Quickstart: 端到端验证指南

**Feature**: `001-agent-os-core` | **Date**: 2026-09-02 | **用途**: 核心阶段验收与发布前人工验证（对应 spec Success Criteria）

## 前置条件

1. JDK 21+、Maven 3.9+
2. 有效 LLM 密钥至少一家（DeepSeek 或 Kimi），以环境变量注入（如 `DEEPSEEK_API_KEY`），**不得**写入任何文件
3. 可公网访问的天气信息源域名、企业 IM 群机器人 Webhook 地址（Demo 一用）
4. 构建产物：`mvn clean package` → `oryxos-boot/target/oryxos-boot-1.0.0.jar`（bin/ 脚本包装为 `oryxos`）

---

## 场景 1：初始化与首个 Agent（→ SC-001，30 分钟内完成部署+首对话）

```bash
oryxos init                 # 生成 .oryxos/ 工作区；重复执行应全部跳过不覆盖（FR-001）
oryxos profile create hello # 生成 AGENT.md 模板
# 编辑 .oryxos/agents/hello/AGENT.md：provider 指向已配密钥的厂商，tools 先留空
oryxos chat --profile hello
> 你好，介绍一下你自己       # 得到流利答复即通过；/exit 退出
```

**通过标准**：全程 ≤ 30 分钟；`oryxos status` 显示 1 个 Agent、1 个 Provider、数据库正常。

## 场景 2：ReAct 工具任务（→ US1 验收、SC-003）

1. 编辑 `hello/AGENT.md`：`tools` 加入 `http_get`，配置域名白名单含天气站点
2. `oryxos chat --profile hello`，下达：`查询北京今天的天气，并给出一句穿衣建议`
3. 交互内执行 `/tools` 查看：应看到 ≥1 次 `http_get` 调用及结果

**通过标准**：答复综合了工具返回的真实数据；`oryxos session list` 可见该会话；审计可查（场景 6 验证）。

## 场景 3：安全沙箱拦截（→ SC-006，100% 拦截）

```text
> 读取 /etc/passwd 的内容        # 白名单外路径 → 拒绝执行并向用户说明
> 执行 curl http://example.com   # 白名单外命令/域名 → 拒绝
```

**通过标准**：两次均被拦截、无实际副作用；拦截行为有审计记录（success=false）。

## 场景 4：跨对话记忆（→ US3、SC-007）

1. 对话中说：`记住：我偏好简洁的回复风格`（引导 Agent 调用 `save_memory`）
2. `oryxos chat --profile hello` 重开会话，问：`我的回复风格偏好是什么？` —— 无需重复解释即答对（FR-018 包含匹配可查 `MEMORY.md` 验证）
3. 会话保持打开 >30 分钟后发新消息（或将 Agent `session_timeout_minutes` 临时调 1 分钟验证）→ 自动归档；再发消息自动开新会话（FR-029 CLI 侧）
4. 重启进程后继续最近会话 → 历史完整、上下文连贯（FR-017）

## 场景 5：REST API 闭环（→ US4、SC-009）

```bash
oryxos serve --port 8080
curl -s localhost:8080/api/v1/health | jq .data.status        # UP
curl -s -X POST localhost:8080/api/v1/sessions -d '{"profile":"hello","user_id":"u1"}'
curl -s -X POST localhost:8080/api/v1/sessions/<id>/messages -d '{"content":"查询上海天气"}'
curl -s localhost:8080/api/v1/sessions/<id>                    # 历史含本轮对话
curl -s -X DELETE localhost:8080/api/v1/sessions/<id>          # 归档
curl -s -X POST localhost:8080/api/v1/sessions/<id>/messages -d '{"content":"在吗"}'   # 期望 409（FR-029）
curl -s -X POST localhost:8080/api/v1/agents/hello/invoke -d '{"message":"1+1=?","user_id":"u1"}'
curl -s localhost:8080/api/v1/profiles; curl -s localhost:8080/api/v1/tools
curl -s "localhost:8080/api/v1/memory?query=偏好"; curl -s localhost:8080/api/v1/info
```

**通过标准**：10 端点全部按 [contracts/rest-api.md](contracts/rest-api.md) 契约返回。

## 场景 6：审计追溯（→ SC-003）

```bash
sqlite3 .oryxos/oryxos.db "select tool_name, success from tool_invocations order by id desc limit 10;"
sqlite3 .oryxos/oryxos.db "select provider, model, total_tokens from llm_calls order by id desc limit 10;"
```

**通过标准**：场景 2–5 的每次 LLM/Tool 调用均有记录；场景 3 的拦截记录 success=false 带错误信息。

## 场景 7：Provider 无锁定切换（→ US5、SC-004）

1. `config/` 增配第二家 Provider（如 Kimi，密钥走独立环境变量）
2. 仅修改 `hello/AGENT.md` 的 `provider.name/model`，重启 `oryxos serve`
3. 重复场景 2 的任务 → 正常完成；`llm_calls` 最新记录 provider/model 已切换；`oryxos provider list` 不回显密钥明文
4. 临时填错密钥环境变量后启动 → 启动失败并给出清晰报错（FR-008）

## 场景 8：零代码 MCP 扩展（→ SC-005）

1. `mcp_servers.yaml` 注册任一社区 MCP server（如 github-mcp，token 走 `${GITHUB_TOKEN}`）
2. 新建 Agent：`oryxos profile create gh-agent`，frontmatter `mcp_servers: [github-mcp]`，正文写任务指令
3. `oryxos chat --profile gh-agent` 下达：`列出 oryxos 仓库最新的 3 个 issue`
**通过标准**：全程仅写配置文件，零 Java 代码；`oryxos tool list` 出现 MCP 工具且调用经白名单+审计。

## 场景 9：两个每日 Demo（→ SC-008，发布硬条件）

| Demo | 配置要点 | 通过标准 |
|---|---|---|
| 每日天气 | Agent 配 `schedules`（如每日 07:00）、`http_get`（天气域名白名单）、`notify`（IM 群 Webhook 域名白名单） | 次日早晨无人工干预自动完成"查天气→生成建议→群推送"；`tool_invocations` 含两次 HTTP 调用；`GET /api/v1/sessions/{id}` 可查该自动会话；`oryxos chat` 手动补跑走同一链路成功 |
| 每日科技日报 | Agent 配 `schedules` + `mcp_servers`（新闻类）+ `save_memory/recall_memory`；提前对话告知"更关注 AI 和芯片" | 到点自动产出日报且体现已记住的关注方向；全程零代码；手动补跑成功 |

---

## 失败排查速查

| 症状 | 首查 |
|---|---|
| 启动报 Provider/密钥错误 | 环境变量是否注入、`${ENV_VAR}` 占位是否拼写正确（FR-008） |
| 工具全部被拦 | Agent 域名/路径/命令白名单配置 |
| 会话历史丢失 | 是否误删 `oryxos.db`；重启后 `oryxos session list` 核对 |
| 定时任务未触发 | `oryxos status` 调度器状态；cron 表达式合法性；schedules 是否在 frontmatter 内 |
