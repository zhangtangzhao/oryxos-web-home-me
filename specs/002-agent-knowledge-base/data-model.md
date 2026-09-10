# Data Model: 给 Agent 增加知识库

**Feature**: `002-agent-knowledge-base` | **Date**: 2026-09-09
**存储**: SQLite（既有 `oryxos.db`）| 建表: `ddl-auto=update`（新表）+ FTS5 手工建
**写路径**: 全部经 `SqliteWriteGate.write(...)`

## 实体关系总览

```text
Agent(Profile) --knowledge_bases: List<String>（按名引用，非外键）--> KnowledgeBase 1 ──* KbDocument 1 ──* KbChunk
                                                        │
                                                        └──* KbRetrievalRecord（审计，复用 tool_invocations）
文件系统镜像: <root>/kb/<name>/docs/**（原文）  evalset.yaml（评估集）
派生索引:    kb_chunks_fts（FTS5 虚拟表，可整表重建）
```

## KbEntity → 表 `kb_knowledge_bases`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| name | String | PK | 库名；全局唯一；`[a-z0-9][a-z0-9_-]{0,63}` |
| description | String | 可空 | 描述 |
| embedding_model | String | 非空 | 摄取所用嵌入模型名（FR-015 身份记录） |
| embedding_dimensions | int | 非空 | 嵌入维度（与模型绑定校验） |
| created_at | Instant | 非空 | |
| updated_at | Instant | 非空 | 最近一次摄取完成时间 |

**校验规则**: 重名创建 → `KbConflictException`（409）；名称不符正则 →
`IllegalArgumentException`（400）。嵌入模型身份变更后在旧索引上检索 →
`KbConflictException`（409，提示重建，FR-015）。

## KbDocumentEntity → 表 `kb_documents`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | String | PK | UUID |
| kb_name | String | 非空，索引 | 所属库（逻辑外键 → kb_knowledge_bases.name） |
| doc_path | String | 非空 | 库内相对路径（`docs/` 下），库内 (kb_name, doc_path) 唯一 |
| content_hash | String | 非空 | sha256 hex（FR-008 增量判定） |
| size_bytes | long | 非空 | |
| status | String | 非空 | `pending` / `ready` / `failed` |
| error_message | String | 可空 | failed 原因 |
| chunk_count | int | 非空 | ready 后回填 |
| ingested_at | Instant | 可空 | 最近成功摄取时间 |

## KbChunkEntity → 表 `kb_chunks`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | String | PK | UUID |
| document_id | String | 非空，索引 | → kb_documents.id（删除级联：应用层先删 chunks 再删 doc） |
| kb_name | String | 非空，索引 | 冗余存储，检索按库过滤免 join |
| chunk_ordinal | int | 非空 | 文档内序号（0 起） |
| heading_path | String | 非空 | 标题路径（如 `部署指南 > Windows`；纯文本为空串） |
| content | String(TEXT) | 非空 | 分段正文 |
| embedding | String(TEXT) | 非空 | float[] JSON（如 `[0.12,-0.33,...]`） |

**派生表** `kb_chunks_fts`（FTS5，`content=''` 无正文列模式）:
`chunk_id UNINDEXED, tokens` —— `tokens` 为 `content` 的二元切分文本
（CJK 2 字滑窗 + ASCII 整词小写，空格连接）。启动 `CREATE VIRTUAL TABLE
IF NOT EXISTS`；该表**可整表删除重建**（rebuild 路径），非事实来源。

## 检索审计：复用 `tool_invocations`（无新表）

`kb_search` 调用行的 `result_json` 固定结构：

```json
{
  "kb": "product-docs",
  "results_count": 5,
  "zero_result": false,
  "degraded": false,
  "degraded_reason": null,
  "top_scores": [0.83, 0.79, 0.74],
  "duration_ms": 412,
  "results": [
    {"kb": "product-docs", "doc_path": "docs/deploy.md",
     "heading_path": "部署指南 > Windows", "chunk_ordinal": 3, "score": 0.83}
  ]
}
```

`input_json` 记录模型传入的 `{query, kb?, top_k?}` 原样。SC-005 统计
→ `json_extract(result_json, '$.zero_result')` 等条件查询。

## 状态机

**KbDocument.status**:

```text
add ──> pending ──ingest 成功──> ready ──再次 ingest 且指纹不变──> ready（跳过，FR-008）
              │                      │
              │                   文件从 docs/ 消失后 ingest ──> 删除（行移除，无终态）
              └─ingest 失败──> failed ──再次 ingest──> pending（重试入口）
```

**KnowledgeBase 生命周期**: `create → active → delete`（删除时：chunks →
documents → KB 行 + FTS 行 + `<root>/kb/<name>/` 目录，一并清除；已绑定
Agent 的检索收到 `KbNotFoundException` 反馈，FR-016）。

## Agent 绑定（Profile 扩展）

`Profile` 新增 `List<String> knowledgeBases`（默认空），来源 frontmatter：

```yaml
knowledge_bases:
  - product-docs
  - faq
```

解析遵循 `AgentLoader.parse()` 既有 `strList` 模式（同 `tools` 字段）。
绑定为**按名引用**，不校验存在性于解析期（库可后建）；检索/总览时不存在
即工具内报错反馈（对模型可见的字符串，非异常崩溃）。

## 文件系统布局

```text
<root>/kb/
├── <name>/
│   ├── docs/                 # 原文（add 时复制入内，FR-012）
│   │   ├── deploy.md
│   │   └── faq.md
│   └── evalset.yaml          # Story 6 评估集（可选文件）
```

## 与既有表的边界

- 不修改任何既有表（ddl-auto=update 只建新表——项目已知约束下安全）。
- 审计表零变更（D6 决策）。
- 所有 KB 表写事务与既有 `sessions` 等共用同一 `SqliteWriteGate` 互斥域，
  无新增并发拓扑。
