# Data Model: Web 管理台知识库页面

**Feature**: specs/003-kb-admin-ui | **Date**: 2026-09-10

> 本特性**零新表、零列变更**。以下实体均为既有存储的只读/受管映射，供 tasks 与测试对齐字段语义。

## 实体

### 知识库（既有 `kb_knowledge_bases`，只读 + 受管）

| 字段 | 来源 | 页面用途 |
|---|---|---|
| name（唯一标识） | `KbRecord.name()` | 列表主键、路由参数 |
| description | `KbRecord.description()` | 列表/详情展示 |
| embedding_model / embedding_dimensions | `KbRecord.embeddingModel()/embeddingDimensions()` | 身份展示（US1），身份变更提示（边界） |
| document_count / ready_count / failed_count | 由 `kb_documents` 按状态聚合（`KbApiController.summary` 现状） | 列表健康度 |
| created_at / updated_at | `KbRecord.createdAt()/updatedAt()` | 列表/详情时间线 |

### 文档（既有 `kb_documents`，受管）

| 字段 | 语义 | 状态机 |
|---|---|---|
| doc_path（库内唯一） | 库内相对路径 | — |
| status | `PENDING / READY / FAILED`（REST 输出小写） | 添加→pending；ingest 成功→ready / 失败→failed；重跑 ingest 可 ready/failed 复位（US2/FR-006） |
| chunk_count / size_bytes / ingested_at | 摄取产物元信息 | 随 ingest 更新 |
| error_message | 失败原因（FR-006 人话展示） | failed 时非空 |

### 摄取结果（瞬时，不落新表）

`POST /{name}/ingest` 响应体：`{processed, skipped, removed, failed, duration_ms, documents[{doc_path, status, chunk_count}]}`——仅用于页面渲染，来源 `KbIngestService.IngestSummary`。

### 试检索结果（瞬时，本特性新增视图形状）

```
TrialSearchResponse {
  kb: string,  query: string,
  zero_result: boolean,  degraded: boolean,  degraded_reason: string|null,
  duration_ms: number,
  hits: [ { doc_path, heading_path, chunk_ordinal, score, content } ]   // 来自 SearchResult.hits（R2 扩展）
}
```

审计侧（`tool_invocations`，既有表写入一行）：session_id=`admin-ui`、tool_name=`kb_search`、input_json=`{kb, query, top_k}`、result_json=既有结构化 auditJson（kb/results_count/zero_result/degraded/degraded_reason/top_scores/duration_ms/results[]）——与 Agent 工具路径同 schema。

## 校验规则（边界 → 契约）

- 建库名称：`[a-z0-9][a-z0-9_-]{0,63}`；重名 → 409 `KB_CONFLICT`（既有）
- 加文档：`.md`/`.markdown`/`.txt`；path 与 filename+content 二选一；重名 → 400/409（既有）
- 试检索：query 必填非空 → 400；top_k 1..20（超界钳制为既有 `clampTopK` 行为，不报错）
- 库不存在（详情/摄取/删除/试检索）→ 404 `KB_NOT_FOUND`（既有映射）

## 关系

- 知识库 1..* 文档（级联物理删除，澄清决策 2：不设回收站）
- 知识库 1..* 分段（`kb_chunks` + `kb_chunks_fts`，页面不直接呈现分段，只呈现 chunk_count 与命中片段）
