# Contract: REST API — 知识库管理

**Base**: `/api/v1/kbs` | **响应包裹**: 既有 `ApiResponse{success, data, error}`
**鉴权**: 沿用现状（内网部署，无鉴权——与既有 10 端点一致）

## 端点

### POST /api/v1/kbs — 创建知识库

请求：`{"name": "product-docs", "description": "产品文档"}`

- 201 `ApiResponse.ok({name, description, document_count: 0})`
- 409 `KB_CONFLICT` 重名
- 400 `INVALID_ARGUMENT` 名称不符 `[a-z0-9][a-z0-9_-]{0,63}`

### GET /api/v1/kbs — 列出知识库

- 200 `ApiResponse.ok([{name, description, document_count, ready_count,
  failed_count, embedding_model, updated_at}])`

### GET /api/v1/kbs/{name} — 状态详情

- 200 `ApiResponse.ok({name, description, embedding_model, embedding_dimensions,
  documents: [{doc_path, status, chunk_count, size_bytes, error_message,
  ingested_at}], created_at, updated_at})`
- 404 `KB_NOT_FOUND`

### POST /api/v1/kbs/{name}/documents — 添加文档

请求（二选一）：`{"path": "/abs/or/rel/file.md"}`（复制文件入工作区）或
`{"filename": "faq.md", "content": "...文本..."}`（直传内容）。

- 202 `ApiResponse.ok({doc_path, status: "pending", content_hash})`
- 404 `KB_NOT_FOUND`；400 路径不存在/格式不支持（仅 `.md`/`.markdown`/`.txt`）/重名文件
  （409 `KB_CONFLICT`）

### POST /api/v1/kbs/{name}/ingest — 执行摄取（手动，spec 澄清 4）

- 200 `ApiResponse.ok({processed: 2, skipped: 8, removed: 0, failed: 0,
  duration_ms: 15230, documents: [{doc_path, status, chunk_count}]})`
- 404 `KB_NOT_FOUND`；409 `EMBEDDING_MISMATCH` 嵌入模型身份与库记录不一致
  （提示重建）；502 `EMBEDDING_UNAVAILABLE` 嵌入服务调用失败（可重试，
  文档留在 pending）

### DELETE /api/v1/kbs/{name} — 删除知识库

- 200 `ApiResponse.ok({name, removed: true})`（级联：文档/分段/FTS/目录）
- 404 `KB_NOT_FOUND`

### GET /api/v1/kbs/{name}/overview — 结构总览

- 200 `ApiResponse.ok({name, document_count, documents: [{doc_path,
  headings: ["部署指南", "部署指南 > Windows", ...]}]})`
- 404 `KB_NOT_FOUND`

## 错误映射（GlobalExceptionHandler 增量）

| 异常 | 状态 | code |
|---|---|---|
| `KbNotFoundException` | 404 | `KB_NOT_FOUND` |
| `KbConflictException` | 409 | `KB_CONFLICT` / `EMBEDDING_MISMATCH` |
| 嵌入服务不可用（`KbIngestService` 包装） | 502 | `EMBEDDING_UNAVAILABLE` |

**未配置嵌入服务的行为**（spec 澄清 2）：创建知识库本身允许（无索引操作），
但**首次 ingest 返回 503 `EMBEDDING_NOT_CONFIGURED`**，message 点名缺失的
`oryxos.kb.embedding.*` 配置项。检索（工具）侧同名反馈为面向模型的字符串。
