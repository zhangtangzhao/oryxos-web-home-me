# Contract: REST API（核心阶段 10 端点）

**Feature**: `001-agent-os-core` | **Base URL**: `http://<host>:8080`（`oryxos serve --port` 可改） | **Auth**: 无（内网假设，spec Assumptions）

## 通用约定

- 所有响应体为统一信封：

```json
{ "success": true, "data": { }, "error": null }
```

- 失败时：`success=false`，`data=null`，`error={code, message}`；HTTP 状态码同步表达（400 参数非法 / 404 资源不存在 / 409 已归档会话发消息 / 500 内部错误）
- 时间戳 ISO-8601；内容类型 `application/json; charset=utf-8`
- 行为约束：向已归档会话发消息返回 409（FR-029）；Agent 无状态调用与会话调用走同一 `AgentService` 链路（FR-021）

## 端点

### 1. 创建会话 — `POST /api/v1/sessions`

```json
// Request
{ "profile": "ops-assistant", "user_id": "u1001" }
// Response 200
{ "success": true, "data": { "session_id": "cli-u1001-ops-assistant", "profile_name": "ops-assistant", "status": "active", "created_at": "2026-09-02T10:00:00Z" } }
```

错误：400（profile 不存在或非法）。

### 2. 发送消息 — `POST /api/v1/sessions/{id}/messages`

```json
// Request
{ "content": "帮我查一下北京今天的天气并给出穿衣建议" }
// Response 200（ReAct 循环完成后的最终响应）
{ "success": true, "data": { "session_id": "…", "reply": "今天北京晴，26°C…", "tool_calls": [ { "tool": "http_get", "success": true } ], "iterations": 2 } }
```

错误：404（会话不存在）、409（会话已归档）。

### 3. 查询历史 — `GET /api/v1/sessions/{id}`

```json
// Response 200
{ "success": true, "data": { "session_id": "…", "profile_name": "…", "status": "active", "messages": [ { "role": "user|assistant|tool", "content": "…", "created_at": "…" } ] } }
```

### 4. 归档会话 — `DELETE /api/v1/sessions/{id}`

`200`：`data: { "session_id": "…", "status": "archived", "archived_at": "…" }`；幂等：重复归档仍返回 200。错误：404。

### 5. 无状态调用 — `POST /api/v1/agents/{name}/invoke`

```json
// Request
{ "message": "总结这份输入：…", "user_id": "u1001" }
// Response 200
{ "success": true, "data": { "agent": "ops-assistant", "reply": "…", "iterations": 1 } }
```

不创建持久会话；但 LLM/Tool 审计照常落库（`session_id` 为本次调用的临时标识）。错误：404（Agent 不存在）。

### 6. 列 Profile（Agent）— `GET /api/v1/profiles`

```json
{ "success": true, "data": { "profiles": [ { "name": "ops-assistant", "description": "…", "provider": "deepseek", "model": "deepseek-chat", "tools": ["http_get"] } ] } }
```

### 7. 查长期记忆 — `GET /api/v1/memory`

可选 `?query=<keyword>`（包含匹配，同 FR-018）；无 query 返回注入视图（截断后全文）。

```json
{ "success": true, "data": { "total_chars": 1234, "truncated": false, "items": [ { "content": "用户偏好…", "matched": true } ] } }
```

### 8. 列可用 Tool — `GET /api/v1/tools`

```json
{ "success": true, "data": { "tools": [ { "name": "read_file", "type": "builtin|memory|http|shell|mcp", "description": "…" } ] } }
```

### 9. 健康检查 — `GET /api/v1/health`

```json
{ "success": true, "data": { "status": "UP", "workspace": "/path/.oryxos", "agents_loaded": 2, "db_ok": true } }
```

### 10. 运行信息 — `GET /api/v1/info`

```json
{ "success": true, "data": { "name": "oryxos", "version": "1.0.0", "java_version": "21", "uptime_seconds": 3600 } }
```

## 审计与可观测约定（跨端点）

- 上述任何触发 LLM/Tool 的端点（2、5）均产生 `llm_calls` / `tool_invocations` 记录（SC-003）
- 所有请求落结构化日志（FR-027）
