# Contract: Admin REST 增量 + 静态页契约 — 知识库管理台

**Feature**: specs/003-kb-admin-ui | **Date**: 2026-09-10
**信封**: 既有 `ApiResponse{success, data, error}`；错误映射走既有 `GlobalExceptionHandler`
**既有端点契约**: 不在此重复，见 [specs/002-agent-knowledge-base/contracts/rest-api.md](../../002-agent-knowledge-base/contracts/rest-api.md)

## 新增端点（唯一，只读）

### POST /api/v1/kbs/{name}/search — 试检索（US4 / FR-011 / FR-008 例外）

请求：

```json
{"query": "回收端口是多少", "top_k": 5}
```

- `query` 必填非空；`top_k` 可省略（默认 5，钳制 ≤20）
- 服务端强制单库检索：绑定集 = `{name}`（管理面权威指定，无跨库面）

成功 → 200：

```json
{"success": true, "data": {
  "kb": "product-docs", "query": "回收端口是多少",
  "zero_result": false, "degraded": false, "degraded_reason": null,
  "duration_ms": 41,
  "hits": [
    {"doc_path": "docs/doc1.md", "heading_path": "部署指南 > 端口",
     "chunk_ordinal": 2, "score": 0.83, "content": "……回收端口默认 9200……"}
  ]
}}
```

零命中 → 200（`hits: []`, `zero_result: true`），页面显示"无命中结果"，不报错。

失败分支（机器可读判别，见 research.md R2）：

| 条件 | 状态 | error code | 页面行为 |
|---|---|---|---|
| query 空/缺失 | 400 | `INVALID_ARGUMENT` | 表单校验提示 |
| 库不存在 | 404 | `KB_NOT_FOUND` | 提示并刷新列表 |
| `degraded_reason` 含 `embedding_model_mismatch` | 409 | `EMBEDDING_MISMATCH` | 展示"嵌入模型已变更，请恢复配置或重建知识库" |
| 嵌入未配置（`error=true` 未配置分支） | 503 | `EMBEDDING_NOT_CONFIGURED` | 展示缺失配置项提示 |
| 嵌入服务失败以外的检索异常 | 502 | `SEARCH_FAILED` | 展示原因 + 重试 |

## 静态页契约

| 路径 | 行为 |
|---|---|
| `GET /admin` | 302 → `/admin/index.html`（`AdminPageController`，避免落到 `NoResourceFoundException` 的 JSON 信封） |
| `GET /admin/index.html`、`/admin/app.js`、`/admin/style.css` | Spring Boot 默认静态资源（`oryxos-web` jar 内 `static/admin/`），200 `text/html` / `text/javascript` / `text/css` |
| `GET /admin/其他` | 既有 JSON 信封 404（可接受：页面内所有链接均为真实资源） |

## 页面动作 → 端点映射（US 覆盖表）

| 页面动作 | 端点 | US |
|---|---|---|
| 打开列表 | GET /api/v1/kbs | US1 |
| 打开详情 | GET /api/v1/kbs/{name} + GET /{name}/overview | US1 |
| 建库 | POST /api/v1/kbs | US2 |
| 加文档 | POST /api/v1/kbs/{name}/documents | US2 |
| 摄取/重新摄取 | POST /api/v1/kbs/{name}/ingest | US2 |
| 删除（确认弹窗后） | DELETE /api/v1/kbs/{name} | US3 |
| 试检索 | POST /api/v1/kbs/{name}/search（新增） | US4 |

## 审计一致性（SC-002 / 宪法 V）

- 经既有端点的管理动作：与 curl 直调完全同路径（无新增代码），审计一致
- 试检索：`tool_invocations` 新增一行（session_id=`admin-ui`，tool_name=`kb_search`，result_json=结构化 auditJson），与 Agent 工具路径同 schema、同 sink（`AuditLog.recordToolInvocation`）
